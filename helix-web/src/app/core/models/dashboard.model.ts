import { AuditEvent } from './audit.model';
import { ClaimStatus } from './claim.model';

export interface StatusCount {
  status: ClaimStatus;
  count: number;
}

export interface DashboardSummary {
  openClaims: number;
  awaitingReview: number;
  totalReservedAmount: number;
  avgCycleTimeDays: number;
  byStatus: StatusCount[];
  recentActivity: AuditEvent[];
}
