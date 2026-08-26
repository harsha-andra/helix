export type PolicyStatus = 'ACTIVE' | 'LAPSED' | 'CANCELLED' | 'EXPIRED';

export interface PolicySummary {
  id: string;
  policyNumber: string;
  productName: string;
  holderName: string;
  status: PolicyStatus;
  effectiveDate: string;
  expirationDate: string;
  premiumAmount: number;
  coverageCount: number;
}

export interface Coverage {
  id: string;
  code: string;
  name: string;
  limitAmount: number;
  deductibleAmount: number;
}

export interface PolicyDetail extends PolicySummary {
  coverages: Coverage[];
}
