import { Claimant, PolicyDetail } from '../../core/models';
import { WizardActions } from './wizard.actions';
import {
  initialWizardState,
  selectCanGoNext,
  selectCompletedSteps,
  selectCurrentStep,
  selectIsStepReachable,
  selectStep1Valid,
  selectStep2Valid,
  selectStep3Valid,
  wizardFeature,
} from './wizard.reducer';
import { WizardDraft } from './wizard.model';

const { reducer } = wizardFeature;

function withWizardState(wizardState: ReturnType<typeof reducer>) {
  return { wizard: wizardState };
}

const POLICY: PolicyDetail = {
  id: 'POL-1',
  policyNumber: 'POL-2024-018842',
  productName: 'Auto Standard',
  holderName: 'Jane Doe',
  status: 'ACTIVE',
  effectiveDate: '2024-01-01T00:00:00.000Z',
  expirationDate: '2025-01-01T00:00:00.000Z',
  premiumAmount: 900,
  coverageCount: 1,
  coverages: [{ id: 'COV-1', code: 'COLL', name: 'Collision', limitAmount: 10000, deductibleAmount: 500 }],
};

const CLAIMANT: Claimant = {
  id: 'CLT-1',
  firstName: 'Jane',
  lastName: 'Doe',
  email: 'jane@example.com',
  phone: '555-0100',
  city: 'Austin',
  state: 'TX',
};

