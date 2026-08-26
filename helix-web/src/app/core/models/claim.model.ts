import { Adjuster, Claimant } from './claimant.model';
import { PolicySummary } from './policy.model';
import { DocumentRef } from './document.model';

export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'PARTIALLY_APPROVED'
  | 'DENIED'
  | 'PAID'
  | 'CLOSED';

export const CLAIM_STATUSES: ClaimStatus[] = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'APPROVED',
  'PARTIALLY_APPROVED',
  'DENIED',
  'PAID',
  'CLOSED',
];

/** Statuses a claim may transition to next, keyed by current status. Drives the status-change menu. */
export const CLAIM_STATUS_TRANSITIONS: Record<ClaimStatus, ClaimStatus[]> = {
  SUBMITTED: ['UNDER_REVIEW', 'DENIED'],
  UNDER_REVIEW: ['APPROVED', 'PARTIALLY_APPROVED', 'DENIED'],
  APPROVED: ['PAID', 'CLOSED'],
  PARTIALLY_APPROVED: ['PAID', 'CLOSED'],
  DENIED: ['CLOSED'],
  PAID: ['CLOSED'],
  CLOSED: [],
};

export interface ClaimSummary {
  id: string;
  claimNumber: string;
  policyNumber: string;
  claimantName: string;
  status: ClaimStatus;
  totalAmount: number;
  incidentDate: string;
  submittedAt: string;
  assignedAdjuster: string | null;
  lineCount: number;
  version: number;
}

export type ClaimLineStatus = 'PENDING' | 'APPROVED' | 'DENIED';

export interface ClaimLine {
  id: string;
  lineNumber: number;
  coverageCode: string;
  description: string;
  claimedAmount: number;
  approvedAmount: number | null;
  status: ClaimLineStatus;
}

export interface ClaimDetail extends ClaimSummary {
  policy: PolicySummary;
  claimant: Claimant;
  adjuster: Adjuster | null;
  description: string;
  lines: ClaimLine[];
  documents: DocumentRef[];
}
