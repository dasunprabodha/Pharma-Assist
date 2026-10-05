import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { dateTime } from '../../core/format';
import { ApiError, ConfirmedPrescription } from '../../core/models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-history',
  imports: [FormsModule, RouterLink, IconComponent],
  templateUrl: './history.component.html',
  styleUrl: './history.component.css'
})
export class HistoryComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly all = signal<ConfirmedPrescription[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);
  readonly query = signal('');
  readonly source = signal<'all' | 'ai' | 'manual'>('all');
  readonly expanded = signal<Set<string>>(new Set());
  readonly dateTime = dateTime;
  readonly pageSize = 25;
  readonly visible = signal(this.pageSize);

  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    return this.all().filter(
      (p) =>
        (this.source() === 'all' || p.source === this.source()) &&
        (!q || p.id.includes(q) || p.items.some((i) => (i.drug_name ?? '').toLowerCase().includes(q)))
    );
  });

  ngOnInit(): void {
    this.api.prescriptions().subscribe({
      next: (list) => {
        this.all.set(list);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e);
        this.loading.set(false);
      }
    });
  }

  readonly shown = computed(() => this.filtered().slice(0, this.visible()));

  toggle(id: string): void {
    const next = new Set(this.expanded());
    next.has(id) ? next.delete(id) : next.add(id);
    this.expanded.set(next);
  }

  summary(p: ConfirmedPrescription): string {
    const names = p.items.map((i) => i.drug_name).filter(Boolean);
    return names.slice(0, 4).join(', ') + (names.length > 4 ? ` +${names.length - 4} more` : '');
  }

  /** Downloads the filtered lists as CSV (one row per item) - useful for the report's test evidence. */
  exportCsv(): void {
    const header = ['confirmed_at', 'ref', 'source', 'corrections', 'drug_name', 'strength', 'dose', 'frequency', 'duration', 'confidence', 'note'];
    const lines = [header.join(',')];
    for (const p of this.filtered()) {
      for (const i of p.items) {
        lines.push(
          [p.confirmed_at, p.id, p.source, p.corrections, i.drug_name, i.strength, i.dose, i.frequency, i.duration, i.confidence, i.note]
            .map(csvCell)
            .join(',')
        );
      }
    }
    const blob = new Blob([lines.join('\n') + '\n'], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `confirmed-prescriptions-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}

function csvCell(v: unknown): string {
  const s = v == null ? '' : String(v);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}
