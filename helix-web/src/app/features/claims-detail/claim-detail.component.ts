import { CommonModule } from '@angular/common';
import { Component, inject, Input, OnDestroy, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { CLAIM_STATUS_TRANSITIONS, ClaimStatus } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { StatusChipComponent } from '../../shared/components/status-chip/status-chip.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { ClaimsActions } from '../../store/claims/claims.actions';
import {
  selectAudit,
  selectAuditLoading,
  selectSelectedClaim,
  selectSelectedClaimError,
  selectSelectedClaimLoading,
  selectStatusUpdating,
} from '../../store/claims/claims.reducer';

@Component({
  selector: 'app-claim-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTabsModule,
    MatTooltipModule,
    EmptyStateComponent,
    SkeletonComponent,
    StatusChipComponent,
    RelativeTimePipe,
    StatusLabelPipe,
  ],
  templateUrl: './claim-detail.component.html',
  styleUrl: './claim-detail.component.scss',
})
export class ClaimDetailComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  @Input() id!: string;

  readonly claim = this.store.selectSignal(selectSelectedClaim);
  readonly loading = this.store.selectSignal(selectSelectedClaimLoading);
  readonly error = this.store.selectSignal(selectSelectedClaimError);
  readonly audit = this.store.selectSignal(selectAudit);
  readonly auditLoading = this.store.selectSignal(selectAuditLoading);
  readonly statusUpdating = this.store.selectSignal(selectStatusUpdating);

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.store.dispatch(ClaimsActions.clearSelectedClaim());
  }

  load(): void {
    this.store.dispatch(ClaimsActions.loadClaimDetail({ id: this.id }));
    this.store.dispatch(ClaimsActions.loadAudit({ id: this.id }));
  }

  nextStatuses(status: ClaimStatus): ClaimStatus[] {
    return CLAIM_STATUS_TRANSITIONS[status] ?? [];
  }

  canChangeStatus(): boolean {
    // RBAC: only ADJUSTER may change a claim's status — mirrors the same
    // AuthService.hasRole check roleGuard uses at the route level.
    return this.auth.hasRole('ADJUSTER');
  }

  changeStatus(status: ClaimStatus): void {
    const claim = this.claim();
    if (!claim) return;
    this.store.dispatch(ClaimsActions.updateStatus({ id: claim.id, status, version: claim.version }));
  }

  back(): void {
    this.router.navigate(['/claims']);
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  documentIcon(contentType: string): string {
    if (contentType.startsWith('image/')) return 'image';
    if (contentType === 'application/pdf') return 'picture_as_pdf';
    return 'insert_drive_file';
  }

  auditIcon(action: string): string {
    switch (action) {
      case 'CLAIM_SUBMITTED':
        return 'note_add';
      case 'STATUS_CHANGED':
        return 'sync_alt';
      case 'PAYMENT_ISSUED':
        return 'payments';
      case 'CLAIM_CLOSED':
        return 'task_alt';
      default:
        return 'circle_notifications';
    }
  }
}
