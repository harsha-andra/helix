import { createFeature, createReducer, createSelector, on } from '@ngrx/store';
import { WizardActions } from './wizard.actions';
import { WizardState } from './wizard.model';

export const initialWizardState: WizardState = {
  currentStep: 0,

  policySearchQuery: '',
  policySearchResults: [],
  policySearching: false,
  policySearchError: null,
  selectedPolicySummary: null,
  selectedPolicyDetail: null,
  policyDetailLoading: false,

  selectedClaimant: null,
  incidentDate: null,
  description: '',

  lines: [],
  documents: [],

  submitting: false,
  submitError: null,
  submittedClaim: null,

  draftSavedAt: null,
  draftAvailable: false,
};

let tempIdSeq = 0;
function nextTempId(prefix: string): string {
  tempIdSeq += 1;
  return `${prefix}-${tempIdSeq}`;
}

export const wizardFeature = createFeature({
  name: 'wizard',
  reducer: createReducer(
    initialWizardState,

    on(WizardActions.goToStep, (state, { step }): WizardState => ({ ...state, currentStep: step })),
    on(WizardActions.next, (state): WizardState => ({
      ...state,
      currentStep: Math.min(state.currentStep + 1, 4),
    })),
    on(WizardActions.back, (state): WizardState => ({ ...state, currentStep: Math.max(state.currentStep - 1, 0) })),

    on(WizardActions.searchPolicy, (state, { query }): WizardState => ({
      ...state,
      policySearchQuery: query,
      policySearching: true,
      policySearchError: null,
    })),
    on(WizardActions.searchPolicySuccess, (state, { results }): WizardState => ({
      ...state,
      policySearching: false,
      policySearchResults: results,
    })),
    on(WizardActions.searchPolicyFailure, (state, { error }): WizardState => ({
      ...state,
      policySearching: false,
      policySearchError: error,
    })),
    on(WizardActions.selectPolicy, (state, { policy }): WizardState => ({
      ...state,
      selectedPolicySummary: policy,
      selectedPolicyDetail: null,
      policyDetailLoading: true,
      // Changing the policy invalidates any claim lines picked against the previous one.
      lines: [],
    })),
    on(WizardActions.clearPolicy, (state): WizardState => ({
      ...state,
      selectedPolicySummary: null,
      selectedPolicyDetail: null,
      lines: [],
    })),
    on(WizardActions.policyDetailLoadSuccess, (state, { policy }): WizardState => ({
      ...state,
      selectedPolicyDetail: policy,
      policyDetailLoading: false,
    })),
    on(WizardActions.policyDetailLoadFailure, (state): WizardState => ({ ...state, policyDetailLoading: false })),

    on(WizardActions.selectClaimant, (state, { claimant }): WizardState => ({ ...state, selectedClaimant: claimant })),
    on(WizardActions.clearClaimant, (state): WizardState => ({ ...state, selectedClaimant: null })),
    on(WizardActions.setIncidentDetails, (state, { incidentDate, description }): WizardState => ({
      ...state,
      incidentDate,
      description,
    })),

    on(WizardActions.addLine, (state, { line }): WizardState => ({
      ...state,
      lines: [...state.lines, { ...line, tempId: nextTempId('line') }],
    })),
    on(WizardActions.updateLine, (state, { tempId, changes }): WizardState => ({
      ...state,
      lines: state.lines.map((l) => (l.tempId === tempId ? { ...l, ...changes } : l)),
    })),
    on(WizardActions.removeLine, (state, { tempId }): WizardState => ({
      ...state,
      lines: state.lines.filter((l) => l.tempId !== tempId),
    })),

    on(WizardActions.addDocument, (state, { document }): WizardState => ({
      ...state,
      documents: [...state.documents, { ...document, tempId: nextTempId('doc') }],
    })),
    on(WizardActions.removeDocument, (state, { tempId }): WizardState => ({
      ...state,
      documents: state.documents.filter((d) => d.tempId !== tempId),
    })),

    on(WizardActions.submit, (state): WizardState => ({ ...state, submitting: true, submitError: null })),
    on(WizardActions.submitSuccess, (state, { claim }): WizardState => ({
      ...state,
      submitting: false,
      submittedClaim: claim,
    })),
    on(WizardActions.submitFailure, (state, { error }): WizardState => ({
      ...state,
      submitting: false,
      submitError: error,
    })),

    on(WizardActions.draftSaved, (state, { savedAt }): WizardState => ({
      ...state,
      draftSavedAt: savedAt,
      draftAvailable: true,
    })),
    on(WizardActions.draftRestored, (state, { draft }): WizardState => {
      if (!draft) return { ...state, draftAvailable: false };
      return {
        ...initialWizardState,
        currentStep: draft.currentStep,
        selectedPolicySummary: draft.selectedPolicySummary,
        selectedPolicyDetail: draft.selectedPolicyDetail,
        selectedClaimant: draft.selectedClaimant,
        incidentDate: draft.incidentDate,
        description: draft.description,
        lines: draft.lines,
        documents: draft.documents,
        draftSavedAt: draft.savedAt,
        draftAvailable: true,
      };
    }),
    on(WizardActions.discardDraft, (state): WizardState => ({ ...initialWizardState, draftAvailable: false })),
    on(WizardActions.resetWizard, (): WizardState => ({ ...initialWizardState })),
  ),
  extraSelectors: ({
    selectSelectedPolicyDetail,
    selectSelectedClaimant,
    selectIncidentDate,
    selectDescription,
    selectLines,
    selectCurrentStep,
  }) => {
    // Validity is derived, never stored — a step's data is the single source of truth for
    // whether it's "complete", so there's no way for stored validity to drift out of sync
    // with the values a user is actively editing.
    const selectStep1Valid = createSelector(selectSelectedPolicyDetail, (policy) => policy !== null);
    const selectStep2Valid = createSelector(
      selectSelectedClaimant,
      selectIncidentDate,
      selectDescription,
      (claimant, incidentDate, description) =>
        claimant !== null && !!incidentDate && description.trim().length >= 10,
    );
    const selectStep3Valid = createSelector(
      selectLines,
      (lines) => lines.length > 0 && lines.every((l) => !!l.coverageCode && (l.claimedAmount ?? 0) > 0),
    );
    const selectStep4Valid = createSelector(selectCurrentStep, () => true); // documents are optional
    const selectStepValidity = createSelector(
      selectStep1Valid,
      selectStep2Valid,
      selectStep3Valid,
      selectStep4Valid,
      (s1, s2, s3, s4): boolean[] => [s1, s2, s3, s4, s1 && s2 && s3],
    );
    const selectCompletedSteps = createSelector(selectStepValidity, (validity) =>
      validity.map((valid, i) => (valid ? i : -1)).filter((i) => i >= 0),
    );
    /** A step is reachable once every step before it is valid; step 0 is always reachable. */
    const selectReachableSteps = createSelector(selectStepValidity, (validity) => {
      const reachable = [true, false, false, false, false];
      for (let i = 1; i < 5; i++) reachable[i] = reachable[i - 1] && validity[i - 1];
      return reachable;
    });
    const selectIsStepReachable = (step: number) =>
      createSelector(selectReachableSteps, (reachable) => reachable[step] ?? false);
    const selectCanGoNext = createSelector(
      selectCurrentStep,
      selectStepValidity,
      (step, validity) => validity[step] ?? false,
    );

    return {
      selectStep1Valid,
      selectStep2Valid,
      selectStep3Valid,
      selectStep4Valid,
      selectStepValidity,
      selectCompletedSteps,
      selectReachableSteps,
      selectIsStepReachable,
      selectCanGoNext,
    };
  },
});

// See the comment in claims.reducer.ts: createFeature leaves per-property selectors
// unprefixed by feature name.
export const {
  name: wizardFeatureKey,
  reducer: wizardReducer,
  selectCurrentStep,
  selectPolicySearchQuery,
  selectPolicySearchResults,
  selectPolicySearching,
  selectSelectedPolicySummary,
  selectSelectedPolicyDetail,
  selectPolicyDetailLoading,
  selectSelectedClaimant,
  selectIncidentDate,
  selectDescription,
  selectLines,
  selectDocuments,
  selectSubmitting,
  selectSubmitError,
  selectSubmittedClaim,
  selectDraftSavedAt,
  selectDraftAvailable,
  selectStep1Valid,
  selectStep2Valid,
  selectStep3Valid,
  selectStep4Valid,
  selectStepValidity,
  selectCompletedSteps,
  selectReachableSteps,
  selectIsStepReachable,
  selectCanGoNext,
} = wizardFeature;
