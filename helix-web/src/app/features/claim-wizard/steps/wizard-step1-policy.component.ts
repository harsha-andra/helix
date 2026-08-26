import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Store } from '@ngrx/store';
import { PolicySummary } from '../../../core/models';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { WizardActions } from '../../../store/wizard/wizard.actions';
import {
  selectPolicyDetailLoading,
  selectPolicySearchQuery,
  selectPolicySearchResults,
  selectPolicySearching,
  selectSelectedPolicyDetail,
  selectSelectedPolicySummary,
} from '../../../store/wizard/wizard.reducer';

@Component({
  selector: 'app-wizard-step1-policy',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, EmptyStateComponent],
  templateUrl: './wizard-step1-policy.component.html',
  styleUrl: './wizard-step.shared.scss',
})
export class WizardStep1PolicyComponent {
  private readonly store = inject(Store);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly searchQuery = this.store.selectSignal(selectPolicySearchQuery);
  readonly results = this.store.selectSignal(selectPolicySearchResults);
  readonly searching = this.store.selectSignal(selectPolicySearching);
  readonly selectedPolicy = this.store.selectSignal(selectSelectedPolicySummary);
  readonly selectedPolicyDetail = this.store.selectSignal(selectSelectedPolicyDetail);
  readonly detailLoading = this.store.selectSignal(selectPolicyDetailLoading);

  constructor() {
    this.searchControl.setValue(this.searchQuery(), { emitEvent: false });
    // Every keystroke dispatches immediately; the debounce + cancel-in-flight logic lives
    // entirely in WizardEffects.searchPolicy$ (debounceTime(300) -> distinctUntilChanged ->
    // switchMap), which is the piece that actually matters for the "cancels the previous
    // search request" behavior — not this component.
    this.searchControl.valueChanges.subscribe((query) => {
      this.store.dispatch(WizardActions.searchPolicy({ query }));
    });
  }

  selectPolicy(policy: PolicySummary): void {
    this.store.dispatch(WizardActions.selectPolicy({ policy }));
  }

  changePolicy(): void {
    this.store.dispatch(WizardActions.clearPolicy());
  }
}
