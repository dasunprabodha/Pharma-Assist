import { Directive, ElementRef, OnDestroy, effect, inject, input } from '@angular/core';

type Placement = 'top' | 'bottom' | 'left' | 'right';

let nextId = 0;

/**
 * Accessible tooltip: shows on hover (after a short delay) and on keyboard focus, hides on
 * leave / blur / Escape / scroll. Rendered in <body> with fixed positioning so table and card
 * overflow never clips it. Styles live in styles.css (.app-tooltip) and follow the theme tokens.
 *
 * Usage: <button appTooltip="Delete this row">…</button>
 */
@Directive({ selector: '[appTooltip]' })
export class TooltipDirective implements OnDestroy {
  readonly text = input<string | null | undefined>('', { alias: 'appTooltip' });
  readonly placement = input<Placement>('top', { alias: 'tooltipPlacement' });

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly id = `app-tooltip-${++nextId}`;
  private tip: HTMLDivElement | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;

  private readonly onEnter = () => this.schedule(350);
  private readonly onFocus = () => {
    if (this.host.matches(':focus-visible')) this.schedule(0);
  };
  private readonly onLeave = () => this.hide();
  private readonly onKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape') this.hide();
  };

  constructor() {
    this.host.addEventListener('mouseenter', this.onEnter);
    this.host.addEventListener('mouseleave', this.onLeave);
    this.host.addEventListener('focusin', this.onFocus);
    this.host.addEventListener('focusout', this.onLeave);
    this.host.addEventListener('click', this.onLeave);
    this.host.addEventListener('keydown', this.onKey);
    // Keep an open tooltip's text in sync (e.g. a status that changes while hovered).
    effect(() => {
      const text = this.text();
      if (!text) this.hide();
      else if (this.tip) {
        this.tip.textContent = text;
        this.position();
      }
    });
  }

  ngOnDestroy(): void {
    this.hide();
    this.host.removeEventListener('mouseenter', this.onEnter);
    this.host.removeEventListener('mouseleave', this.onLeave);
    this.host.removeEventListener('focusin', this.onFocus);
    this.host.removeEventListener('focusout', this.onLeave);
    this.host.removeEventListener('click', this.onLeave);
    this.host.removeEventListener('keydown', this.onKey);
  }

  private schedule(delay: number): void {
    this.clearTimer();
    this.timer = setTimeout(() => this.show(), delay);
  }

  private show(): void {
    const text = this.text();
    if (!text || this.tip) return;
    const tip = document.createElement('div');
    tip.className = 'app-tooltip';
    tip.id = this.id;
    tip.setAttribute('role', 'tooltip');
    tip.textContent = text;
    document.body.appendChild(tip);
    this.tip = tip;
    this.host.setAttribute('aria-describedby', this.id);
    this.position();
    requestAnimationFrame(() => tip.classList.add('show'));
    window.addEventListener('scroll', this.onLeave, true);
  }

  private hide(): void {
    this.clearTimer();
    if (!this.tip) return;
    this.tip.remove();
    this.tip = null;
    this.host.removeAttribute('aria-describedby');
    window.removeEventListener('scroll', this.onLeave, true);
  }

  private clearTimer(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
  }

  /** Place the tooltip on the preferred side, flipping if it would leave the viewport. */
  private position(): void {
    const tip = this.tip;
    if (!tip) return;
    const gap = 8;
    const margin = 8;
    const r = this.host.getBoundingClientRect();
    const t = tip.getBoundingClientRect();
    const vw = window.innerWidth;
    const vh = window.innerHeight;

    let side = this.placement();
    if (side === 'top' && r.top - t.height - gap < margin) side = 'bottom';
    else if (side === 'bottom' && r.bottom + t.height + gap > vh - margin) side = 'top';
    else if (side === 'left' && r.left - t.width - gap < margin) side = 'right';
    else if (side === 'right' && r.right + t.width + gap > vw - margin) side = 'left';

    let top: number;
    let left: number;
    if (side === 'top' || side === 'bottom') {
      top = side === 'top' ? r.top - t.height - gap : r.bottom + gap;
      left = r.left + r.width / 2 - t.width / 2;
    } else {
      top = r.top + r.height / 2 - t.height / 2;
      left = side === 'left' ? r.left - t.width - gap : r.right + gap;
    }
    left = Math.min(Math.max(left, margin), vw - t.width - margin);
    top = Math.min(Math.max(top, margin), vh - t.height - margin);

    tip.dataset['side'] = side;
    tip.style.top = `${Math.round(top)}px`;
    tip.style.left = `${Math.round(left)}px`;
  }
}
