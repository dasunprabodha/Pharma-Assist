import { Component, ElementRef, HostListener, OnInit, inject, signal, viewChild } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { StatusStore } from './core/status.store';
import { ThemeService } from './core/theme.service';
import { ConfirmDialogComponent } from './shared/confirm-dialog.component';
import { IconComponent } from './shared/icon.component';
import { TooltipDirective } from './shared/tooltip.directive';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, TooltipDirective, ConfirmDialogComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  readonly store = inject(StatusStore);
  readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  readonly navOpen = signal(false);
  /** Position of the sliding highlight behind the active navigation link. */
  readonly indicator = signal({ top: 0, height: 0, ready: false });
  private readonly navEl = viewChild.required<ElementRef<HTMLElement>>('navEl');

  readonly nav = [
    { path: '/dashboard', label: 'Dashboard', icon: 'dashboard', hint: 'Scans, read times, cost and corrections at a glance' },
    { path: '/scan', label: 'New prescription', icon: 'scan', hint: 'Read a prescription photo with AI, or enter the items manually' },
    { path: '/history', label: 'History', icon: 'history', hint: 'Lists the pharmacist has confirmed' },
    { path: '/stock', label: 'Stock & rules', icon: 'stock', hint: 'Simulated stock list and interaction rules' },
    { path: '/settings', label: 'Settings', icon: 'settings', hint: 'AWS configuration status and connection test' }
  ];

  /** Tooltip for the header status pill. */
  statusHint(): string {
    if (this.store.backendDown()) return 'The Spring Boot backend is not responding. Start it with mvn spring-boot:run.';
    const s = this.store.status();
    if (!s) return 'Checking the backend and AWS configuration…';
    return s.credentials_found
      ? `AWS credentials found · model ${s.model_id} · ${s.region}. Click for details.`
      : 'AWS credentials were not found, so only manual entry will work. Click for setup help.';
  }

  ngOnInit(): void {
    this.store.refresh();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.navOpen.set(false);
      // routerLinkActive updates the class during this navigation; measure on the next frame.
      requestAnimationFrame(() => this.moveIndicator());
    });
  }

  @HostListener('window:resize')
  moveIndicator(): void {
    const active = this.navEl().nativeElement.querySelector<HTMLElement>('.nav-link.active');
    if (!active) {
      this.indicator.update((v) => ({ ...v, height: 0 }));
      return;
    }
    const first = !this.indicator().ready;
    this.indicator.set({ top: active.offsetTop, height: active.offsetHeight, ready: !first });
    // The first placement jumps into position; later ones slide.
    if (first) requestAnimationFrame(() => this.indicator.update((v) => ({ ...v, ready: true })));
  }
}
