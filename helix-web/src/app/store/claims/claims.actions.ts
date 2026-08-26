import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { AuditEvent, ClaimDetail, ClaimStatus, ClaimSummary, Page } from '../../core/models';
import { ClaimsQuery } from '../../core/services/claims-api.service';

export const ClaimsActions = createActionGroup({
  source: 'Claims',
  events: {
    'Set Query': props<{ changes: Partial<ClaimsQuery> }>(),
    'Load Claims': emptyProps(),
    'Load Claims Success': props<{ result: Page<ClaimSummary> }>(),
    'Load Claims Failure': props<{ error: string }>(),

    'Load Claim Detail': props<{ id: string }>(),
    'Load Claim Detail Success': props<{ claim: ClaimDetail }>(),
    'Load Claim Detail Failure': props<{ error: string }>(),
    'Clear Selected Claim': emptyProps(),

    'Load Audit': props<{ id: string }>(),
    'Load Audit Success': props<{ events: AuditEvent[] }>(),
    'Load Audit Failure': props<{ error: string }>(),

    'Update Status': props<{ id: string; status: ClaimStatus; version: number }>(),
    'Update Status Optimistic': props<{ id: string; status: ClaimStatus }>(),
    'Update Status Success': props<{ claim: ClaimDetail }>(),
    'Update Status Failure': props<{ error: string }>(),

    'Claim Created': props<{ claim: ClaimDetail }>(),
  },
});
