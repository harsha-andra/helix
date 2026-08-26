import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, delay, of, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DemoApiError, DemoDataStore } from '../mock/demo-data.store';

function randomLatency(): number {
  return 200 + Math.floor(Math.random() * 300); // 200-500ms, per spec
}

function ok<T>(body: T): Observable<any> {
  return of(new HttpResponse({ status: 200, body })).pipe(delay(randomLatency()));
}

function fail(status: number, message: string): Observable<never> {
  return of(null).pipe(
    delay(randomLatency()),
    switchMap(() => throwError(() => new HttpErrorResponse({ status, error: { message }, statusText: message }))),
  );
}

/**
 * Serves every `/api/v1/*` call from an in-memory seeded store instead of a real backend.
 * This is what lets the whole app run as a static, backend-free deployment (e.g. on Vercel)
 * while still exercising real pagination, filtering, sorting, search, and mutation logic.
 */
export const demoApiInterceptor: HttpInterceptorFn = (req, next) => {
  if (!environment.demoMode || !req.url.startsWith(environment.apiBaseUrl)) {
    return next(req);
  }

  const store = inject(DemoDataStore);
  const path = req.url.slice(environment.apiBaseUrl.length).split('?')[0];
  const segments = path.split('/').filter(Boolean);

  try {
    // GET /claims
    if (req.method === 'GET' && segments.length === 1 && segments[0] === 'claims') {
      const page = Number(req.params.get('page') ?? 0);
      const size = Number(req.params.get('size') ?? 10);
      const status = req.params.get('status');
      const q = req.params.get('q');
      const sort = req.params.get('sort');
      return ok(store.listClaims({ page, size, status, q, sort }));
    }

    // GET /claims/:id/audit
    if (req.method === 'GET' && segments.length === 3 && segments[0] === 'claims' && segments[2] === 'audit') {
      return ok(store.getClaimAudit(segments[1]));
    }

    // GET /claims/:id
    if (req.method === 'GET' && segments.length === 2 && segments[0] === 'claims') {
      const claim = store.getClaim(segments[1]);
      if (!claim) return fail(404, 'Claim not found.');
      return ok(claim);
    }

    // POST /claims
    if (req.method === 'POST' && segments.length === 1 && segments[0] === 'claims') {
      const created = store.createClaim(req.body as any);
      return ok(created);
    }

    // PATCH /claims/:id/status
    if (req.method === 'PATCH' && segments.length === 3 && segments[0] === 'claims' && segments[2] === 'status') {
      const body = req.body as { status: any; version: number };
      const updated = store.updateClaimStatus(segments[1], body.status, body.version);
      return ok(updated);
    }

    // GET /policies/:id
    if (req.method === 'GET' && segments.length === 2 && segments[0] === 'policies') {
      const policy = store.getPolicy(segments[1]);
      if (!policy) return fail(404, 'Policy not found.');
      return ok(policy);
    }

    // GET /policies
    if (req.method === 'GET' && segments.length === 1 && segments[0] === 'policies') {
      const page = Number(req.params.get('page') ?? 0);
      const size = Number(req.params.get('size') ?? 10);
      const q = req.params.get('q');
      return ok(store.listPolicies({ page, size, q }));
    }

    // GET /claimants/search
    if (req.method === 'GET' && segments.length === 2 && segments[0] === 'claimants' && segments[1] === 'search') {
      return ok(store.searchClaimants(req.params.get('q') ?? ''));
    }

    // GET /adjusters
    if (req.method === 'GET' && segments.length === 1 && segments[0] === 'adjusters') {
      return ok(store.listAdjusters());
    }

    // GET /dashboard/summary
    if (req.method === 'GET' && segments.length === 2 && segments[0] === 'dashboard' && segments[1] === 'summary') {
      return ok(store.getDashboardSummary());
    }

    return fail(404, `No demo handler for ${req.method} ${path}`);
  } catch (e) {
    if (e instanceof DemoApiError) {
      return fail(e.statusCode, e.message);
    }
    return fail(500, 'Unexpected demo API error.');
  }
};
