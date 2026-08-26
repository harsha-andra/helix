import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { MatStepperModule } from '@angular/material/stepper';
import { Store } from '@ngrx/store';
import { WizardPersistenceService } from '../../store/wizard/wizard-persistence.service';
import { WizardActions } from '../../store/wizard/wizard.actions';
import {
  selectCanGoNext,
  selectCompletedSteps,
  selectCurrentStep,
  selectDraftSavedAt,
  selectIsStepReachable,
  selectSubmitError,
  selectSubmitting,
} from '../../store/wizard/wizard.reducer';
import { WIZARD_STEP_LABELS } from '../../store/wizard/wizard.model';
import { WizardStep1PolicyComponent } from './steps/wizard-step1-policy.component';
import { WizardStep2ClaimantComponent } from './steps/wizard-step2-claimant.component';
import { WizardStep3LinesComponent } from './steps/wizard-step3-lines.component';
import { WizardStep4DocumentsComponent } from './steps/wizard-step4-documents.component';
import { WizardStep5ReviewComponent } from './steps/wizard-step5-review.component';

@Component({
  selector: 'app-claim-wizard',
  standalone: true,
  imports: [
    CommonModule,
    MatStepperModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    WizardStep1PolicyComponent,
    WizardStep2ClaimantComponent,
    WizardStep3LinesComponent,
    WizardStep4DocumentsComponent,
    WizardStep5ReviewComponent,
  ],
  templateUrl: './claim-wizard.component.html',
  styleUrl: './claim-wizard.component.scss',
})
export class ClaimWizardComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly persistence = inject(WizardPersistenceService);

  readonly stepLabels = WIZARD_STEP_LABELS;
  readonly currentStep = this.store.selectSignal(selectCurrentStep);
  readonly completedSteps = this.store.selectSignal(selectCompletedSteps);
  readonly canGoNext = this.store.selectSignal(selectCanGoNext);
  readonly submitting = this.store.selectSignal(selectSubmitting);
  readonly submitError = this.store.selectSignal(selectSubmitError);
  readonly draftSavedAt = this.store.selectSignal(selectDraftSavedAt);

  readonly reachable = [0, 1, 2, 3, 4].map((i) => this.store.selectSignal(selectIsStepReachable(i)));

  showDraftBanner = false;

  ngOnInit(): void {
    // Only surface the "resume draft" banner when the store's wizard slice is still at its
    // untouched defaults (fresh session / hard reload) — if the user simply navigated away
    // and back within the app, the NgRx store already has their in-progress state.
    if (this.currentStep() === 0 && this.completedSteps().length === 0 && this.persistence.hasDraft()) {
      this.showDraftBanner = true;
    }
  }

  resumeDraft(): void {
    this.store.dispatch(WizardActions.restoreDraft());
    this.showDraftBanner = false;
  }

  discardDraft(): void {
    this.store.dispatch(WizardActions.discardDraft());
    this.showDraftBanner = false;
  }

  saveDraft(): void {
    this.store.dispatch(WizardActions.saveDraft());
  }

  isStepComplete(index: number): boolean {
    return this.completedSteps().includes(index);
  }

  onSelectionChange(event: StepperSelectionEvent): void {
    // Defense in depth: Material's `linear` stepper already blocks jumping ahead of an
    // incomplete step, but we re-check against the same NgRx reachability selector the
    // Next button uses, so the store stays the single source of truth for navigation.
    if (this.reachable[event.selectedIndex]()) {
      this.store.dispatch(WizardActions.goToStep({ step: event.selectedIndex }));
    } else {
      this.store.dispatch(WizardActions.goToStep({ step: this.currentStep() }));
    }
  }

  next(): void {
    this.store.dispatch(WizardActions.next());
  }

  back(): void {
    this.store.dispatch(WizardActions.back());
  }

  submit(): void {
    this.store.dispatch(WizardActions.submit());
  }
}
