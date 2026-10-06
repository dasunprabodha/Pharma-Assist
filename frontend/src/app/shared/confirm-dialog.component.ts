import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { ConfirmService } from '../core/confirm.service';
import { IconComponent } from './icon.component';

/** Modal confirm dialog driven by ConfirmService. Uses <dialog> for focus trapping and Escape. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [IconComponent],
  template: `
    <dialog #dlg class="dlg" (cancel)="$event.preventDefault(); confirm.close(false)" (click)="onBackdrop($event)"
            aria-labelledby="confirm-title" aria-describedby="confirm-msg">
      @if (confirm.pending(); as p) {
        <div class="body">
          <span class="icon" [class.danger]="p.danger"><app-icon [name]="p.danger ? 'alert' : 'info'" /></span>
          <div>
            <h2 id="confirm-title">{{ p.title }}</h2>
            <p id="confirm-msg">{{ p.message }}</p>
          </div>
        </div>
        <div class="foot">
          <button type="button" class="btn" (click)="confirm.close(false)" autofocus>{{ p.cancelText || 'Keep editing' }}</button>
          <button type="button" class="btn" [class.primary]="!p.danger" [class.danger-solid]="p.danger" (click)="confirm.close(true)">
            {{ p.confirmText }}
          </button>
        </div>
      }
    </dialog>
  `,
  styles: `
    .dlg {
      width: min(440px, calc(100vw - 32px));
      padding: 0;
      border: 1px solid var(--border);
      border-radius: var(--radius);
      background: var(--surface-1);
      color: var(--text-primary);
      box-shadow: 0 20px 50px rgb(0 0 0 / 0.25);
    }
    .dlg[open] { animation: pop 0.16s ease-out; }
    .dlg::backdrop { background: rgb(0 0 0 / 0.45); animation: fade 0.16s ease-out; }
    .body { display: flex; gap: 14px; padding: 20px 20px 8px; }
    .icon {
      display: grid; place-items: center; flex-shrink: 0;
      width: 36px; height: 36px; border-radius: 50%;
      background: var(--accent-soft); color: var(--accent-strong);
    }
    .icon.danger { background: var(--warning-bg); color: var(--warning-text); }
    h2 { font-size: 16px; font-weight: 650; }
    p { margin: 6px 0 0; color: var(--text-secondary); }
    .foot { display: flex; justify-content: flex-end; gap: 10px; padding: 16px 20px 20px; flex-wrap: wrap; }
    .danger-solid { background: var(--status-critical); border-color: var(--status-critical); color: #fff; }
    .danger-solid:hover:not(:disabled) { background: var(--critical-text); border-color: var(--critical-text); }
    @keyframes pop { from { opacity: 0; transform: translateY(8px) scale(0.98); } }
    @keyframes fade { from { opacity: 0; } }
    @media (prefers-reduced-motion: reduce) { .dlg[open], .dlg::backdrop { animation: none; } }
  `
})
export class ConfirmDialogComponent {
  readonly confirm = inject(ConfirmService);
  private readonly dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  constructor() {
    effect(() => {
      const el = this.dlg().nativeElement;
      if (this.confirm.pending() && !el.open) el.showModal();
      else if (!this.confirm.pending() && el.open) el.close();
    });
  }

  /** A click on the backdrop (the dialog element itself, outside its content) cancels. */
  onBackdrop(event: MouseEvent): void {
    if (event.target === this.dlg().nativeElement) this.confirm.close(false);
  }
}
