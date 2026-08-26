import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { PolicyDetail, PolicySummary } from '../../core/models';
import { PoliciesApiService } from '../../core/services/policies-api.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

const COLUMNS = ['expand', 'policyNumber', 'holderName', 'productName', 'status', 'premiumAmount', 'coverageCount'] as const;

@Component({
  selector: 'app-policies-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    PageHeaderComponent,
    SkeletonComponent,
    EmptyStateComponent,
  ],
  templateUrl: './policies-list.component.html',
  styleUrl: './policies-list.component.scss',
})
export class PoliciesListComponent implements OnInit {
  private readonly api = inject(PoliciesApiService);

  readonly displayedColumns = [...COLUMNS];
  readonly searchControl = new FormControl('', { nonNullable: true });

  readonly policies = signal<PolicySummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly totalElements = signal(0);
  readonly page = signal(0);
  readonly size = signal(10);

  readonly expandedId = signal<string | null>(null);
  readonly details = signal<Record<string, PolicyDetail>>({});
  readonly detailLoadingId = signal<string | null>(null);

  readonly skeletonRows = Array.from({ length: 6 });

  ngOnInit(): void {
    this.load();
    this.searchControl.valueChanges.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe(() => {
      this.page.set(0);
      this.load();
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list({ page: this.page(), size: this.size(), q: this.searchControl.value || null }).subscribe({
      next: (result) => {
        this.policies.set(result.content);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load policies.');
        this.loading.set(false);
      },
    });
  }

  onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.size.set(event.pageSize);
    this.load();
  }

  toggleExpand(row: PolicySummary): void {
    if (this.expandedId() === row.id) {
      this.expandedId.set(null);
      return;
    }
    this.expandedId.set(row.id);
    if (!this.details()[row.id]) {
      this.detailLoadingId.set(row.id);
      this.api.getById(row.id).subscribe((detail) => {
        this.details.update((d) => ({ ...d, [row.id]: detail }));
        this.detailLoadingId.set(null);
      });
    }
  }

  isExpanded(row: PolicySummary): boolean {
    return this.expandedId() === row.id;
  }
}
