import { CanActivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService, UserRole } from '../services/auth.service';

/**
 * Route-level RBAC guard factory. The same `AuthService.hasRole` check it wraps is reused
 * component-side (e.g. to disable the claim status-change control for non-ADJUSTER roles),
 * so the role check is real and consistent, not just a hidden route.
 */
export function roleGuard(...allowed: UserRole[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    if (!auth.isAuthenticated()) return true; // authGuard handles the unauthenticated case
    return allowed.some((role) => auth.hasRole(role));
  };
}
