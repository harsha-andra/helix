import { Claimant, ClaimDetail, PolicyDetail, PolicySummary } from '../../core/models';

export interface WizardClaimLine {
  tempId: string;
  coverageCode: string;
  description: string;
  claimedAmount: number | null;
}

export interface WizardDocument {
  tempId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
}

export const WIZARD_STEP_COUNT = 5;
export const WIZARD_STEP_LABELS = [
  'Select Policy',
  'Claimant & Incident',
  'Claim Lines',
  'Documents',
  'Review & Submit',
] as const;

export interface WizardState {
  currentStep: number;

  // Step 1 — Select Policy
  policySearchQuery: string;
  policySearchResults: PolicySummary[];
  policySearching: boolean;
  policySearchError: string | null;
  selectedPolicySummary: PolicySummary | null;
  selectedPolicyDetail: PolicyDetail | null;
  policyDetailLoading: boolean;

  // Step 2 — Claimant & Incident
  selectedClaimant: Claimant | null;
  incidentDate: string | null;
  description: string;

  // Step 3 — Claim Lines
  lines: WizardClaimLine[];

  // Step 4 — Documents
  documents: WizardDocument[];

  // Step 5 — Review & Submit
  submitting: boolean;
  submitError: string | null;
  submittedClaim: ClaimDetail | null;

  draftSavedAt: string | null;
  draftAvailable: boolean;
}

/** The subset of wizard state that gets persisted to localStorage for "save draft / resume". */
export interface WizardDraft {
  currentStep: number;
  selectedPolicySummary: PolicySummary | null;
  selectedPolicyDetail: PolicyDetail | null;
  selectedClaimant: Claimant | null;
  incidentDate: string | null;
  description: string;
  lines: WizardClaimLine[];
  documents: WizardDocument[];
  savedAt: string;
}
