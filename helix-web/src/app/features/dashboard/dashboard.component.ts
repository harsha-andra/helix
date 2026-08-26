import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { DashboardApiService } from '../../core/services/dashboard-api.service';
import { ClaimStatus, DashboardSummary } from '../../core/models';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';

interface KpiCard {
  label: string;
  icon: string;
  value: string;
  hint: string;
}

const STATUS_BAR_COLOR: Record<ClaimStatus, string> = {
  SUBMITTED: 'var(--status-submitted-fg)',
  UNDER_REVIEW: 'var(--status-under-review-fg)',
  APPROVED: 'var(--status-approved-fg)',
  PARTIALLY_APPROVED: 'var(--status-partially-approved-fg)',
  DENIED: 'var(--status-denied-fg)',
  PAID: 'var(--status-paid-fg)',
  CLOSED: 'var(--status-closed-fg)',
};

const ACTION_ICON: Record<string, string> = {
  CLAIM_SUBMITTED: 'note_add',
  STATUS_CHANGED: 'sync_alt',
  PAYMENT_ISSUED: 'payments',
  CLAIM_CLOSED: 'task_alt',
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    RouterLink,
    PageHeaderComponent,
    SkeletonComponent,
    EmptyStateComponent,
    RelativeTimePipe,
    StatusLabelPipe,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly api = inject(DashboardApiService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly summary = signal<DashboardSummary | null>(null);

  readonly kpis = computed<KpiCard[]>(() => {
    const s = this.summary();
    if (!s) return [];
    return [
      { label: 'Open Claims', icon: 'folder_open', value: s.openClaims.toLocaleString(), hint: 'Submitted, in review, or partially approved' },
      { label: 'Awaiting Review', icon: 'hourglass_top', value: s.awaitingReview.toLocaleString(), hint: 'Currently with an adjuster' },
      { label: 'Total Reserved', icon: 'account_balance_wallet', value: this.formatCurrency(s.totalReservedAmount), hint: 'Exposure across open + approved claims' },
      { label: 'Avg Cycle Time', icon: 'schedule', value: `${s.avgCycleTimeDays.toFixed(1)}d`, hint: 'Submission to resolution, paid/closed claims' },
    ];
  });

  readonly maxStatusCount = computed(() => Math.max(1, ...(this.summary()?.byStatus.map((s) => s.count) ?? [1])));

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getSummary().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load the dashboard summary. The demo API may be temporarily unavailable.');
        this.loading.set(false);
      },
    });
  }

  barColor(status: ClaimStatus): string {
    return STATUS_BAR_COLOR[status] ?? 'var(--helix-brand)';
  }

  barWidth(count: number): string {
    return `${Math.max(4, (count / this.maxStatusCount()) * 100)}%`;
  }

  actionIcon(action: string): string {
    return ACTION_ICON[action] ?? 'circle_notifications';
  }

  private formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 0,
    }).format(value);
  }
}
