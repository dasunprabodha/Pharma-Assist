import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { dateTime } from '../../core/format';
import { StatusStore } from '../../core/status.store';
import { ApiError, ConfirmedPrescription } from '../../core/models';
import { IconComponent } from '../../shared/icon.component';
import { DispensingCheckComponent } from '../../shared/dispensing-check.component';
import { ImageViewerComponent } from '../../shared/image-viewer.component';
import { TooltipDirective } from '../../shared/tooltip.directive';

@Component({
  selector: 'app-history',
  imports: [FormsModule, RouterLink, IconComponent, TooltipDirective, ImageViewerComponent, DispensingCheckComponent],
  templateUrl: './history.component.html',
  styleUrl: './history.component.css'
})
export class HistoryComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(ConfirmService);
  readonly status = inject(StatusStore);

  readonly all = signal<ConfirmedPrescription[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);
  readonly query = signal('');
  readonly source = signal<'all' | 'ai' | 'manual'>('all');
  readonly expanded = signal<Set<string>>(new Set());
  readonly dateTime = dateTime;
  readonly pageSize = 25;
  readonly visible = signal(this.pageSize);
  /** Image pages open in the full-screen view. */
  readonly lightbox = signal<string[] | null>(null);
  private readonly urlCache = new Map<string, string[]>();

  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    return this.all().filter(
      (p) =>
        (this.source() === 'all' || p.source === this.source()) &&
        (!q || p.id.includes(q) || p.items.some((i) => (i.drug_name ?? '').toLowerCase().includes(q)))
    );
  });

  ngOnInit(): void {
    if (!this.status.status()) this.status.refresh();
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

  /** Lists whose dispensing check is shown (Feature 2 can be re-run on any confirmed list). */
  readonly checking = signal<Set<string>>(new Set());

  startCheck(id: string): void {
    this.checking.set(new Set(this.checking()).add(id));
  }

  toggle(id: string): void {
    const next = new Set(this.expanded());
    next.has(id) ? next.delete(id) : next.add(id);
    this.expanded.set(next);
  }

  /** Image URLs for a list (same array each time, so the viewer keeps its zoom). */
  imageUrls(p: ConfirmedPrescription): string[] {
    const n = p.image_pages ?? 0;
    const key = `${p.id}:${n}`;
    let urls = this.urlCache.get(key);
    if (!urls) {
      urls = Array.from({ length: n }, (_, i) => this.api.imageUrl(p.id, i + 1));
      this.urlCache.set(key, urls);
    }
    return urls;
  }

  /** Why there is no image for this list. */
  noImageReason(p: ConfirmedPrescription): string {
    if (p.source === 'manual') return 'Entered manually – there is no photo for this list.';
    if (this.status.status() && !this.status.status()!.keep_images) {
      return 'Image saving is off. Set KEEP_IMAGES=true in .env to keep images of newly confirmed lists.';
    }
    return 'No image kept – confirmed before image saving was turned on, or the image was deleted or has expired.';
  }

  expiryText(p: ConfirmedPrescription): string {
    if (!p.image_expires_at) return '';
    const d = new Date(p.image_expires_at);
    return `Deleted automatically on ${d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}`;
  }

  async deleteImage(p: ConfirmedPrescription): Promise<void> {
    const ok = await this.dialog.ask({
      title: 'Delete this image?',
      message: `The saved prescription image for ${p.id} will be deleted now. The confirmed drug list is kept.`,
      confirmText: 'Delete image',
      cancelText: 'Cancel',
      danger: true
    });
    if (!ok) return;
    this.api.deleteImage(p.id).subscribe({
      next: () => this.all.update((list) => list.map((x) => (x.id === p.id ? { ...x, image_pages: 0, image_expires_at: null } : x))),
      error: (e: ApiError) => this.error.set(e)
    });
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
