import { Component, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { StatusStore } from './core/status.store';
import { IconComponent } from './shared/icon.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  readonly store = inject(StatusStore);
  private readonly router = inject(Router);
  readonly navOpen = signal(false);

  readonly nav = [
    { path: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/scan', label: 'New prescription', icon: 'scan' },
    { path: '/history', label: 'History', icon: 'history' },
    { path: '/stock', label: 'Stock & rules', icon: 'stock' },
    { path: '/settings', label: 'Settings', icon: 'settings' }
  ];

  ngOnInit(): void {
    this.store.refresh();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.navOpen.set(false));
  }
}
