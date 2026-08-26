import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, withLatestFrom } from 'rxjs/operators';
import { ClaimsActions } from '../claims/claims.actions';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { PoliciesApiService } from '../../core/services/policies-api.service';
import { WizardPersistenceService } from './wizard-persistence.service';
import { WizardActions } from './wizard.actions';
import {
  selectDescription,
  selectDocuments,
  selectIncidentDate,
  selectLines,
  selectSelectedClaimant,
  selectSelectedPolicyDetail,
  selectSelectedPolicySummary,
  selectCurrentStep,
} from './wizard.reducer';

function extractMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) return (err.error?.message as string) ?? err.message ?? 'Request failed.';
  return 'Unexpected error.';
}

@Injectable()
export class WizardEffects {
  private readonly actions$ = inject(Actions);
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly claimsApi = inject(ClaimsApiService);
  private readonly policiesApi = inject(PoliciesApiService);
  private readonly persistence = inject(WizardPersistenceService);
  private readonly snackBar = inject(MatSnackBar);

  // Step 1 typeahead: policy search.
  searchPolicy$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WizardActions.searchPolicy),
      map((a) => a.query),
      debounceTime(300),
      distinctUntilChanged(),
      // switchMap cancels the previous in-flight policy search whenever a new keystroke
      // arrives, so a stale response can never overwrite fresher results (see also the
      // identical pattern in claimants.effects.ts for the step-2 claimant typeahead).
      switchMap((query) =>
        query.trim().length === 0
          ? of(WizardActions.searchPolicySuccess({ results: [] }))
          : this.policiesApi.list({ page: 0, size: 8, q: query }).pipe(
              map((page) => WizardActions.searchPolicySuccess({ results: page.content })),
              catchError((err) => of(WizardActions.searchPolicyFailure({ error: extractMessage(err) }))),
            ),
      ),
    ),
  );

  loadPolicyDetail$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WizardActions.selectPolicy),
      switchMap(({ policy }) =>
        this.policiesApi.getById(policy.id).pipe(
          map((detail) => WizardActions.policyDetailLoadSuccess({ policy: detail })),
          catchError((err) => of(WizardActions.policyDetailLoadFailure({ error: extractMessage(err) }))),
        ),
      ),
    ),
  );

  submit$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WizardActions.submit),
      withLatestFrom(
        this.store.select(selectSelectedPolicyDetail),
        this.store.select(selectSelectedClaimant),
        this.store.select(selectIncidentDate),
        this.store.select(selectDescription),
        this.store.select(selectLines),
        this.store.select(selectDocuments),
      ),
      switchMap(([, policy, claimant, incidentDate, description, lines, documents]) => {
        if (!policy || !claimant || !incidentDate) {
          return of(WizardActions.submitFailure({ error: 'Wizard is missing required data.' }));
        }
        return this.claimsApi
          .create({
            policyId: policy.id,
            claimantId: claimant.id,
            incidentDate,
            description,
            lines: lines.map((l) => ({
              coverageCode: l.coverageCode,
              description: l.description,
              claimedAmount: l.claimedAmount ?? 0,
            })),
            documents: documents.map((d) => ({
              fileName: d.fileName,
              contentType: d.contentType,
              sizeBytes: d.sizeBytes,
            })),
          })
          .pipe(
            map((claim) => WizardActions.submitSuccess({ claim })),
            catchError((err) => of(WizardActions.submitFailure({ error: extractMessage(err) }))),
          );
      }),
    ),
  );

  onSubmitSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(WizardActions.submitSuccess),
        map(({ claim }) => {
          this.persistence.clear();
          this.snackBar.open(`Claim ${claim.claimNumber} submitted.`, 'Dismiss', { duration: 4000 });
          this.router.navigate(['/claims', claim.id]);
          this.store.dispatch(ClaimsActions.claimCreated({ claim }));
          this.store.dispatch(WizardActions.resetWizard());
        }),
      ),
    { dispatch: false },
  );

  onSubmitFailure$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(WizardActions.submitFailure),
        map(({ error }) => {
          this.snackBar.open(`Submission failed: ${error}`, 'Dismiss', { duration: 6000, panelClass: 'helix-snackbar-error' });
        }),
      ),
    { dispatch: false },
  );

  saveDraft$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WizardActions.saveDraft),
      withLatestFrom(
        this.store.select(selectCurrentStep),
        this.store.select(selectSelectedPolicySummary),
        this.store.select(selectSelectedPolicyDetail),
        this.store.select(selectSelectedClaimant),
        this.store.select(selectIncidentDate),
        this.store.select(selectDescription),
        this.store.select(selectLines),
        this.store.select(selectDocuments),
      ),
      map(([, currentStep, selectedPolicySummary, selectedPolicyDetail, selectedClaimant, incidentDate, description, lines, documents]) => {
        const savedAt = new Date().toISOString();
        this.persistence.save({
          currentStep,
          selectedPolicySummary,
          selectedPolicyDetail,
          selectedClaimant,
          incidentDate,
          description,
          lines,
          documents,
          savedAt,
        });
        this.snackBar.open('Draft saved.', undefined, { duration: 2000 });
        return WizardActions.draftSaved({ savedAt });
      }),
    ),
  );

  restoreDraft$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WizardActions.restoreDraft),
      map(() => WizardActions.draftRestored({ draft: this.persistence.load() })),
    ),
  );

  discardDraft$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(WizardActions.discardDraft),
        map(() => this.persistence.clear()),
      ),
    { dispatch: false },
  );
}
