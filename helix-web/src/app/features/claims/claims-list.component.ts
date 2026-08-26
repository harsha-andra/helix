import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSort, MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { CLAIM_STATUSES, ClaimStatus, ClaimSummary } from '../../core/models';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { StatusChipComponent } from '../../shared/components/status-chip/status-chip.component';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { ClaimsActions } from '../../store/claims/claims.actions';
import {
  selectAllClaims,
  selectError,
  selectLoading,
  selectQuery,
  selectTotalElements,
} from '../../store/claims/claims.reducer';

const COLUMNS = [
  'claimNumber',
  'claimantName',
  'policyNumber',
  'status',
  'totalAmount',
  'incidentDate',
  'assignedAdjuster',
] as const;

@Component({
  selector: 'app-claims-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    PageHeaderComponent,
    SkeletonComponent,
    EmptyStateComponent,
    StatusChipComponent,
    StatusLabelPipe,
  ],
  templateUrl: './claims-list.component.html',
  styleUrl: './claims-list.component.scss',
})
export class ClaimsListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly displayedColumns = [...COLUMNS];
  readonly statuses = CLAIM_STATUSES;
  readonly searchControl = new FormControl('', { nonNullable: true });

  readonly claims = this.store.selectSignal(selectAllClaims);
  readonly loading = this.store.selectSignal(selectLoading);
  readonly error = this.store.selectSignal(selectError);
  readonly totalElements = this.store.selectSignal(selectTotalElements);
  readonly query = this.store.selectSignal(selectQuery);

  readonly activeStatuses = signal<Set<ClaimStatus>>(new Set());
  readonly sortActive = computed(() => this.query().sort?.split(',')[0] ?? 'submittedAt');
  readonly sortDirection = computed(() => (this.query().sort?.split(',')[1] as 'asc' | 'desc') ?? 'desc');
  readonly skeletonRows = Array.from({ length: 8 });

  ngOnInit(): void {
    const initialQ = this.route.snapshot.queryParamMap.get('q');
    if (initialQ) {
      this.searchControl.setValue(initialQ, { emitEvent: false });
      this.store.dispatch(ClaimsActions.setQuery({ changes: { q: initialQ, page: 0 } }));
      // Consume the ?q= param seeded by the shell's global search once, then drop it from
      // the URL so it doesn't stick around as the (increasingly stale) source of truth.
      this.router.navigate([], { queryParams: {}, relativeTo: this.route });
    } else {
      this.store.dispatch(ClaimsActions.loadClaims());
    }

    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => {
        this.store.dispatch(ClaimsActions.setQuery({ changes: { q: q || null, page: 0 } }));
      });
  }

  onStatusToggle(status: ClaimStatus, selected: boolean): void {
    const next = new Set(this.activeStatuses());
    if (selected) next.add(status);
    else next.delete(status);
    this.activeStatuses.set(next);
    this.store.dispatch(
      ClaimsActions.setQuery({ changes: { status: next.size ? Array.from(next).join(',') : null, page: 0 } }),
    );
  }

  onPage(event: PageEvent): void {
    this.store.dispatch(ClaimsActions.setQuery({ changes: { page: event.pageIndex, size: event.pageSize } }));
  }

  onSort(sort: Sort): void {
    const direction = sort.direction || 'asc';
    this.store.dispatch(
      ClaimsActions.setQuery({ changes: { sort: sort.direction ? `${sort.active},${direction}` : null, page: 0 } }),
    );
  }

  openClaim(claim: ClaimSummary): void {
    this.router.navigate(['/claims', claim.id]);
  }

  clearFilters(): void {
    this.activeStatuses.set(new Set());
    this.searchControl.setValue('');
    this.store.dispatch(ClaimsActions.setQuery({ changes: { status: null, q: null, page: 0 } }));
  }

  retry(): void {
    this.store.dispatch(ClaimsActions.loadClaims());
  }
}
