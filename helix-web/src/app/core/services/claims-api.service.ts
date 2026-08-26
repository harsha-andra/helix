import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditEvent, ClaimDetail, ClaimStatus, ClaimSummary, Page } from '../models';

export interface ClaimsQuery {
  page: number;
  size: number;
  status?: string | null;
  q?: string | null;
  sort?: string | null;
}

export interface CreateClaimPayload {
  policyId: string;
  claimantId: string;
  incidentDate: string;
  description: string;
  lines: { coverageCode: string; description: string; claimedAmount: number }[];
  documents?: { fileName: string; contentType: string; sizeBytes: number }[];
}

@Injectable({ providedIn: 'root' })
export class ClaimsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/claims`;

  list(query: ClaimsQuery): Observable<Page<ClaimSummary>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.status) params = params.set('status', query.status);
    if (query.q) params = params.set('q', query.q);
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<Page<ClaimSummary>>(this.base, { params });
  }

  getById(id: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`${this.base}/${id}`);
  }

  create(payload: CreateClaimPayload): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(this.base, payload);
  }

  updateStatus(id: string, status: ClaimStatus, version: number): Observable<ClaimDetail> {
    return this.http.patch<ClaimDetail>(`${this.base}/${id}/status`, { status, version });
  }

  getAudit(id: string): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${this.base}/${id}/audit`);
  }
}
