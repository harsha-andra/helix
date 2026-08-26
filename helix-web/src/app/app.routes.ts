import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in · HELIX',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () => import('./features/shell/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · HELIX',
        data: { breadcrumb: 'Dashboard' },
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'claims/new',
        title: 'New Claim · HELIX',
        data: { breadcrumb: 'New Claim' },
        canActivate: [roleGuard('ADJUSTER', 'MANAGER')],
        loadComponent: () =>
          import('./features/claim-wizard/claim-wizard.component').then((m) => m.ClaimWizardComponent),
      },
      {
        path: 'claims/:id',
        title: 'Claim Detail · HELIX',
        data: { breadcrumb: 'Claim Detail' },
        loadComponent: () =>
          import('./features/claims-detail/claim-detail.component').then((m) => m.ClaimDetailComponent),
      },
      {
        path: 'claims',
        title: 'Claims · HELIX',
        data: { breadcrumb: 'Claims' },
        loadComponent: () => import('./features/claims/claims-list.component').then((m) => m.ClaimsListComponent),
      },
      {
        path: 'policies',
        title: 'Policies · HELIX',
        data: { breadcrumb: 'Policies' },
        loadComponent: () => import('./features/policies/policies-list.component').then((m) => m.PoliciesListComponent),
      },
      {
        path: 'claimants',
        title: 'Claimants · HELIX',
        data: { breadcrumb: 'Claimants' },
        loadComponent: () =>
          import('./features/claimants/claimants-search.component').then((m) => m.ClaimantsSearchComponent),
      },
    ],
  },
  {
    path: '**',
    title: 'Not Found · HELIX',
    loadComponent: () => import('./features/not-found/not-found.component').then((m) => m.NotFoundComponent),
  },
];
