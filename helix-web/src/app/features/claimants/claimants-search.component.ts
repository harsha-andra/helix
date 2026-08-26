import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Store } from '@ngrx/store';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ClaimantsActions } from '../../store/claimants/claimants.actions';
import { selectClaimantsQuery, selectClaimantsResults, selectClaimantsSearching } from '../../store/claimants/claimants.reducer';

@Component({
  selector: 'app-claimants-search',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    EmptyStateComponent,
  ],
  templateUrl: './claimants-search.component.html',
  styleUrl: './claimants-search.component.scss',
})
export class ClaimantsSearchComponent {
  private readonly store = inject(Store);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly results = this.store.selectSignal(selectClaimantsResults);
  readonly searching = this.store.selectSignal(selectClaimantsSearching);
  readonly query = this.store.selectSignal(selectClaimantsQuery);

  constructor() {
    // Debounce + switchMap cancellation lives in ClaimantsEffects.search$ — every keystroke
    // here just dispatches; the effect is what guarantees a slow "sm" response can never
    // clobber the fresher "smith" results.
    this.searchControl.valueChanges.subscribe((query) => {
      this.store.dispatch(ClaimantsActions.search({ query }));
    });
  }
}
