import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmText: string;
  cancelText?: string;
  /** Style the confirm button as destructive (e.g. discarding work). */
  danger?: boolean;
}

interface PendingConfirm extends ConfirmOptions {
  resolve: (ok: boolean) => void;
}

/** Themed replacement for window.confirm(), rendered once by ConfirmDialogComponent in the app shell. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<PendingConfirm | null>(null);

  ask(options: ConfirmOptions): Promise<boolean> {
    this.pending()?.resolve(false);
    return new Promise((resolve) => this.pending.set({ ...options, resolve }));
  }

  close(ok: boolean): void {
    const p = this.pending();
    if (!p) return;
    this.pending.set(null);
    p.resolve(ok);
  }
}
