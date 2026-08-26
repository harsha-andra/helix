import { Injectable } from '@angular/core';
import {
  ADJUSTERS,
  AUDIT_BY_CLAIM,
  CLAIMANTS,
  CLAIMS,
  POLICIES,
  ALWAYS_FAILS_STATUS_UPDATE_CLAIM_ID,
} from './seed-data';
import {
  Adjuster,
  AuditEvent,
  Claimant,
  ClaimDetail,
  ClaimLine,
  ClaimStatus,
  ClaimSummary,
  DashboardSummary,
  Page,
  PolicyDetail,
  PolicySummary,
} from '../models';

export interface ListClaimsParams {
  page: number;
  size: number;
  status?: string | null;
  q?: string | null;
  sort?: string | null;
}

export interface ListPoliciesParams {
  page: number;
  size: number;
  q?: string | null;
}

export class DemoApiError extends Error {
  constructor(public readonly statusCode: number, message: string) {
    super(message);
  }
}

/**
 * Holds the in-memory "database" backing demo mode. State is mutated in place so that
 * status changes, new claims, etc. persist for the lifetime of the browser session,
 * but resets whenever the page is fully reloaded — deliberate for a stateless portfolio demo.
 */
@Injectable({ providedIn: 'root' })
export class DemoDataStore {
  private claims: ClaimDetail[] = CLAIMS.map((c) => ({ ...c, lines: [...c.lines], documents: [...c.documents] }));
  private policies: PolicyDetail[] = POLICIES.map((p) => ({ ...p, coverages: [...p.coverages] }));
  private claimants: Claimant[] = [...CLAIMANTS];
  private adjusters: Adjuster[] = [...ADJUSTERS];
  private auditByClaim: Record<string, AuditEvent[]> = Object.fromEntries(
    Object.entries(AUDIT_BY_CLAIM).map(([k, v]) => [k, [...v]]),
  );
  private nextClaimSeq = this.claims.length + 1;

  private toSummary(c: ClaimDetail): ClaimSummary {
    const { policy, claimant, adjuster, description, lines, documents, ...summary } = c;
    return summary;
  }

