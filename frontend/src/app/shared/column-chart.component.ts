import { Component, computed, input, signal } from '@angular/core';

export interface ColumnSeries {
  key: string;
  label: string;
  color: string; // CSS color / var()
}

export interface ColumnDatum {
  label: string; // x-axis label (short)
  title: string; // tooltip title (full)
  values: Record<string, number>;
}

/**
 * Stacked column chart (SVG). Thin columns (<= 24px), 4px rounded data-end, 2px surface gap between
 * segments, hairline grid, hover tooltip per column, legend for >= 2 series, and a table view.
 */
@Component({
  selector: 'app-column-chart',
  template: `
    <div class="head">
      @if (series().length > 1) {
        <div class="legend">
          @for (s of series(); track s.key) {
            <span><i [style.background]="s.color"></i>{{ s.label }}</span>
          }
        </div>
      }
      <button type="button" class="toggle" (click)="showTable.set(!showTable())">
        {{ showTable() ? 'Show chart' : 'Show table' }}
      </button>
    </div>

    @if (!showTable()) {
      <div class="chart" (mouseleave)="hover.set(-1)">
        <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" preserveAspectRatio="none" role="img" [attr.aria-label]="ariaLabel()">
          @for (t of ticks(); track t) {
            <line [attr.x1]="padL" [attr.x2]="W" [attr.y1]="y(t)" [attr.y2]="y(t)" class="grid" />
          }
          @for (col of columns(); track col.i) {
            <rect class="hit" [attr.x]="col.slotX" [attr.y]="0" [attr.width]="col.slotW" [attr.height]="H - padB"
                  (mouseenter)="hover.set(col.i)" />
            @if (hover() === col.i) {
              <rect class="hover-band" [attr.x]="col.slotX" y="0" [attr.width]="col.slotW" [attr.height]="H - padB" />
            }
            @for (seg of col.segments; track seg.key) {
              <path [attr.d]="seg.d" [attr.fill]="seg.color" class="seg" />
            }
          }
        </svg>
        <div class="y-axis">
          @for (t of ticks(); track t) {
            <span [style.top.%]="(y(t) / H) * 100">{{ t }}</span>
          }
        </div>
        <div class="x-axis">
          @for (col of columns(); track col.i) {
            <span [style.left.%]="((col.slotX + col.slotW / 2) / W) * 100" [class.hide]="col.i % labelEvery() !== 0">{{ col.label }}</span>
          }
        </div>
        @if (hover() >= 0 && columns()[hover()]; as col) {
          <div class="tooltip" [style.left.%]="((col.slotX + col.slotW / 2) / W) * 100" [class.flip]="col.i > columns().length * 0.65">
            <div class="tt-title">{{ col.title }}</div>
            @for (s of series(); track s.key) {
              <div class="tt-row"><i [style.background]="s.color"></i>{{ s.label }}<b>{{ col.values[s.key] || 0 }}</b></div>
            }
          </div>
        }
      </div>
    } @else {
      <div class="table-wrap">
        <table class="data">
          <thead><tr><th>Date</th>@for (s of series(); track s.key) { <th class="num">{{ s.label }}</th> }</tr></thead>
          <tbody>
            @for (d of data(); track d.title) {
              <tr><td>{{ d.title }}</td>@for (s of series(); track s.key) { <td class="num">{{ d.values[s.key] || 0 }}</td> }</tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: `
    :host { display: block; }
    .head { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
    .legend { display: flex; gap: 14px; font-size: 12px; color: var(--text-secondary); }
    .legend span { display: inline-flex; align-items: center; gap: 6px; }
    .legend i, .tt-row i { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }
    .toggle { margin-left: auto; border: 0; background: none; color: var(--accent-strong); font: inherit; font-size: 12px; cursor: pointer; padding: 2px 4px; }
    .chart { position: relative; height: 220px; padding-left: 28px; padding-bottom: 22px; }
    svg { width: 100%; height: 100%; display: block; overflow: visible; }
    .grid { stroke: var(--grid); stroke-width: 1; vector-effect: non-scaling-stroke; }
    .hit { fill: transparent; }
    .hover-band { fill: var(--text-primary); opacity: 0.04; pointer-events: none; }
    .seg { pointer-events: none; }
    .y-axis { position: absolute; left: 0; top: 0; bottom: 22px; width: 24px; }
    .y-axis span { position: absolute; right: 4px; transform: translateY(-50%); font-size: 11px; color: var(--text-muted); font-variant-numeric: tabular-nums; }
    .x-axis { position: absolute; left: 28px; right: 0; bottom: 0; height: 18px; }
    .x-axis span { position: absolute; transform: translateX(-50%); font-size: 11px; color: var(--text-muted); white-space: nowrap; }
    .x-axis span.hide { display: none; }
    .tooltip {
      position: absolute;
      top: 6px;
      transform: translateX(12px);
      background: var(--surface-1);
      border: 1px solid var(--border);
      border-radius: 8px;
      box-shadow: 0 6px 20px rgb(0 0 0 / 0.12);
      padding: 8px 10px;
      font-size: 12px;
      pointer-events: none;
      min-width: 140px;
      z-index: 2;
    }
    .tooltip.flip { transform: translateX(calc(-100% - 12px)); }
    .tt-title { font-weight: 600; margin-bottom: 4px; }
    .tt-row { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); white-space: nowrap; }
    .tt-row b { margin-left: auto; padding-left: 12px; color: var(--text-primary); font-variant-numeric: tabular-nums; }
  `
})
export class ColumnChartComponent {
  readonly data = input.required<ColumnDatum[]>();
  readonly series = input.required<ColumnSeries[]>();
  readonly ariaLabel = input('Column chart');

  readonly W = 600;
  readonly H = 200;
  readonly padL = 0;
  readonly padB = 0;
  readonly hover = signal(-1);
  readonly showTable = signal(false);

  readonly max = computed(() => {
    const totals = this.data().map((d) => this.series().reduce((sum, s) => sum + (d.values[s.key] ?? 0), 0));
    return niceMax(Math.max(0, ...totals));
  });

  readonly ticks = computed(() => {
    const max = this.max();
    const step = max / 4;
    return [0, 1, 2, 3, 4].map((i) => Math.round(i * step));
  });

  readonly labelEvery = computed(() => Math.max(1, Math.ceil(this.data().length / 5)));

  readonly columns = computed(() => {
    const n = this.data().length || 1;
    const slotW = this.W / n;
    const barW = Math.min(slotW * 0.55, 24); // thin columns, never filling the slot
    return this.data().map((d, i) => {
      const slotX = i * slotW;
      const x = slotX + (slotW - barW) / 2;
      let base = this.H;
      const visible = this.series().filter((s) => (d.values[s.key] ?? 0) > 0);
      const segments = visible.map((s, idx) => {
        const h = ((d.values[s.key] ?? 0) / this.max()) * this.H;
        const top = base - h;
        const isTop = idx === visible.length - 1;
        const gap = idx > 0 ? 2 : 0; // 2px surface gap between stacked segments
        const d2 = segmentPath(x, top, barW, h - gap, isTop ? 4 : 0, base - gap);
        base = top;
        return { key: s.key, color: s.color, d: d2 };
      });
      return { i, label: d.label, title: d.title, values: d.values, slotX, slotW, segments };
    });
  });

  y(value: number): number {
    return this.H - (value / this.max()) * this.H;
  }
}

function niceMax(v: number): number {
  if (v <= 4) return 4;
  const pow = Math.pow(10, Math.floor(Math.log10(v)));
  for (const m of [1, 2, 2.5, 5, 10]) {
    if (m * pow >= v) return Math.ceil((m * pow) / 4) * 4;
  }
  return v;
}

/** Column segment: rounded top corners (radius r) when it is the data-end, square at its base. */
function segmentPath(x: number, top: number, w: number, h: number, r: number, bottom: number): string {
  if (h <= 0) return '';
  const rr = Math.min(r, w / 2, h);
  const t = bottom - h;
  return `M${x},${bottom} L${x},${t + rr} Q${x},${t} ${x + rr},${t} L${x + w - rr},${t} Q${x + w},${t} ${x + w},${t + rr} L${x + w},${bottom} Z`;
}