describe('wizardFeature reducer', () => {
  describe('step navigation', () => {
    it('starts on step 0', () => {
      expect(initialWizardState.currentStep).toBe(0);
    });

    it('next() advances the current step, capped at the final step (index 4)', () => {
      let state = initialWizardState;
      for (let i = 0; i < 6; i++) {
        state = reducer(state, WizardActions.next());
      }
      expect(state.currentStep).toBe(4);
    });

    it('back() retreats the current step, capped at 0', () => {
      const state = reducer({ ...initialWizardState, currentStep: 1 }, WizardActions.back());
      expect(state.currentStep).toBe(0);

      const clampedAtZero = reducer(initialWizardState, WizardActions.back());
      expect(clampedAtZero.currentStep).toBe(0);
    });

    it('goToStep jumps directly to the requested step', () => {
      const state = reducer(initialWizardState, WizardActions.goToStep({ step: 3 }));
      expect(state.currentStep).toBe(3);
    });

    it('selectCurrentStep selector reads the current step from the root state', () => {
      const state = reducer(initialWizardState, WizardActions.goToStep({ step: 2 }));
      expect(selectCurrentStep(withWizardState(state) as any)).toBe(2);
    });
  });

  describe('validity gating', () => {
    it('step 1 (policy) is invalid until a policy detail has loaded', () => {
      expect(selectStep1Valid(withWizardState(initialWizardState) as any)).toBeFalse();

      const withPolicy = reducer(initialWizardState, WizardActions.policyDetailLoadSuccess({ policy: POLICY }));
      expect(selectStep1Valid(withWizardState(withPolicy) as any)).toBeTrue();
    });

    it('step 2 (claimant + incident) requires a claimant, an incident date, and a >=10 char description', () => {
      const withClaimant = reducer(initialWizardState, WizardActions.selectClaimant({ claimant: CLAIMANT }));
      expect(selectStep2Valid(withWizardState(withClaimant) as any)).toBeFalse();

      const withShortDescription = reducer(
        withClaimant,
        WizardActions.setIncidentDetails({ incidentDate: '2025-01-01T00:00:00.000Z', description: 'too short' }),
      );
      expect(selectStep2Valid(withWizardState(withShortDescription) as any)).toBeFalse();

      const complete = reducer(
        withClaimant,
        WizardActions.setIncidentDetails({
          incidentDate: '2025-01-01T00:00:00.000Z',
          description: 'Rear-end collision at the intersection.',
        }),
      );
      expect(selectStep2Valid(withWizardState(complete) as any)).toBeTrue();
    });

    it('step 3 (claim lines) requires at least one line with a coverage code and a positive amount', () => {
      expect(selectStep3Valid(withWizardState(initialWizardState) as any)).toBeFalse();

      const withEmptyLine = reducer(
        initialWizardState,
        WizardActions.addLine({ line: { coverageCode: 'COLL', description: '', claimedAmount: null } }),
      );
      expect(selectStep3Valid(withWizardState(withEmptyLine) as any)).toBeFalse();

      const tempId = withEmptyLine.lines[0].tempId;
      const withAmount = reducer(
        withEmptyLine,
        WizardActions.updateLine({ tempId, changes: { claimedAmount: 500 } }),
      );
      expect(selectStep3Valid(withWizardState(withAmount) as any)).toBeTrue();
    });

    it('selectIsStepReachable(n) requires every prior step to be valid, and step 0 is always reachable', () => {
      expect(selectIsStepReachable(0)(withWizardState(initialWizardState) as any)).toBeTrue();
      expect(selectIsStepReachable(1)(withWizardState(initialWizardState) as any)).toBeFalse();

      const withPolicy = reducer(initialWizardState, WizardActions.policyDetailLoadSuccess({ policy: POLICY }));
      expect(selectIsStepReachable(1)(withWizardState(withPolicy) as any)).toBeTrue();
      expect(selectIsStepReachable(2)(withWizardState(withPolicy) as any)).toBeFalse();
    });

    it('selectCanGoNext reflects the validity of the step the user is currently on', () => {
      const onStep0Invalid = { ...initialWizardState, currentStep: 0 };
      expect(selectCanGoNext(withWizardState(onStep0Invalid) as any)).toBeFalse();

      const onStep0Valid = reducer(
        { ...initialWizardState, currentStep: 0 },
        WizardActions.policyDetailLoadSuccess({ policy: POLICY }),
      );
      expect(selectCanGoNext(withWizardState(onStep0Valid) as any)).toBeTrue();
    });

    it('selectCompletedSteps lists every step index whose data currently validates', () => {
      const withPolicy = reducer(initialWizardState, WizardActions.policyDetailLoadSuccess({ policy: POLICY }));
      expect(selectCompletedSteps(withWizardState(withPolicy) as any)).toEqual([0, 3]); // step 3 (documents) is always valid
    });
  });

  describe('draft restore', () => {
    const draft: WizardDraft = {
      currentStep: 2,
      selectedPolicySummary: POLICY,
      selectedPolicyDetail: POLICY,
      selectedClaimant: CLAIMANT,
      incidentDate: '2025-01-01T00:00:00.000Z',
      description: 'Rear-end collision at the intersection.',
      lines: [{ tempId: 'line-1', coverageCode: 'COLL', description: 'Bumper', claimedAmount: 800 }],
      documents: [],
      savedAt: '2025-01-05T00:00:00.000Z',
    };

    it('restores every persisted field and marks a draft as available', () => {
      const state = reducer(initialWizardState, WizardActions.draftRestored({ draft }));

      expect(state.currentStep).toBe(2);
      expect(state.selectedPolicyDetail?.policyNumber).toBe('POL-2024-018842');
      expect(state.selectedClaimant?.email).toBe('jane@example.com');
      expect(state.lines.length).toBe(1);
      expect(state.draftSavedAt).toBe('2025-01-05T00:00:00.000Z');
      expect(state.draftAvailable).toBeTrue();
    });

    it('restoring a null draft (nothing persisted) just clears draftAvailable without touching progress', () => {
      const inProgress = reducer(initialWizardState, WizardActions.goToStep({ step: 1 }));
      const state = reducer(inProgress, WizardActions.draftRestored({ draft: null }));

      expect(state.currentStep).toBe(1);
      expect(state.draftAvailable).toBeFalse();
    });

    it('discardDraft resets the whole wizard back to its initial state', () => {
      const restored = reducer(initialWizardState, WizardActions.draftRestored({ draft }));
      const discarded = reducer(restored, WizardActions.discardDraft());

      expect(discarded).toEqual({ ...initialWizardState, draftAvailable: false });
    });
  });
});
