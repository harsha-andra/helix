import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page, PolicyDetail, PolicySummary } from '../models';

export interface PoliciesQuery {
  page: number;
  size: number;
  q?: string | null;
}

@Injectable({ providedIn: 'root' })
export class PoliciesApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/policies`;

  list(query: PoliciesQuery): Observable<Page<PolicySummary>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q) params = params.set('q', query.q);
    return this.http.get<Page<PolicySummary>>(this.base, { params });
  }

  getById(id: string): Observable<PolicyDetail> {
    return this.http.get<PolicyDetail>(`${this.base}/${id}`);
  }
}
