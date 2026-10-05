import { Component, computed, input } from '@angular/core';

export interface BarItem {
  label: string;
  value: number;
  /** Optional status marker shown before the label (color + label, never color alone). */
  status?: 'good' | 'warning' | 'critical' | 'neutral';
}

/** Horizontal bar list: label left, thin bar, value at the tip. One series, so no legend. */
@Component({
  selector: 'app-bar-list',
  template: `
    <ul>
      @for (b of rows(); track b.label) {
        <li [title]="b.label + ': ' + b.value">
          <span class="label">
            @if (b.status) { <i class="st" [class]="'st ' + b.status"></i> }
            {{ b.label }}
          </span>
          <span class="track"><span class="bar" [style.width.%]="b.pct" [style.background]="color()"></span></span>
          <span class="value">{{ b.value }}</span>
        </li>
      } @empty {
        <li class="none">{{ emptyText() }}</li>
      }
    </ul>
  `,
  styles: `
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
    li { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(48px, 1fr) 32px; align-items: center; gap: 10px; font-size: 13px; }
    .label { display: flex; align-items: center; gap: 6px; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .track { height: 10px; }
    .bar { display: block; height: 10px; border-radius: 0 4px 4px 0; min-width: 2px; }
    .value { text-align: right; font-variant-numeric: tabular-nums; color: var(--text-secondary); font-weight: 600; }
    .st { width: 8px; height: 8px; border-radius: 50%; display: inline-block; flex-shrink: 0; }
    .st.good { background: var(--status-good); }
    .st.warning { background: var(--status-warning); }
    .st.critical { background: var(--status-critical); }
    .st.neutral { background: var(--text-muted); }
    .none { display: block; color: var(--text-muted); text-align: center; padding: 16px 0; }
  `
})
export class BarListComponent {
  readonly items = input.required<BarItem[]>();
  readonly color = input('var(--series-1)');
  readonly emptyText = input('No data yet');

  readonly rows = computed(() => {
    const max = Math.max(1, ...this.items().map((i) => i.value));
    return this.items().map((i) => ({ ...i, pct: (i.value / max) * 100 }));
  });
}
