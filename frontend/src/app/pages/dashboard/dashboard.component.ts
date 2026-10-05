import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { compact, dateTime, percent, seconds, usd } from '../../core/format';
import { ApiError, Dashboard, OUTCOME_LABELS } from '../../core/models';
import { BarItem, BarListComponent } from '../../shared/bar-list.component';
import { ColumnChartComponent, ColumnDatum, ColumnSeries } from '../../shared/column-chart.component';
import { IconComponent } from '../../shared/icon.component';
import { StatTileComponent } from '../../shared/stat-tile.component';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, IconComponent, StatTileComponent, ColumnChartComponent, BarListComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly data = signal<Dashboard | null>(null);
  readonly error = signal<ApiError | null>(null);
  readonly loading = signal(true);

  readonly seconds = seconds;
  readonly percent = percent;
  readonly usd = usd;
  readonly compact = compact;
  readonly dateTime = dateTime;
  readonly outcomeLabels = OUTCOME_LABELS;

  readonly series: ColumnSeries[] = [
    { key: 'success', label: 'Read successfully', color: 'var(--series-1)' },
    { key: 'failed', label: 'Failed / fallback', color: 'var(--series-2)' }
  ];

  readonly perDay = computed<ColumnDatum[]>(() =>
    (this.data()?.scans_per_day ?? []).map((d) => {
      const date = new Date(d.date + 'T00:00:00');
      return {
        label: `${date.getDate()}/${date.getMonth() + 1}`,
        title: date.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' }),
        values: { success: d.success, failed: d.failed }
      };
    })
  );

  readonly outcomes = computed<BarItem[]>(() =>
    Object.entries(this.data()?.outcomes ?? {})
      .sort((a, b) => b[1] - a[1])
      .map(([k, v]) => ({
        label: OUTCOME_LABELS[k] ?? k,
        value: v,
        status: k === 'success' ? 'good' : k === 'unreadable_image' || k === 'invalid_image' ? 'warning' : 'critical'
      }))
  );

  readonly topDrugs = computed<BarItem[]>(() =>
    (this.data()?.top_drugs ?? []).map((d) => ({ label: d.name, value: d.count }))
  );

  readonly confidence = computed(() => {
    const c = this.data()?.confidence_confirmed ?? {};
    const parts = [
      { key: 'high', label: 'High', value: c.high ?? 0 },
      { key: 'medium', label: 'Medium', value: c.medium ?? 0 },
      { key: 'low', label: 'Low', value: c.low ?? 0 }
    ];
    const total = parts.reduce((s, p) => s + p.value, 0);
    return { total, parts: parts.map((p) => ({ ...p, pct: total ? (p.value / total) * 100 : 0 })) };
  });

  readonly hasData = computed(() => {
    const d = this.data();
    return !!d && (d.scans_total > 0 || d.confirmed_total > 0);
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (d) => {
        this.data.set(d);
        this.error.set(null);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e);
        this.loading.set(false);
      }
    });
  }

  latencyTone(d: Dashboard): 'good' | 'warn' | 'neutral' {
    if (d.avg_latency_ms == null) return 'neutral';
    return d.avg_latency_ms <= d.target_ms ? 'good' : 'warn';
  }

  outcomeClass(outcome: string): string {
    if (outcome === 'success') return 'good';
    return outcome === 'unreadable_image' || outcome === 'invalid_image' ? 'warn' : 'bad';
  }
}
