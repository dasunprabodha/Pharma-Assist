import { Component, OnChanges, computed, inject, input, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { ApiError, Checklist, ChecklistRow, ChecklistWarning, Item } from '../core/models';
import { DrugThumbComponent } from './drug-thumb.component';
import { IconComponent } from './icon.component';

/**
 * Feature 2 – the dispensing check. Runs on a confirmed list (AI-extracted or manual – same Item structure) and shows
 * stock status, in-stock generic alternatives, interaction / duplicate warnings and a per-item "Checked" box.
 */
@Component({
  selector: 'app-dispensing-check',
  imports: [IconComponent, DrugThumbComponent],
  templateUrl: './dispensing-check.component.html',
  styleUrl: './dispensing-check.component.css'
})
export class DispensingCheckComponent implements OnChanges {
  private readonly api = inject(ApiService);

  readonly items = input.required<Item[]>();
  /** Confirmed-list reference, shown on the CSV. */
  readonly reference = input<string>('');

  readonly checklist = signal<Checklist | null>(null);
  readonly error = signal<ApiError | null>(null);
  readonly loading = signal(false);
  readonly checked = signal<Set<number>>(new Set());

  readonly counts = computed(() => {
    const w = this.checklist()?.warnings ?? [];
    return {
      major: w.filter((x) => x.severity === 'major').length,
      moderate: w.filter((x) => x.severity === 'moderate').length,
      minor: w.filter((x) => x.severity === 'minor').length
    };
  });
  readonly allChecked = computed(() => {
    const rows = this.checklist()?.rows ?? [];
    return rows.length > 0 && rows.every((r) => this.checked().has(r.index));
  });

  ngOnChanges(): void {
    this.run();
  }

  run(): void {
    this.loading.set(true);
    this.error.set(null);
    this.checked.set(new Set());
    this.api.check(this.items()).subscribe({
      next: (c) => {
        this.checklist.set(c);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.checklist.set(null);
        this.error.set(e);
        this.loading.set(false);
      }
    });
  }

  toggle(row: ChecklistRow): void {
    const next = new Set(this.checked());
    next.has(row.index) ? next.delete(row.index) : next.add(row.index);
    this.checked.set(next);
  }

  severityClass(w: ChecklistWarning): string {
    return w.severity === 'major' ? 'bad' : w.severity === 'moderate' ? 'warn' : 'info';
  }

  severityIcon(w: ChecklistWarning): string {
    return w.severity === 'major' ? 'error' : w.severity === 'moderate' ? 'alert' : 'info';
  }

  severityLabel(w: ChecklistWarning): string {
    const kind = {
      interaction: 'Interaction',
      duplicate_class: 'Duplicate class',
      duplicate_generic: 'Same active ingredient',
      not_in_stock_list: 'Not checked'
    }[w.rule_type];
    return `${w.severity[0].toUpperCase()}${w.severity.slice(1)} · ${kind}`;
  }

  warningsForRow(row: ChecklistRow): ChecklistWarning[] {
    return (this.checklist()?.warnings ?? []).filter((w) => w.rows.includes(row.index) && w.rule_type !== 'not_in_stock_list');
  }

  directions(row: ChecklistRow): string {
    return [row.dose, row.frequency, row.duration].filter(Boolean).join(' · ') || '–';
  }

  /** CSV of the checklist – drug items, stock status, warnings and the Checked ticks; no patient details. */
  downloadCsv(): void {
    const c = this.checklist();
    if (!c) return;
    const cell = (v: unknown) => {
      const s = v == null ? '' : String(v);
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    };
    const lines = [
      'Dispensing checklist – decision support only; the pharmacist must confirm every item',
      `Generated,${cell(new Date(c.checked_at).toLocaleString('en-GB'))},Ref,${cell(this.reference())}`,
      'Contains drug items only – no patient details. Stock list and rules are SIMULATED data.',
      '',
      ['#', 'Prescribed', 'Directions', 'Dispense (stock item)', 'Generic', 'Stock status', 'Quantity on hand',
        'Alternative', 'Notes', 'Checked'].join(','),
      ...c.rows.map((r) =>
        [r.index, r.prescribed, this.directions(r), r.matched_product ? `${r.matched_product} ${r.stock_strength}` : '',
          r.generic_name, r.status.replace(/_/g, ' '), r.quantity_on_hand,
          r.alternatives.map((a) => `${a.brand_name} ${a.strength} (${a.quantity_on_hand} in stock)`).join('; '),
          r.notes.join('; '), this.checked().has(r.index) ? 'yes' : 'no'].map(cell).join(',')),
      '',
      ['Severity', 'Rule', 'Rows', 'Items', 'Warning'].join(','),
      ...c.warnings.map((w) => [w.severity, w.rule_id ?? '', w.rows.join(' & '), w.items.join(' + '), w.message].map(cell).join(','))
    ];
    const blob = new Blob([lines.join('\n') + '\n'], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `dispensing-checklist-${this.reference() || 'list'}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
