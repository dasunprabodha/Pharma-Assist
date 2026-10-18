import { Component, HostListener, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { StatusStore } from '../../core/status.store';
import { seconds, usd } from '../../core/format';
import {
  ApiError,
  ConfirmedPrescription,
  Confidence,
  ITEM_FIELDS,
  Item,
  ItemField,
  NameSuggestion,
  ScanResult,
  Source,
  StockMatch
} from '../../core/models';
import { HasUnsavedWork } from '../../core/unsaved-changes.guard';
import { IconComponent } from '../../shared/icon.component';
import { ImageViewerComponent } from '../../shared/image-viewer.component';
import { DrugThumbComponent } from '../../shared/drug-thumb.component';
import { DispensingCheckComponent } from '../../shared/dispensing-check.component';
import { TooltipDirective } from '../../shared/tooltip.directive';

type Stage = 'upload' | 'reading' | 'review' | 'confirmed';
type Mode = 'ai' | 'manual';

interface Row {
  key: number;
  item: Item;
  /** The AI's original reading, to count pharmacist corrections. Null for rows added by hand. */
  orig: Item | null;
  /** Stock-list names offered as "Did you mean…?" chips for the drug name. */
  suggestions?: NameSuggestion[];
  /** The stock item this name resolves to (same matcher as the dispensing check) – shown with its pack image. */
  stockMatch?: StockMatch | null;
}

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
const ACCEPTED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'pdf'];
/** Value for the file input's accept attribute. */
export const ACCEPT_ATTR = 'image/jpeg,image/png,image/webp,application/pdf,.jpg,.jpeg,.png,.webp,.pdf';
const MAX_BYTES = 10 * 1024 * 1024;

@Component({
  selector: 'app-scan',
  imports: [FormsModule, RouterLink, IconComponent, TooltipDirective, ImageViewerComponent, DrugThumbComponent, DispensingCheckComponent],
  templateUrl: './scan.component.html',
  styleUrl: './scan.component.css'
})
export class ScanComponent implements OnDestroy, HasUnsavedWork {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(ConfirmService);
  readonly status = inject(StatusStore);
  readonly accept = ACCEPT_ATTR;

  readonly fields = ITEM_FIELDS;
  readonly labels: Record<ItemField, string> = {
    drug_name: 'Drug name',
    strength: 'Strength',
    dose: 'Dose',
    frequency: 'Frequency',
    duration: 'Duration'
  };
  readonly placeholders: Record<ItemField, string> = {
    drug_name: 'e.g. Lipitor',
    strength: 'e.g. 20 mg',
    dose: 'e.g. 1 tablet',
    frequency: 'e.g. twice daily (bd)',
    duration: 'e.g. 5 days'
  };
  readonly hints: Record<ItemField, string> = {
    drug_name: 'Exactly as written on the prescription (brand or generic). Checked against the stock list.',
    strength: 'Amount of active ingredient per unit, e.g. 500 mg',
    dose: 'How much to take each time, e.g. 1 tablet',
    frequency: 'How often. Shorthand is expanded: bd = twice daily, tds = three times, nocte = at night, 1×2 = one twice daily',
    duration: 'How long. 5/7 = 5 days, 2/52 = 2 weeks'
  };
  readonly confidenceHint = 'How sure the AI is about this row. Low rows must be checked against the photo. You can change it.';
  readonly confidenceLabels: Record<Confidence, string> = {
    high: 'High – clearly read',
    medium: 'Medium – probably right',
    low: 'Low – verify against the photo'
  };
  readonly seconds = seconds;
  readonly usd = usd;

  readonly mode = signal<Mode>('ai');
  readonly stage = signal<Stage>('upload');
  readonly file = signal<File | null>(null);
  readonly previewUrl = signal<string | null>(null);
  /** For a PDF: the rendered page images (exactly what the model will see). Null for photos. */
  readonly pdfPages = signal<string[] | null>(null);
  readonly rendering = signal(false);
  readonly dragging = signal(false);
  readonly zoomed = signal(false);
  readonly elapsedMs = signal(0);
  readonly result = signal<ScanResult | null>(null);
  readonly error = signal<ApiError | null>(null);
  readonly busy = signal(false);
  readonly confirmed = signal<ConfirmedPrescription | null>(null);
  readonly invalidRows = signal<Set<number>>(new Set());

  rows: Row[] = [];
  private deletedOriginals = 0;
  private nextKey = 1;
  private timer: ReturnType<typeof setInterval> | null = null;
  private scanSub: Subscription | null = null;

