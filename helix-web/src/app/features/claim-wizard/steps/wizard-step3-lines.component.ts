import { CommonModule } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Store } from '@ngrx/store';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { WizardActions } from '../../../store/wizard/wizard.actions';
import { selectLines, selectSelectedPolicyDetail } from '../../../store/wizard/wizard.reducer';

@Component({
  selector: 'app-wizard-step3-lines',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    EmptyStateComponent,
  ],
  templateUrl: './wizard-step3-lines.component.html',
  styleUrl: './wizard-step.shared.scss',
})
export class WizardStep3LinesComponent {
  private readonly store = inject(Store);

  readonly policy = this.store.selectSignal(selectSelectedPolicyDetail);
  readonly lines = this.store.selectSignal(selectLines);
  readonly coverages = computed(() => this.policy()?.coverages ?? []);
  readonly total = computed(() => this.lines().reduce((sum, l) => sum + (l.claimedAmount ?? 0), 0));

  addLine(): void {
    const firstCoverage = this.coverages()[0];
    this.store.dispatch(
      WizardActions.addLine({
        line: {
          coverageCode: firstCoverage?.code ?? '',
          description: '',
          claimedAmount: null,
        },
      }),
    );
  }

  setCoverage(tempId: string, coverageCode: string): void {
    this.store.dispatch(WizardActions.updateLine({ tempId, changes: { coverageCode } }));
  }

  setDescription(tempId: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.store.dispatch(WizardActions.updateLine({ tempId, changes: { description: value } }));
  }

  setAmount(tempId: string, event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    const value = raw === '' ? null : Number(raw);
    this.store.dispatch(WizardActions.updateLine({ tempId, changes: { claimedAmount: value } }));
  }

  removeLine(tempId: string): void {
    this.store.dispatch(WizardActions.removeLine({ tempId }));
  }

  coverageName(code: string): string {
    return this.coverages().find((c) => c.code === code)?.name ?? code;
  }
}
