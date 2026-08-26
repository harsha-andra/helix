import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { createFeature, createReducer, createSelector, on } from '@ngrx/store';
import { AuditEvent, ClaimDetail, ClaimSummary } from '../../core/models';
import { ClaimsQuery } from '../../core/services/claims-api.service';
import { ClaimsActions } from './claims.actions';

export interface ClaimsState extends EntityState<ClaimSummary> {
  loading: boolean;
  error: string | null;
  totalElements: number;
  totalPages: number;
  query: ClaimsQuery;

  selectedClaim: ClaimDetail | null;
  selectedClaimLoading: boolean;
  selectedClaimError: string | null;

  audit: AuditEvent[];
  auditLoading: boolean;

  statusUpdating: boolean;
  statusUpdateError: string | null;
  /** Snapshot used to roll back the optimistic update if the server rejects it. */
  rollbackSnapshot: { id: string; summary: ClaimSummary | null; detail: ClaimDetail | null } | null;
}

export const claimsAdapter = createEntityAdapter<ClaimSummary>({
  selectId: (c) => c.id,
});

const initialQuery: ClaimsQuery = { page: 0, size: 10, status: null, q: null, sort: 'incidentDate,desc' };

export const initialClaimsState: ClaimsState = claimsAdapter.getInitialState({
  loading: false,
  error: null,
  totalElements: 0,
  totalPages: 0,
  query: initialQuery,
  selectedClaim: null,
  selectedClaimLoading: false,
  selectedClaimError: null,
  audit: [],
  auditLoading: false,
  statusUpdating: false,
  statusUpdateError: null,
  rollbackSnapshot: null,
});

export const claimsFeature = createFeature({
  name: 'claims',
  reducer: createReducer(
    initialClaimsState,

    on(ClaimsActions.setQuery, (state, { changes }): ClaimsState => ({
      ...state,
      query: { ...state.query, ...changes },
    })),

    on(ClaimsActions.loadClaims, (state): ClaimsState => ({ ...state, loading: true, error: null })),
    on(ClaimsActions.loadClaimsSuccess, (state, { result }): ClaimsState =>
      claimsAdapter.setAll(result.content, {
        ...state,
        loading: false,
        totalElements: result.totalElements,
        totalPages: result.totalPages,
        query: { ...state.query, page: result.number, size: result.size },
      }),
    ),
    on(ClaimsActions.loadClaimsFailure, (state, { error }): ClaimsState => ({ ...state, loading: false, error })),

    on(ClaimsActions.loadClaimDetail, (state): ClaimsState => ({
      ...state,
      selectedClaimLoading: true,
      selectedClaimError: null,
      selectedClaim: null,
    })),
    on(ClaimsActions.loadClaimDetailSuccess, (state, { claim }): ClaimsState => ({
      ...state,
      selectedClaimLoading: false,
      selectedClaim: claim,
    })),
    on(ClaimsActions.loadClaimDetailFailure, (state, { error }): ClaimsState => ({
      ...state,
      selectedClaimLoading: false,
      selectedClaimError: error,
    })),
    on(ClaimsActions.clearSelectedClaim, (state): ClaimsState => ({
      ...state,
      selectedClaim: null,
      selectedClaimError: null,
      audit: [],
    })),

    on(ClaimsActions.loadAudit, (state): ClaimsState => ({ ...state, auditLoading: true })),
    on(ClaimsActions.loadAuditSuccess, (state, { events }): ClaimsState => ({
      ...state,
      auditLoading: false,
      audit: events,
    })),
    on(ClaimsActions.loadAuditFailure, (state): ClaimsState => ({ ...state, auditLoading: false })),

    // --- Optimistic status update ---------------------------------------------------
    on(ClaimsActions.updateStatus, (state): ClaimsState => ({
      ...state,
      statusUpdating: true,
      statusUpdateError: null,
    })),
    on(ClaimsActions.updateStatusOptimistic, (state, { id, status }): ClaimsState => {
      const previousSummary = state.entities[id] ?? null;
      const previousDetail = state.selectedClaim && state.selectedClaim.id === id ? state.selectedClaim : null;

      let next = state;
      if (previousSummary) {
        next = claimsAdapter.updateOne({ id, changes: { status } }, next);
      }
      const selectedClaim =
        next.selectedClaim && next.selectedClaim.id === id ? { ...next.selectedClaim, status } : next.selectedClaim;

      return {
        ...next,
        selectedClaim,
        rollbackSnapshot: { id, summary: previousSummary, detail: previousDetail },
      };
    }),
    on(ClaimsActions.updateStatusSuccess, (state, { claim }): ClaimsState => {
      const next = claimsAdapter.upsertOne(
        {
          id: claim.id,
          claimNumber: claim.claimNumber,
          policyNumber: claim.policyNumber,
          claimantName: claim.claimantName,
          status: claim.status,
          totalAmount: claim.totalAmount,
          incidentDate: claim.incidentDate,
          submittedAt: claim.submittedAt,
          assignedAdjuster: claim.assignedAdjuster,
          lineCount: claim.lineCount,
          version: claim.version,
        },
        state,
      );
      return {
        ...next,
        statusUpdating: false,
        rollbackSnapshot: null,
        selectedClaim: next.selectedClaim && next.selectedClaim.id === claim.id ? claim : next.selectedClaim,
      };
    }),
    on(ClaimsActions.updateStatusFailure, (state, { error }): ClaimsState => {
      const snapshot = state.rollbackSnapshot;
      let next = state;
      if (snapshot?.summary) {
        next = claimsAdapter.updateOne({ id: snapshot.id, changes: snapshot.summary }, next);
      }
      const selectedClaim = snapshot?.detail ? snapshot.detail : next.selectedClaim;
      return {
        ...next,
        selectedClaim,
        statusUpdating: false,
        statusUpdateError: error,
        rollbackSnapshot: null,
      };
    }),

    on(ClaimsActions.claimCreated, (state, { claim }): ClaimsState =>
      claimsAdapter.addOne(
        {
          id: claim.id,
          claimNumber: claim.claimNumber,
          policyNumber: claim.policyNumber,
          claimantName: claim.claimantName,
          status: claim.status,
          totalAmount: claim.totalAmount,
          incidentDate: claim.incidentDate,
          submittedAt: claim.submittedAt,
          assignedAdjuster: claim.assignedAdjuster,
          lineCount: claim.lineCount,
          version: claim.version,
        },
        state,
      ),
    ),
  ),
  extraSelectors: ({ selectClaimsState, selectEntities, selectIds }) => ({
    selectAllClaims: createSelector(selectIds, selectEntities, (ids, entities) =>
      ids.map((id) => entities[id]!).filter(Boolean),
    ),
  }),
});

// NgRx's createFeature only prefixes the *whole-state* selector with the feature name
// (selectClaimsState); every per-property selector is `select` + the capitalized state key,
// unprefixed — e.g. `loading` -> `selectLoading`, regardless of the feature's name.
export const {
  name: claimsFeatureKey,
  reducer: claimsReducer,
  selectQuery,
  selectLoading,
  selectError,
  selectTotalElements,
  selectTotalPages,
  selectSelectedClaim,
  selectSelectedClaimLoading,
  selectSelectedClaimError,
  selectAudit,
  selectAuditLoading,
  selectStatusUpdating,
  selectStatusUpdateError,
  selectAllClaims,
} = claimsFeature;
