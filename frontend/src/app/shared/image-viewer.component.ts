import { Component, ElementRef, computed, effect, input, output, signal, viewChild } from '@angular/core';
import { IconComponent } from './icon.component';
import { TooltipDirective } from './tooltip.directive';

const MIN_SCALE = 1;
const MAX_SCALE = 6;
const STEP = 1.4;

/**
 * Zoom-and-pan viewer for the prescription photo (or PDF pages, one at a time).
 * Mouse: wheel to zoom at the pointer, drag to pan, double-click to zoom in / reset.
 * Touch: pinch to zoom, drag to pan. Keyboard (when focused): + / − / 0 and arrow keys.
 */
@Component({
  selector: 'app-image-viewer',
  imports: [IconComponent, TooltipDirective],
  template: `
    <div #vp class="viewport" tabindex="0" role="img" [attr.aria-label]="label() + ' – use plus and minus to zoom, arrows to pan'"
         [class.zoomed]="scale() > 1" [class.dragging]="dragging()"
         [style.touch-action]="scale() > 1 ? 'none' : 'pan-y'"
         (wheel)="onWheel($event)" (dblclick)="onDoubleClick($event)" (keydown)="onKey($event)"
         (pointerdown)="onPointerDown($event)" (pointermove)="onPointerMove($event)"
         (pointerup)="onPointerUp($event)" (pointercancel)="onPointerUp($event)">
      <img [src]="pages()[page()]" [alt]="label()" draggable="false" [style.transform]="transform()"
           [class.animate]="animate()" />
    </div>
    <div class="bar">
      @if (pages().length > 1) {
        <div class="pages" role="group" aria-label="Page">
          @for (p of pages(); track $index) {
            <button type="button" [class.on]="$index === page()" (click)="goTo($index)"
                    [attr.aria-label]="'Page ' + ($index + 1)" [attr.aria-pressed]="$index === page()"
                    [appTooltip]="'Show page ' + ($index + 1)">{{ $index + 1 }}</button>
          }
        </div>
      }
      <span class="grow"></span>
      <button type="button" class="icon-btn" (click)="zoomBy(1 / STEP)" [disabled]="scale() <= MIN" aria-label="Zoom out" appTooltip="Zoom out (−)">
        <app-icon name="minus" [size]="16" />
      </button>
      <button type="button" class="pct" (click)="reset()" [disabled]="scale() <= MIN" aria-label="Reset zoom" appTooltip="Reset to fit (0)">
        {{ (scale() * 100).toFixed(0) }}%
      </button>
      <button type="button" class="icon-btn" (click)="zoomBy(STEP)" [disabled]="scale() >= MAX" aria-label="Zoom in" appTooltip="Zoom in (+)">
        <app-icon name="plus" [size]="16" />
      </button>
      <button type="button" class="icon-btn" (click)="enlarge.emit()" aria-label="Full screen" appTooltip="Full screen">
        <app-icon name="expand" [size]="16" />
      </button>
    </div>
  `,
  styles: `
    :host { display: block; }
    .viewport {
      position: relative;
      height: var(--viewer-height, min(62vh, 620px));
      overflow: hidden;
      border-radius: 8px;
      background: var(--surface-2);
      cursor: zoom-in;
      user-select: none;
      overscroll-behavior: contain;
    }
    .viewport.zoomed { cursor: grab; }
    .viewport.dragging { cursor: grabbing; }
    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
      display: block;
      transform-origin: 0 0;
      will-change: transform;
      pointer-events: none;
    }
    img.animate { transition: transform 0.18s ease-out; }
    .bar { display: flex; align-items: center; gap: 4px; margin-top: 8px; }
    .grow { flex: 1; }
    .pages { display: inline-flex; gap: 4px; }
    .pages button, .pct {
      min-width: 30px; height: 30px; padding: 0 8px;
      border: 1px solid var(--border); border-radius: 7px;
      background: var(--surface-1); color: var(--text-secondary);
      font: inherit; font-size: 12px; font-weight: 600; cursor: pointer;
      font-variant-numeric: tabular-nums;
    }
    .pages button.on { background: var(--accent-soft); color: var(--accent-strong); border-color: transparent; }
    .pct { min-width: 54px; }
    .pct:disabled { cursor: default; opacity: 0.7; }
    .icon-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    @media (prefers-reduced-motion: reduce) { img.animate { transition: none; } }
  `
})
export class ImageViewerComponent {
  readonly pages = input.required<string[]>();
  readonly label = input('Prescription photo');
  readonly enlarge = output<void>();

  readonly MIN = MIN_SCALE;
  readonly MAX = MAX_SCALE;
  readonly STEP = STEP;

  readonly page = signal(0);
  readonly scale = signal(1);
  readonly x = signal(0);
  readonly y = signal(0);
  readonly dragging = signal(false);
  /** Smooth transitions for button/keyboard zoom; off while dragging or pinching so it tracks the finger. */
  readonly animate = signal(true);
  readonly transform = computed(() => `translate(${this.x()}px, ${this.y()}px) scale(${this.scale()})`);

