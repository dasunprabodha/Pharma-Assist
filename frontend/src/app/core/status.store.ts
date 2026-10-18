import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { ConfigStatus } from './models';

/** Configuration status shared by the top bar and the Settings page. */
@Injectable({ providedIn: 'root' })
export class StatusStore {
  private readonly api = inject(ApiService);

  readonly status = signal<ConfigStatus | null>(null);
  readonly backendDown = signal(false);

  refresh(): void {
    this.api.status().subscribe({
      next: (s) => {
        this.status.set(s);
        this.backendDown.set(false);
      },
      error: () => {
        this.status.set(null);
        this.backendDown.set(true);
      }
    });
  }
}
