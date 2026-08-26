import { Injectable, signal } from '@angular/core';

export type UserRole = 'ADJUSTER' | 'VIEWER' | 'MANAGER';

export interface DemoUser {
  name: string;
  email: string;
  role: UserRole;
  token: string;
}

const STORAGE_KEY = 'helix.auth.session';

/**
 * Demo-mode authentication: any credentials succeed. A signed-looking bearer token is
 * generated locally and attached to every outgoing request by authInterceptor, and the
 * chosen role drives roleGuard / RBAC-gated UI (e.g. the claim status-change control).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly userSignal = signal<DemoUser | null>(this.restore());

  readonly user = this.userSignal.asReadonly();

  isAuthenticated(): boolean {
    return this.userSignal() !== null;
  }

  hasRole(role: UserRole): boolean {
    return this.userSignal()?.role === role;
  }

  login(name: string, role: UserRole): void {
    const user: DemoUser = {
      name: name.trim() || 'Demo User',
      email: `${(name.trim() || 'demo.user').toLowerCase().replace(/\s+/g, '.')}@helixinsurance.com`,
      role,
      token: this.fakeToken(name, role),
    };
    this.userSignal.set(user);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
  }

  logout(): void {
    this.userSignal.set(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  getToken(): string | null {
    return this.userSignal()?.token ?? null;
  }

  private restore(): DemoUser | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as DemoUser) : null;
    } catch {
      return null;
    }
  }

  private fakeToken(name: string, role: UserRole): string {
    const payload = btoa(JSON.stringify({ sub: name, role, iat: Date.now() }));
    return `demo.${payload}.signature`;
  }
}