  /** Pages compared by content: parents may pass a fresh array with the same images on every render. */
  private readonly pageList = computed(() => this.pages(), {
    equal: (a, b) => a.length === b.length && a.every((src, i) => src === b[i])
  });
  private readonly vp = viewChild.required<ElementRef<HTMLDivElement>>('vp');
  private readonly pointers = new Map<number, { x: number; y: number }>();
  private pinchDistance = 0;

  constructor() {
    // New file → back to page 1, fitted.
    effect(() => {
      this.pageList();
      this.page.set(0);
      this.setView(1, 0, 0);
    });
  }

  goTo(index: number): void {
    this.page.set(index);
    this.reset();
  }

  reset(): void {
    this.animate.set(true);
    this.setView(1, 0, 0);
  }

  /** Zoom around the centre of the viewport (buttons and keyboard). */
  zoomBy(factor: number): void {
    const r = this.vp().nativeElement.getBoundingClientRect();
    this.animate.set(true);
    this.zoomAt(this.scale() * factor, r.width / 2, r.height / 2);
  }

  onWheel(e: WheelEvent): void {
    e.preventDefault();
    const r = this.vp().nativeElement.getBoundingClientRect();
    this.animate.set(false);
    // Trackpads send many small deltas, mouse wheels a few large ones – scale smoothly either way.
    this.zoomAt(this.scale() * Math.exp(-e.deltaY * 0.0025), e.clientX - r.left, e.clientY - r.top);
  }

  onDoubleClick(e: MouseEvent): void {
    const r = this.vp().nativeElement.getBoundingClientRect();
    this.animate.set(true);
    if (this.scale() > 1) this.setView(1, 0, 0);
    else this.zoomAt(2.5, e.clientX - r.left, e.clientY - r.top);
  }

  onKey(e: KeyboardEvent): void {
    const pan = 40;
    const actions: Record<string, () => void> = {
      '+': () => this.zoomBy(STEP),
      '=': () => this.zoomBy(STEP),
      '-': () => this.zoomBy(1 / STEP),
      '0': () => this.reset(),
      ArrowLeft: () => this.panBy(pan, 0),
      ArrowRight: () => this.panBy(-pan, 0),
      ArrowUp: () => this.panBy(0, pan),
      ArrowDown: () => this.panBy(0, -pan)
    };
    const action = actions[e.key];
    // Arrow keys only pan when zoomed, so they still scroll the page otherwise.
    if (!action || (e.key.startsWith('Arrow') && this.scale() === 1)) return;
    e.preventDefault();
    action();
  }

  onPointerDown(e: PointerEvent): void {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    this.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (this.pointers.size === 2) this.pinchDistance = this.distance();
    // Capture only when there is something to drag or pinch, so a plain touch can still scroll the page.
    if (this.scale() > 1 || this.pointers.size === 2) {
      this.vp().nativeElement.setPointerCapture(e.pointerId);
      this.animate.set(false);
    }
  }

  onPointerMove(e: PointerEvent): void {
    const prev = this.pointers.get(e.pointerId);
    if (!prev) return;
    const curr = { x: e.clientX, y: e.clientY };
    this.pointers.set(e.pointerId, curr);

    if (this.pointers.size === 2) {
      const d = this.distance();
      const mid = this.midpoint();
      if (this.pinchDistance > 0) this.zoomAt(this.scale() * (d / this.pinchDistance), mid.x, mid.y);
      this.pinchDistance = d;
    } else if (this.scale() > 1) {
      this.dragging.set(true);
      this.panBy(curr.x - prev.x, curr.y - prev.y);
    }
  }

  onPointerUp(e: PointerEvent): void {
    this.pointers.delete(e.pointerId);
    if (this.pointers.size < 2) this.pinchDistance = 0;
    if (this.pointers.size === 0) {
      this.dragging.set(false);
      this.animate.set(true);
    }
  }

  private panBy(dx: number, dy: number): void {
    this.setView(this.scale(), this.x() + dx, this.y() + dy);
  }

  /** Zoom to `next`, keeping the image point under (px, py) – viewport coordinates – in place. */
  private zoomAt(next: number, px: number, py: number): void {
    const s = this.scale();
    const target = Math.min(MAX_SCALE, Math.max(MIN_SCALE, next));
    const k = target / s;
    this.setView(target, px - (px - this.x()) * k, py - (py - this.y()) * k);
  }

  /** Apply a view, keeping the image covering the viewport (no panning off into empty space). */
  private setView(scale: number, x: number, y: number): void {
    if (scale <= MIN_SCALE) {
      this.scale.set(MIN_SCALE);
      this.x.set(0);
      this.y.set(0);
      return;
    }
    const el = this.vp().nativeElement;
    const w = el.clientWidth;
    const h = el.clientHeight;
    this.scale.set(scale);
    this.x.set(Math.min(0, Math.max(w - w * scale, x)));
    this.y.set(Math.min(0, Math.max(h - h * scale, y)));
  }

  private distance(): number {
    const [a, b] = [...this.pointers.values()];
    return Math.hypot(a.x - b.x, a.y - b.y);
  }

  private midpoint(): { x: number; y: number } {
    const [a, b] = [...this.pointers.values()];
    const r = this.vp().nativeElement.getBoundingClientRect();
    return { x: (a.x + b.x) / 2 - r.left, y: (a.y + b.y) / 2 - r.top };
  }
}
