import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Store } from '@ngrx/store';
import { Claimant } from '../../../core/models';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ClaimantsActions } from '../../../store/claimants/claimants.actions';
import {
  selectClaimantsResults,
  selectClaimantsSearching,
} from '../../../store/claimants/claimants.reducer';
import { WizardActions } from '../../../store/wizard/wizard.actions';
import { selectDescription, selectIncidentDate, selectSelectedClaimant } from '../../../store/wizard/wizard.reducer';

@Component({
  selector: 'app-wizard-step2-claimant',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    EmptyStateComponent,
  ],
  templateUrl: './wizard-step2-claimant.component.html',
  styleUrl: './wizard-step.shared.scss',
})
export class WizardStep2ClaimantComponent {
  private readonly store = inject(Store);
  private readonly fb = inject(FormBuilder);

  readonly today = new Date();
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly results = this.store.selectSignal(selectClaimantsResults);
  readonly searching = this.store.selectSignal(selectClaimantsSearching);
  readonly selectedClaimant = this.store.selectSignal(selectSelectedClaimant);

  readonly incidentForm = this.fb.group({
    incidentDate: this.fb.control<Date | null>(null),
    description: this.fb.control<string>(''),
  });

  constructor() {
    const currentDescription = this.store.selectSignal(selectDescription)();
    const currentIncidentDate = this.store.selectSignal(selectIncidentDate)();
    this.incidentForm.patchValue({
      incidentDate: currentIncidentDate ? new Date(currentIncidentDate) : null,
      description: currentDescription,
    });

    // Typeahead: dispatch on every keystroke — debounce + switchMap cancellation happens
    // inside ClaimantsEffects.search$, reused here and on the standalone /claimants page.
    this.searchControl.valueChanges.subscribe((query) => {
      this.store.dispatch(ClaimantsActions.search({ query }));
    });

    this.incidentForm.valueChanges.subscribe((value) => {
      const incidentDate = value.incidentDate ? new Date(value.incidentDate).toISOString() : '';
      this.store.dispatch(
        WizardActions.setIncidentDetails({ incidentDate, description: value.description ?? '' }),
      );
    });
  }

  selectClaimant(claimant: Claimant): void {
    this.store.dispatch(WizardActions.selectClaimant({ claimant }));
    this.store.dispatch(ClaimantsActions.clearResults());
    this.searchControl.setValue('', { emitEvent: false });
  }

  changeClaimant(): void {
    this.store.dispatch(WizardActions.clearClaimant());
  }
}
