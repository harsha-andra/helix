import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Actions } from '@ngrx/effects';
import { provideMockActions } from '@ngrx/effects/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { Observable, firstValueFrom, of, throwError, toArray } from 'rxjs';
import { ClaimDetail } from '../../core/models';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { ClaimsActions } from './claims.actions';
import { ClaimsEffects } from './claims.effects';
import { initialClaimsState } from './claims.reducer';

function makeDetail(overrides: Partial<ClaimDetail> = {}): ClaimDetail {
  return {
    id: 'CLM-1',
    claimNumber: 'CLM-2025-000001',
    policyNumber: 'POL-2024-000001',
    claimantName: 'Jane Doe',
    status: 'UNDER_REVIEW',
    totalAmount: 1000,
    incidentDate: '2025-01-01T00:00:00.000Z',
    submittedAt: '2025-01-02T00:00:00.000Z',
    assignedAdjuster: 'Alex Adjuster',
    lineCount: 0,
    version: 2,
    policy: {
      id: 'POL-1',
      policyNumber: 'POL-2024-000001',
      productName: 'Auto Standard',
      holderName: 'Jane Doe',
      status: 'ACTIVE',
      effectiveDate: '2024-01-01T00:00:00.000Z',
      expirationDate: '2025-01-01T00:00:00.000Z',
      premiumAmount: 900,
      coverageCount: 1,
    },
    claimant: {
      id: 'CLT-1',
      firstName: 'Jane',
      lastName: 'Doe',
      email: 'jane@example.com',
      phone: '555-0100',
      city: 'Austin',
      state: 'TX',
    },
    adjuster: null,
    description: 'Test claim',
    lines: [],
    documents: [],
    ...overrides,
  };
}

describe('ClaimsEffects', () => {
  let actions$: Observable<any>;
  let effects: ClaimsEffects;
  let apiSpy: jasmine.SpyObj<ClaimsApiService>;

  beforeEach(() => {
    apiSpy = jasmine.createSpyObj<ClaimsApiService>('ClaimsApiService', [
      'list',
      'getById',
      'create',
      'updateStatus',
      'getAudit',
    ]);

    TestBed.configureTestingModule({
      providers: [
        ClaimsEffects,
        provideMockActions(() => actions$),
        provideMockStore({ initialState: { claims: initialClaimsState } }),
        { provide: ClaimsApiService, useValue: apiSpy },
        { provide: MatSnackBar, useValue: jasmine.createSpyObj('MatSnackBar', ['open']) },
      ],
    });

    effects = TestBed.inject(ClaimsEffects);
  });

  it('loadClaims$ maps a successful API response to loadClaimsSuccess', async () => {
    const result = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 };
    apiSpy.list.and.returnValue(of(result));
    actions$ = of(ClaimsActions.loadClaims());

    const emitted = await firstValueFrom(effects.loadClaims$);
    expect(emitted).toEqual(ClaimsActions.loadClaimsSuccess({ result }));
    expect(apiSpy.list).toHaveBeenCalledWith(initialClaimsState.query);
  });

  it('loadClaims$ maps a failed API response to loadClaimsFailure', async () => {
    apiSpy.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'boom' } })));
    actions$ = of(ClaimsActions.loadClaims());

    const emitted = await firstValueFrom(effects.loadClaims$);
    expect(emitted).toEqual(ClaimsActions.loadClaimsFailure({ error: 'boom' }));
  });

  it('updateStatus$ emits an optimistic update immediately, then the confirmed result', async () => {
    const claim = makeDetail();
    apiSpy.updateStatus.and.returnValue(of(claim));
    actions$ = of(ClaimsActions.updateStatus({ id: 'CLM-1', status: 'UNDER_REVIEW', version: 1 }));

    const emitted = await firstValueFrom(effects.updateStatus$.pipe(toArray()));
    expect(emitted).toEqual([
      ClaimsActions.updateStatusOptimistic({ id: 'CLM-1', status: 'UNDER_REVIEW' }),
      ClaimsActions.updateStatusSuccess({ claim }),
    ]);
  });

  it('updateStatus$ emits an optimistic update, then a failure when the API rejects it', async () => {
    apiSpy.updateStatus.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Version conflict' } })),
    );
    actions$ = of(ClaimsActions.updateStatus({ id: 'CLM-1', status: 'PAID', version: 1 }));

    const emitted = await firstValueFrom(effects.updateStatus$.pipe(toArray()));
    expect(emitted).toEqual([
      ClaimsActions.updateStatusOptimistic({ id: 'CLM-1', status: 'PAID' }),
      ClaimsActions.updateStatusFailure({ error: 'Version conflict' }),
    ]);
  });
});
