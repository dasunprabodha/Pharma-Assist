import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { seconds } from '../../core/format';
import { ApiError, BedrockCheck } from '../../core/models';
import { StatusStore } from '../../core/status.store';
import { IconComponent } from '../../shared/icon.component';
import { TooltipDirective } from '../../shared/tooltip.directive';

@Component({
  selector: 'app-settings',
  imports: [IconComponent, TooltipDirective],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css'
})
export class SettingsComponent implements OnInit {
  readonly store = inject(StatusStore);
  private readonly api = inject(ApiService);

  readonly check = signal<BedrockCheck | null>(null);
  readonly checking = signal(false);
  readonly seconds = seconds;

  ngOnInit(): void {
    this.store.refresh();
  }

  testConnection(): void {
    this.checking.set(true);
    this.check.set(null);
    this.api.checkBedrock().subscribe({
      next: (c) => {
        this.check.set(c);
        this.checking.set(false);
        this.store.refresh();
      },
      error: (e: ApiError) => {
        this.check.set({ ok: false, model_id: '', message: e.message, latency_ms: 0 });
        this.checking.set(false);
      }
    });
  }
}
