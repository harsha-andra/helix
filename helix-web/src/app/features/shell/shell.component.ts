import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
  UrlSegment,
} from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

interface Breadcrumb {
  label: string;
  url: string | null;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', icon: 'space_dashboard', route: '/dashboard' },
  { label: 'Claims', icon: 'assignment', route: '/claims' },
  { label: 'New Claim', icon: 'add_circle', route: '/claims/new' },
  { label: 'Policies', icon: 'shield', route: '/policies' },
  { label: 'Claimants', icon: 'people', route: '/claimants' },
];

const NOTIFICATIONS = [
  { title: 'Claim CLM-2025-004417 flagged for review', time: '12m ago' },
  { title: 'New document uploaded to CLM-2025-004392', time: '48m ago' },
  { title: 'Policy POL-2024-018842 renewal due in 14 days', time: '3h ago' },
];

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    FormsModule,
    MatSidenavModule,
    MatToolbarModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatButtonModule,
    MatBadgeModule,
    MatTooltipModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly breakpointObserver = inject(BreakpointObserver);
  readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);

  readonly navItems = NAV_ITEMS;
  readonly notifications = NOTIFICATIONS;

  readonly isHandset = toSignal(
    this.breakpointObserver.observe(Breakpoints.Handset).pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  readonly collapsed = signal(false);
  readonly searchTerm = signal('');

  readonly breadcrumbs = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      startWith(null),
      map(() => this.buildBreadcrumbs()),
    ),
    { initialValue: [] as Breadcrumb[] },
  );

  readonly sidenavMode = computed(() => (this.isHandset() ? 'over' : 'side'));
  readonly sidenavOpened = computed(() => !this.isHandset());

  toggleCollapsed(): void {
    this.collapsed.update((c) => !c);
  }

  runSearch(): void {
    const term = this.searchTerm().trim();
    if (!term) return;
    this.router.navigate(['/claims'], { queryParams: { q: term } });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  private buildBreadcrumbs(): Breadcrumb[] {
    const crumbs: Breadcrumb[] = [];
    let route: ActivatedRoute | null = this.activatedRoute.root;
    let url = '';
    while (route) {
      const child: ActivatedRoute | null = route.firstChild;
      if (child) {
        const segments = child.snapshot.url.map((s: UrlSegment) => s.path).join('/');
        if (segments) url += `/${segments}`;
        const label = child.snapshot.data['breadcrumb'];
        if (label) crumbs.push({ label, url: url || null });
      }
      route = child;
    }
    if (crumbs.length === 0) crumbs.push({ label: 'Dashboard', url: null });
    return crumbs;
  }
}
