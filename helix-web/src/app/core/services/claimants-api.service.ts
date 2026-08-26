import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Adjuster, Claimant } from '../models';

@Injectable({ providedIn: 'root' })
export class ClaimantsApiService {
  private readonly http = inject(HttpClient);

  search(q: string): Observable<Claimant[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<Claimant[]>(`${environment.apiBaseUrl}/claimants/search`, { params });
  }

  listAdjusters(): Observable<Adjuster[]> {
    return this.http.get<Adjuster[]>(`${environment.apiBaseUrl}/adjusters`);
  }
}
