import { Component, input } from '@angular/core';
import { IconComponent } from './icon.component';

/** KPI tile: label · value · optional context line. */
@Component({
  selector: 'app-stat-tile',
  imports: [IconComponent],
  template: `
    <div class="tile" [class.hero]="hero()">
      <div class="label"><app-icon [name]="icon()" [size]="16" /> {{ label() }}</div>
      <div class="value">{{ value() }}</div>
      @if (context()) {
        <div class="context" [class.good]="tone() === 'good'" [class.warn]="tone() === 'warn'">{{ context() }}</div>
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .tile {
      height: 100%;
      background: var(--surface-1);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      padding: 16px 18px;
    }
    .label { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); font-size: 13px; font-weight: 500; }
    .label app-icon { color: var(--text-muted); }
    .value { font-size: 28px; font-weight: 650; letter-spacing: -0.02em; margin-top: 6px; line-height: 1.15; }
    .hero .value { font-size: 48px; }
    .context { margin-top: 6px; font-size: 12px; color: var(--text-muted); }
    .context.good { color: var(--good-text); }
    .context.warn { color: var(--warning-text); }
  `
})
export class StatTileComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly context = input<string>('');
  readonly icon = input('info');
  readonly tone = input<'neutral' | 'good' | 'warn'>('neutral');
  readonly hero = input(false);
}
