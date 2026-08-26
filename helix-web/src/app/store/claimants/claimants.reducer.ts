import { createFeature, createReducer, on } from '@ngrx/store';
import { Claimant } from '../../core/models';
import { ClaimantsActions } from './claimants.actions';

export interface ClaimantsState {
  query: string;
  results: Claimant[];
  searching: boolean;
  error: string | null;
}

export const initialClaimantsState: ClaimantsState = {
  query: '',
  results: [],
  searching: false,
  error: null,
};

export const claimantsFeature = createFeature({
  name: 'claimants',
  reducer: createReducer(
    initialClaimantsState,
    on(ClaimantsActions.search, (state, { query }): ClaimantsState => ({ ...state, query, searching: true, error: null })),
    on(ClaimantsActions.searchSuccess, (state, { results }): ClaimantsState => ({ ...state, searching: false, results })),
    on(ClaimantsActions.searchFailure, (state, { error }): ClaimantsState => ({ ...state, searching: false, error })),
    on(ClaimantsActions.clearResults, (state): ClaimantsState => ({ ...state, results: [], query: '' })),
  ),
});

// See the comment in claims.reducer.ts: createFeature leaves per-property selectors
// unprefixed, so we alias them here to avoid clashing with other features' `selectQuery` /
// `selectError` when both are imported into the same file.
export const {
  name: claimantsFeatureKey,
  reducer: claimantsReducer,
  selectClaimantsState,
  selectQuery: selectClaimantsQuery,
  selectResults: selectClaimantsResults,
  selectSearching: selectClaimantsSearching,
  selectError: selectClaimantsError,
} = claimantsFeature;
