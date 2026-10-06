import { Routes } from '@angular/router';
import { unsavedChangesGuard } from './core/unsaved-changes.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Dashboard · Prescription Reader',
    loadComponent: () => import('./pages/dashboard/dashboard.component').then((m) => m.DashboardComponent)
  },
  {
    path: 'scan',
    title: 'New prescription · Prescription Reader',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./pages/scan/scan.component').then((m) => m.ScanComponent)
  },
  {
    path: 'history',
    title: 'History · Prescription Reader',
    loadComponent: () => import('./pages/history/history.component').then((m) => m.HistoryComponent)
  },
  {
    path: 'stock',
    title: 'Stock & rules · Prescription Reader',
    loadComponent: () => import('./pages/stock/stock.component').then((m) => m.StockComponent)
  },
  {
    path: 'settings',
    title: 'Settings · Prescription Reader',
    loadComponent: () => import('./pages/settings/settings.component').then((m) => m.SettingsComponent)
  },
  { path: '**', redirectTo: 'dashboard' }
];