  listClaims(params: ListClaimsParams): Page<ClaimSummary> {
    let items = this.claims.slice();

    if (params.status) {
      const wanted = new Set(params.status.split(',').map((s) => s.trim().toUpperCase()));
      items = items.filter((c) => wanted.has(c.status));
    }
    if (params.q && params.q.trim()) {
      const q = params.q.trim().toLowerCase();
      items = items.filter(
        (c) =>
          c.claimNumber.toLowerCase().includes(q) ||
          c.policyNumber.toLowerCase().includes(q) ||
          c.claimantName.toLowerCase().includes(q) ||
          (c.assignedAdjuster ?? '').toLowerCase().includes(q),
      );
    }
    if (params.sort) {
      const [field, dir] = params.sort.split(',');
      const mult = dir === 'asc' ? 1 : -1;
      items = items.sort((a: any, b: any) => {
        const av = a[field];
        const bv = b[field];
        if (av == null && bv == null) return 0;
        if (av == null) return 1;
        if (bv == null) return -1;
        if (typeof av === 'string') return av.localeCompare(bv) * mult;
        return (av - bv) * mult;
      });
    } else {
      items = items.sort((a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime());
    }

    const totalElements = items.length;
    const size = params.size || 10;
    const totalPages = Math.max(1, Math.ceil(totalElements / size));
    const page = Math.min(params.page || 0, totalPages - 1);
    const start = page * size;
    const content = items.slice(start, start + size).map((c) => this.toSummary(c));

    return { content, totalElements, totalPages, number: page, size };
  }

  getClaim(id: string): ClaimDetail | undefined {
    return this.claims.find((c) => c.id === id);
  }

  createClaim(payload: {
    policyId: string;
    claimantId: string;
    incidentDate: string;
    description: string;
    lines: { coverageCode: string; description: string; claimedAmount: number }[];
    documents?: { fileName: string; contentType: string; sizeBytes: number }[];
  }): ClaimDetail {
    const policy = this.policies.find((p) => p.id === payload.policyId);
    const claimant = this.claimants.find((c) => c.id === payload.claimantId);
    if (!policy) throw new DemoApiError(400, 'Unknown policy.');
    if (!claimant) throw new DemoApiError(400, 'Unknown claimant.');

    const now = new Date().toISOString();
    const seq = this.nextClaimSeq++;
    const lines: ClaimLine[] = payload.lines.map((l, i) => ({
      id: `LN-${String(seq).padStart(6, '0')}-${i + 1}`,
      lineNumber: i + 1,
      coverageCode: l.coverageCode,
      description: l.description,
      claimedAmount: l.claimedAmount,
      approvedAmount: null,
      status: 'PENDING',
    }));
    const totalAmount = lines.reduce((s, l) => s + l.claimedAmount, 0);
    const adjuster = this.adjusters[seq % this.adjusters.length];

    const claim: ClaimDetail = {
      id: `CLM-${String(seq).padStart(6, '0')}`,
      claimNumber: `CLM-2025-${String(9000 + seq).padStart(6, '0')}`,
      policyNumber: policy.policyNumber,
      claimantName: `${claimant.firstName} ${claimant.lastName}`,
      status: 'SUBMITTED',
      totalAmount,
      incidentDate: payload.incidentDate,
      submittedAt: now,
      assignedAdjuster: adjuster.name,
      lineCount: lines.length,
      version: 1,
      policy,
      claimant,
      adjuster,
      description: payload.description,
      lines,
      documents: (payload.documents ?? []).map((d, i) => ({
        id: `DOC-${String(seq).padStart(6, '0')}-${i + 1}`,
        fileName: d.fileName,
        contentType: d.contentType,
        sizeBytes: d.sizeBytes,
        uploadedAt: now,
      })),
    };

    this.claims.unshift(claim);
    this.auditByClaim[claim.id] = [
      {
        id: `EVT-${claim.id}-1`,
        entityType: 'CLAIM',
        entityId: claim.id,
        action: 'CLAIM_SUBMITTED',
        actor: 'You',
        occurredAt: now,
        detail: 'Claim submitted via new claim wizard.',
      },
    ];
    return claim;
  }

  updateClaimStatus(id: string, status: ClaimStatus, version: number): ClaimDetail {
    const claim = this.claims.find((c) => c.id === id);
    if (!claim) throw new DemoApiError(404, 'Claim not found.');

    // Deliberately deterministic failure: this single claim always rejects its status
    // change so the UI's optimistic-update rollback path has something real to demo.
    if (id === ALWAYS_FAILS_STATUS_UPDATE_CLAIM_ID) {
      throw new DemoApiError(
        409,
        `Update rejected: claim ${claim.claimNumber} was modified by another user. Refresh and try again.`,
      );
    }
    if (version !== claim.version) {
      throw new DemoApiError(409, `Version conflict: claim ${claim.claimNumber} is now at version ${claim.version}.`);
    }

    claim.status = status;
    claim.version += 1;
    this.auditByClaim[id] = [
      {
        id: `EVT-${id}-${this.auditByClaim[id].length + 1}`,
        entityType: 'CLAIM',
        entityId: id,
        action: 'STATUS_CHANGED',
        actor: 'You',
        occurredAt: new Date().toISOString(),
        detail: `Status changed to ${status}.`,
      },
      ...this.auditByClaim[id],
    ];
    return claim;
  }

  getClaimAudit(id: string): AuditEvent[] {
    return this.auditByClaim[id] ?? [];
  }

  listPolicies(params: ListPoliciesParams): Page<PolicySummary> {
    let items = this.policies.slice();
    if (params.q && params.q.trim()) {
      const q = params.q.trim().toLowerCase();
      items = items.filter(
        (p) =>
          p.policyNumber.toLowerCase().includes(q) ||
          p.holderName.toLowerCase().includes(q) ||
          p.productName.toLowerCase().includes(q),
      );
    }
    items = items.sort((a, b) => a.policyNumber.localeCompare(b.policyNumber));

    const totalElements = items.length;
    const size = params.size || 10;
    const totalPages = Math.max(1, Math.ceil(totalElements / size));
    const page = Math.min(params.page || 0, totalPages - 1);
    const start = page * size;
    const content = items
      .slice(start, start + size)
      .map(({ coverages, ...summary }) => summary);

    return { content, totalElements, totalPages, number: page, size };
  }

  getPolicy(id: string): PolicyDetail | undefined {
    return this.policies.find((p) => p.id === id);
  }

  searchClaimants(q: string): Claimant[] {
    const query = (q ?? '').trim().toLowerCase();
    if (!query) return this.claimants.slice(0, 10);
    return this.claimants
      .filter(
        (c) =>
          `${c.firstName} ${c.lastName}`.toLowerCase().includes(query) ||
          c.email.toLowerCase().includes(query) ||
          c.city.toLowerCase().includes(query),
      )
      .slice(0, 15);
  }

  listAdjusters(): Adjuster[] {
    return this.adjusters.slice();
  }

  getDashboardSummary(): DashboardSummary {
    const openStatuses: ClaimStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'PARTIALLY_APPROVED'];
    const openClaims = this.claims.filter((c) => openStatuses.includes(c.status)).length;
    const awaitingReview = this.claims.filter((c) => c.status === 'UNDER_REVIEW').length;
    const totalReservedAmount = this.claims
      .filter((c) => openStatuses.includes(c.status) || c.status === 'APPROVED')
      .reduce((s, c) => s + c.totalAmount, 0);

    const closed = this.claims.filter((c) => ['PAID', 'CLOSED'].includes(c.status));
    const avgCycleTimeDays = closed.length
      ? Math.round(
          (closed.reduce((s, c) => s + (Date.now() - new Date(c.submittedAt).getTime()), 0) /
            closed.length /
            (1000 * 60 * 60 * 24)) *
            10,
        ) / 10
      : 0;

    const byStatusMap = new Map<ClaimStatus, number>();
    for (const c of this.claims) byStatusMap.set(c.status, (byStatusMap.get(c.status) ?? 0) + 1);
    const byStatus = Array.from(byStatusMap.entries()).map(([status, count]) => ({ status, count }));

    const recentActivity = Object.values(this.auditByClaim)
      .flat()
      .sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime())
      .slice(0, 12);

    return { openClaims, awaitingReview, totalReservedAmount, avgCycleTimeDays, byStatus, recentActivity };
  }
}