  ngOnDestroy(): void {
    this.scanSub?.unsubscribe();
    this.stopTimer();
    this.revokePreview();
  }

  // ---- Unsaved work ------------------------------------------------------

  /** True while reading, or while reviewing a list that has not been confirmed. */
  hasUnsavedWork(): boolean {
    if (this.stage() === 'reading') return true;
    if (this.stage() !== 'review') return false;
    // An AI reading always counts: it took time and cost tokens to produce.
    return !!this.result() || this.rows.some((r) => ITEM_FIELDS.some((f) => norm(r.item[f])));
  }

  /** Route guard hook: asks before leaving the page with unconfirmed work. */
  confirmLeave(): Promise<boolean> {
    if (!this.hasUnsavedWork()) return Promise.resolve(true);
    if (this.stage() === 'reading') {
      return this.dialog.ask({
        title: 'Stop reading this prescription?',
        message: 'Amazon Nova is still reading the photo. If you leave now, the result will be lost.',
        confirmText: 'Stop and leave',
        cancelText: 'Stay',
        danger: true
      });
    }
    return this.askDiscard('Leave without confirming?', 'Leave page');
  }

  /** Browser refresh / tab close: the browser shows its own generic prompt. */
  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasUnsavedWork()) event.preventDefault();
  }

  private askDiscard(title: string, confirmText: string): Promise<boolean> {
    const n = this.rows.length;
    const edits = this.corrections();
    const what = this.result()
      ? `The AI-extracted list (${n} item${n === 1 ? '' : 's'}${edits ? `, ${edits} correction${edits === 1 ? '' : 's'}` : ''})`
      : `The ${n} manually entered item${n === 1 ? '' : 's'}`;
    return this.dialog.ask({
      title,
      message: `${what} has not been confirmed and will be lost.`,
      confirmText,
      danger: true
    });
  }

  // ---- Mode -------------------------------------------------------------

  async setMode(mode: Mode): Promise<void> {
    if (mode === this.mode() || this.stage() === 'reading') return;
    if (this.hasUnsavedWork()) {
      const ok = await this.askDiscard(
        mode === 'manual' ? 'Switch to manual entry?' : 'Switch to reading a photo?',
        mode === 'manual' ? 'Discard and enter manually' : 'Discard and upload a photo'
      );
      if (!ok) return;
    }
    if (mode === 'manual') {
      this.startManual();
    } else {
      this.mode.set('ai');
      this.result.set(null);
      this.rows = [];
      this.stage.set('upload');
    }
    this.error.set(null);
  }

  private startManual(): void {
    this.mode.set('manual');
    this.result.set(null);
    this.rows = [this.emptyRow()];
    this.deletedOriginals = 0;
    this.invalidRows.set(new Set());
    this.stage.set('review');
  }

  // ---- Upload -----------------------------------------------------------

  onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.setFile(input.files[0]);
    input.value = '';
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) this.setFile(file);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  setFile(file: File): void {
    this.error.set(null);
    const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
    // Some systems report an empty MIME type, so the extension is accepted as well.
    if (!ACCEPTED_TYPES.includes(file.type) && !ACCEPTED_EXTENSIONS.includes(ext)) {
      this.error.set({ error_type: 'invalid_image', message: 'Only JPG, PNG, WEBP or PDF files are accepted.' });
      return;
    }
    if (file.size > MAX_BYTES) {
      this.error.set({
        error_type: 'invalid_image',
        message: `The file is ${(file.size / 1048576).toFixed(1)} MB; the maximum is 10 MB. Please use a smaller file.`
      });
      return;
    }
    this.revokePreview();
    this.file.set(file);
    this.pdfPages.set(null);
    if (file.type === 'application/pdf' || ext === 'pdf') {
      // Browsers (notably on Android) cannot reliably show PDFs inline, so the backend renders the pages.
      this.rendering.set(true);
      this.api.preview(file).subscribe({
        next: (res) => {
          this.pdfPages.set(res.pages);
          this.previewUrl.set(res.pages[0]);
          this.rendering.set(false);
        },
        error: (err: ApiError) => {
          this.rendering.set(false);
          this.clearFile();
          this.error.set(err);
        }
      });
    } else {
      this.previewUrl.set(URL.createObjectURL(file));
    }
  }

  clearFile(): void {
    this.revokePreview();
    this.file.set(null);
    this.previewUrl.set(null);
    this.pdfPages.set(null);
  }

  private revokePreview(): void {
    const url = this.previewUrl();
    if (url?.startsWith('blob:')) URL.revokeObjectURL(url);
  }

  /** Images to show for comparison: every PDF page, or the single photo. */
  pages(): string[] {
    return this.pdfPages() ?? (this.previewUrl() ? [this.previewUrl()!] : []);
  }

  // ---- AI reading ---------------------------------------------------------

  read(): void {
    const file = this.file();
    if (!file) return;
    this.error.set(null);
    this.stage.set('reading');
    this.startTimer();
    this.scanSub = this.api.scan(file).subscribe({
      next: (res) => {
        this.stopTimer();
        this.scanSub = null;
        this.result.set(res);
        this.mode.set('ai');
        this.rows = res.items.map((item) => ({ key: this.nextKey++, item: { ...item }, orig: { ...item } }));
        this.deletedOriginals = 0;
        this.invalidRows.set(new Set());
        this.stage.set('review');
        this.loadSuggestions(this.rows);
      },
      error: (err: ApiError) => {
        this.stopTimer();
        this.scanSub = null;
        this.error.set(err);
        if (err.fallback_to_manual) {
          this.startManual();
        } else {
          this.stage.set('upload');
        }
      }
    });
  }

  /**
   * Stop waiting for the AI. Unsubscribing aborts the HTTP request; the Bedrock call may still
   * finish on the server (and be logged), but its result is ignored.
   */
  cancelReading(toManual: boolean): void {
    this.scanSub?.unsubscribe();
    this.scanSub = null;
    this.stopTimer();
    if (toManual) {
      this.startManual();
      this.error.set({
        error_type: 'cancelled',
        message: 'Enter the items from the photo below, or switch back to "Read from photo" to try again.',
        fallback_to_manual: true
      });
    } else {
      this.stage.set('upload');
    }
  }

  private startTimer(): void {
    const start = Date.now();
    this.elapsedMs.set(0);
    this.timer = setInterval(() => this.elapsedMs.set(Date.now() - start), 100);
  }

  private stopTimer(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  // ---- Table editing -------------------------------------------------------

  private emptyRow(): Row {
    return {
      key: this.nextKey++,
      item: { drug_name: null, strength: null, dose: null, frequency: null, duration: null, confidence: 'high', note: null },
      orig: null
    };
  }

  addRow(): void {
    this.rows = [...this.rows, this.emptyRow()];
  }

  deleteRow(row: Row): void {
    if (row.orig) this.deletedOriginals++;
    this.rows = this.rows.filter((r) => r !== row);
    this.invalidRows.set(new Set());
  }

  isChanged(row: Row, field: ItemField): boolean {
    return !!row.orig && norm(row.item[field]) !== norm(row.orig[field]);
  }

  /** Tooltip for an editable cell: the AI's original reading once edited, or a hint if it was unreadable. */
  cellHint(row: Row, field: ItemField): string | null {
    if (!row.orig) return null;
    if (this.isChanged(row, field)) return `AI read: ${row.orig[field] ?? '(unreadable)'}`;
    if (row.item[field] === null) return 'The AI could not read this field – check the photo';
    return null;
  }

  setConfidence(row: Row, value: Confidence): void {
    row.item.confidence = value;
  }

  /** 1-based rows currently marked low confidence. */
  lowRows(): number[] {
    return this.rows.map((r, i) => (r.item.confidence === 'low' ? i + 1 : 0)).filter((n) => n > 0);
  }

  /** Cells the pharmacist changed vs the AI reading, plus rows added or removed. */
  corrections(): number {
    if (!this.result()) return 0;
    let count = this.deletedOriginals;
    for (const row of this.rows) {
      if (!row.orig) {
        count++;
        continue;
      }
      count += this.fields.filter((f) => this.isChanged(row, f)).length;
    }
    return count;
  }

  source(): Source {
    return this.result() ? 'ai' : 'manual';
  }

  recheckNames(): void {
    const items = this.rows.map((r) => cleanItem(r.item));
    this.busy.set(true);
    this.api.nameCheck(items).subscribe({
      next: (checked) => {
        checked.forEach((c, i) => {
          this.rows[i].item.note = c.note;
          this.rows[i].item.confidence = c.confidence;
        });
        this.busy.set(false);
        this.loadSuggestions(this.rows);
      },
      error: (err: ApiError) => {
        this.error.set(err);
        this.busy.set(false);
      }
    });
  }

  // ---- "Did you mean…?" ---------------------------------------------------------

  /** Fetch stock-list suggestions for the given rows (matched by row, so edits/deletes meanwhile are safe). */
  loadSuggestions(rows: Row[]): void {
    const targets = rows.filter((r) => norm(r.item.drug_name));
    rows.filter((r) => !norm(r.item.drug_name)).forEach((r) => (r.suggestions = []));
    if (!targets.length) return;
    this.api.nameSuggestions(targets.map((r) => norm(r.item.drug_name))).subscribe({
      next: (lists) => targets.forEach((r, i) => (r.suggestions = lists[i] ?? [])),
      // Suggestions are a convenience – fail quietly; the name check note still applies.
      error: () => targets.forEach((r) => (r.suggestions = []))
    });
    this.api.stockMatches(targets.map((r) => ({ drug_name: norm(r.item.drug_name), strength: r.item.strength }))).subscribe({
      next: (matches) => targets.forEach((r, i) => (r.stockMatch = matches[i] ?? null)),
      error: () => targets.forEach((r) => (r.stockMatch = null))
    });
  }

  /** After the drug name is edited: refresh its suggestions and its stock-list note. */
  onDrugNameChange(row: Row): void {
    this.loadSuggestions([row]);
    this.refreshNote(row);
  }

  /** Show chips only when the written name is not already one of the stock names. */
  showSuggestions(row: Row): boolean {
    const name = norm(row.item.drug_name).toLowerCase();
    const list = row.suggestions ?? [];
    return list.length > 0 && !list.some((s) => s.name.toLowerCase() === name);
  }

  suggestionHint(s: NameSuggestion): string {
    return `${s.generic_name} ${s.strength} · ${s.in_stock ? 'in stock' : 'out of stock'} · ${s.score}% match. Click to use this name.`;
  }

  /** Apply a suggestion, then re-run the name check for that row so its note matches the new name. */
  applySuggestion(row: Row, s: NameSuggestion): void {
    row.item.drug_name = s.name;
    this.refreshNote(row);
  }

  /**
   * Re-run the name check for one row and update only its note, so it never describes an old name.
   * Confidence is left as the pharmacist set it ("Check names against stock" still re-rates every row).
   */
  private refreshNote(row: Row): void {
    const name = norm(row.item.drug_name);
    this.api.nameCheck([cleanItem(row.item)]).subscribe({
      next: ([checked]) => {
        // Ignore a late reply if the name changed again meanwhile.
        if (checked && norm(row.item.drug_name) === name) row.item.note = checked.note;
      },
      error: () => (row.item.note = null)
    });
  }

  // ---- Confirm ---------------------------------------------------------------

  confirm(): void {
    const missing = new Set(this.rows.map((r, i) => (norm(r.item.drug_name) ? -1 : i)).filter((i) => i >= 0));
    this.invalidRows.set(missing);
    if (this.rows.length === 0) {
      this.error.set({ error_type: 'validation', message: 'Add at least one item before confirming.' });
      return;
    }
    if (missing.size) {
      this.error.set({
        error_type: 'validation',
        message: `Every row needs a drug name. Missing in row(s): ${[...missing].map((i) => i + 1).join(', ')}.`
      });
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    const items = this.rows.map((r) => cleanItem(r.item));
    this.api.confirm(this.source(), this.result()?.scan_id ?? null, items, this.corrections()).subscribe({
      next: (saved) => {
        this.confirmed.set(saved);
        this.stage.set('confirmed');
        this.busy.set(false);
      },
      error: (err: ApiError) => {
        this.error.set(err);
        this.busy.set(false);
      }
    });
  }

  /** Cancel button during review: ask first if anything would be lost. */
  async cancelReview(): Promise<void> {
    if (this.hasUnsavedWork() && !(await this.askDiscard('Discard this list?', 'Discard list'))) return;
    this.startOver();
  }

  startOver(): void {
    this.clearFile();
    this.rows = [];
    this.result.set(null);
    this.error.set(null);
    this.confirmed.set(null);
    this.invalidRows.set(new Set());
    this.deletedOriginals = 0;
    this.mode.set('ai');
    this.stage.set('upload');
  }

  errorTitle(err: ApiError): string {
    switch (err.error_type) {
      case 'invalid_image':
        return 'Please choose another file';
      case 'validation':
        return 'Check the list';
      case 'cancelled':
        return 'Reading cancelled – using manual entry';
      case 'unreadable_image':
        return 'Photo could not be read';
      default:
        return err.fallback_to_manual ? 'Switched to manual entry' : 'Something went wrong';
    }
  }

  stepIndex(): number {
    return { upload: 0, reading: 0, review: 1, confirmed: 2 }[this.stage()];
  }
}

function norm(value: string | null | undefined): string {
  return (value ?? '').trim();
}

function cleanItem(item: Item): Item {
  const out = { ...item };
  for (const f of ITEM_FIELDS) {
    out[f] = norm(item[f]) || null;
  }
  return out;
}
