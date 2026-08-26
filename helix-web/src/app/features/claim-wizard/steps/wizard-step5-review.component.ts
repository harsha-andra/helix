import { CommonModule } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Store } from '@ngrx/store';
import {
  selectDescription,
  selectDocuments,
  selectIncidentDate,
  selectLines,
  selectSelectedClaimant,
  selectSelectedPolicyDetail,
} from '../../../store/wizard/wizard.reducer';

@Component({
  selector: 'app-wizard-step5-review',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './wizard-step5-review.component.html',
  styleUrl: './wizard-step.shared.scss',
})
export class WizardStep5ReviewComponent {
  private readonly store = inject(Store);

  readonly policy = this.store.selectSignal(selectSelectedPolicyDetail);
  readonly claimant = this.store.selectSignal(selectSelectedClaimant);
  readonly incidentDate = this.store.selectSignal(selectIncidentDate);
  readonly description = this.store.selectSignal(selectDescription);
  readonly lines = this.store.selectSignal(selectLines);
  readonly documents = this.store.selectSignal(selectDocuments);

  readonly total = computed(() => this.lines().reduce((sum, l) => sum + (l.claimedAmount ?? 0), 0));
}
