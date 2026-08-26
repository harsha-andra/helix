import { ClaimDetail, ClaimSummary, Page } from '../../core/models';
import { ClaimsActions } from './claims.actions';
import { claimsAdapter, claimsFeature, initialClaimsState } from './claims.reducer';

function makeSummary(overrides: Partial<ClaimSummary> = {}): ClaimSummary {
  return {
    id: 'CLM-000001',
    claimNumber: 'CLM-2025-000001',
    policyNumber: 'POL-2024-000001',
    claimantName: 'Jane Doe',
    status: 'SUBMITTED',
    totalAmount: 1000,
    incidentDate: '2025-01-01T00:00:00.000Z',
    submittedAt: '2025-01-02T00:00:00.000Z',
    assignedAdjuster: 'Alex Adjuster',
    lineCount: 1,
    version: 1,
    ...overrides,
  };
}

function makeDetail(overrides: Partial<ClaimDetail> = {}): ClaimDetail {
  const summary = makeSummary(overrides);
  return {
    ...summary,
    policy: {
      id: 'POL-1',
      policyNumber: summary.policyNumber,
      productName: 'Auto Standard',
      holderName: 'Jane Doe',
      status: 'ACTIVE',
      effectiveDate: '2024-01-01T00:00:00.000Z',
      expirationDate: '2025-01-01T00:00:00.000Z',
      premiumAmount: 900,
      coverageCount: 2,
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

describe('claimsFeature reducer', () => {
  const { reducer } = claimsFeature;

  it('returns the initial state for an unknown action', () => {
    const state = reducer(undefined, { type: '@@INIT' } as any);
    expect(state.loading).toBeFalse();
    expect(state.query.page).toBe(0);
  });

  it('setQuery merges partial changes into the existing query', () => {
    const state = reducer(initialClaimsState, ClaimsActions.setQuery({ changes: { status: 'DENIED', page: 2 } }));
    expect(state.query.status).toBe('DENIED');
    expect(state.query.page).toBe(2);
    expect(state.query.size).toBe(initialClaimsState.query.size);
  });

  it('loadClaims sets loading and clears any previous error', () => {
    const errored = { ...initialClaimsState, error: 'boom' };
    const state = reducer(errored, ClaimsActions.loadClaims());
    expect(state.loading).toBeTrue();
    expect(state.error).toBeNull();
  });

  it('loadClaimsSuccess populates the entity collection and pagination metadata', () => {
    const page: Page<ClaimSummary> = {
      content: [makeSummary({ id: 'a' }), makeSummary({ id: 'b' })],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 10,
    };
    const state = reducer(initialClaimsState, ClaimsActions.loadClaimsSuccess({ result: page }));
    expect(state.loading).toBeFalse();
    expect(state.totalElements).toBe(2);
    expect(Object.keys(state.entities).length).toBe(2);
  });

  describe('optimistic status update', () => {
    it('applies the new status immediately and stores a rollback snapshot', () => {
      const seeded = claimsAdapter.setAll([makeSummary({ id: 'CLM-1', status: 'SUBMITTED' })], initialClaimsState);
      const state = reducer(seeded, ClaimsActions.updateStatusOptimistic({ id: 'CLM-1', status: 'UNDER_REVIEW' }));

      expect(state.entities['CLM-1']?.status).toBe('UNDER_REVIEW');
      expect(state.rollbackSnapshot?.id).toBe('CLM-1');
      expect(state.rollbackSnapshot?.summary?.status).toBe('SUBMITTED');
    });

    it('rolls the entity back to its pre-update snapshot on failure', () => {
      const seeded = claimsAdapter.setAll([makeSummary({ id: 'CLM-1', status: 'SUBMITTED' })], initialClaimsState);
      const optimistic = reducer(seeded, ClaimsActions.updateStatusOptimistic({ id: 'CLM-1', status: 'UNDER_REVIEW' }));
      const rolledBack = reducer(optimistic, ClaimsActions.updateStatusFailure({ error: 'Version conflict' }));

      expect(rolledBack.entities['CLM-1']?.status).toBe('SUBMITTED');
      expect(rolledBack.statusUpdateError).toBe('Version conflict');
      expect(rolledBack.rollbackSnapshot).toBeNull();
    });

    it('also rolls back the selected claim detail when it matches the failed update', () => {
      const detail = makeDetail({ id: 'CLM-1', status: 'SUBMITTED' });
      const withSelected = { ...initialClaimsState, selectedClaim: detail };
      const optimistic = reducer(withSelected, ClaimsActions.updateStatusOptimistic({ id: 'CLM-1', status: 'PAID' }));
      expect(optimistic.selectedClaim?.status).toBe('PAID');

      const rolledBack = reducer(optimistic, ClaimsActions.updateStatusFailure({ error: 'nope' }));
      expect(rolledBack.selectedClaim?.status).toBe('SUBMITTED');
    });

    it('confirms the update and clears the rollback snapshot on success', () => {
      const seeded = claimsAdapter.setAll([makeSummary({ id: 'CLM-1', status: 'SUBMITTED' })], initialClaimsState);
      const optimistic = reducer(seeded, ClaimsActions.updateStatusOptimistic({ id: 'CLM-1', status: 'APPROVED' }));
      const confirmed = reducer(optimistic, ClaimsActions.updateStatusSuccess({ claim: makeDetail({ id: 'CLM-1', status: 'APPROVED', version: 2 }) }));

      expect(confirmed.entities['CLM-1']?.status).toBe('APPROVED');
      expect(confirmed.entities['CLM-1']?.version).toBe(2);
      expect(confirmed.rollbackSnapshot).toBeNull();
      expect(confirmed.statusUpdating).toBeFalse();
    });
  });

  it('claimCreated adds the new claim to the entity collection', () => {
    const state = reducer(initialClaimsState, ClaimsActions.claimCreated({ claim: makeDetail({ id: 'CLM-NEW' }) }));
    expect(state.entities['CLM-NEW']).toBeTruthy();
  });

  it('clearSelectedClaim resets the detail slice', () => {
    const withSelected = { ...initialClaimsState, selectedClaim: makeDetail(), audit: [{ id: '1' } as any] };
    const state = reducer(withSelected, ClaimsActions.clearSelectedClaim());
    expect(state.selectedClaim).toBeNull();
    expect(state.audit).toEqual([]);
  });
});
