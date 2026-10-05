import { Component, OnInit, inject, signal } from '@angular/core';
import { ConfigStatus, StatusService } from './status.service';

@Component({
  selector: 'app-root',
  imports: [],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  private readonly statusService = inject(StatusService);

  readonly status = signal<ConfigStatus | null>(null);
  readonly backendError = signal(false);

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.backendError.set(false);
    this.statusService.getStatus().subscribe({
      next: (s) => this.status.set(s),
      error: () => {
        this.status.set(null);
        this.backendError.set(true);
      }
    });
  }
}
