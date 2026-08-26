import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { concat, of } from 'rxjs';
import { catchError, map, switchMap, withLatestFrom } from 'rxjs/operators';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { ClaimsActions } from './claims.actions';
import { selectQuery } from './claims.reducer';

function extractMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    return (err.error?.message as string) ?? err.message ?? 'Request failed.';
  }
  return 'Unexpected error.';
}

@Injectable()
export class ClaimsEffects {
  private readonly actions$ = inject(Actions);
  private readonly store = inject(Store);
  private readonly api = inject(ClaimsApiService);
  private readonly snackBar = inject(MatSnackBar);

  loadClaims$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ClaimsActions.setQuery, ClaimsActions.loadClaims),
      withLatestFrom(this.store.select(selectQuery)),
      switchMap(([, query]) =>
        this.api.list(query).pipe(
          map((result) => ClaimsActions.loadClaimsSuccess({ result })),
          catchError((err) => of(ClaimsActions.loadClaimsFailure({ error: extractMessage(err) }))),
        ),
      ),
    ),
  );

  loadClaimDetail$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ClaimsActions.loadClaimDetail),
      switchMap(({ id }) =>
        this.api.getById(id).pipe(
          map((claim) => ClaimsActions.loadClaimDetailSuccess({ claim })),
          catchError((err) => of(ClaimsActions.loadClaimDetailFailure({ error: extractMessage(err) }))),
        ),
      ),
    ),
  );

  loadAudit$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ClaimsActions.loadAudit),
      switchMap(({ id }) =>
        this.api.getAudit(id).pipe(
          map((events) => ClaimsActions.loadAuditSuccess({ events })),
          catchError((err) => of(ClaimsActions.loadAuditFailure({ error: extractMessage(err) }))),
        ),
      ),
    ),
  );

  // Optimistic update: the UI is patched immediately via `updateStatusOptimistic`, then this
  // effect fires the real request. On failure the reducer rolls the entity back to its
  // pre-update snapshot, and we surface the rejection as a snackbar error.
  updateStatus$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ClaimsActions.updateStatus),
      switchMap(({ id, status, version }) =>
        concat(
          of(ClaimsActions.updateStatusOptimistic({ id, status })),
          this.api.updateStatus(id, status, version).pipe(
            map((claim) => ClaimsActions.updateStatusSuccess({ claim })),
            catchError((err) => of(ClaimsActions.updateStatusFailure({ error: extractMessage(err) }))),
          ),
        ),
      ),
    ),
  );

  updateStatusSuccessToast$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(ClaimsActions.updateStatusSuccess),
        map(({ claim }) => {
          this.snackBar.open(`Claim ${claim.claimNumber} updated to ${claim.status.replace('_', ' ')}.`, 'Dismiss', {
            duration: 4000,
            panelClass: 'helix-snackbar-success',
          });
        }),
      ),
    { dispatch: false },
  );

  updateStatusFailureToast$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(ClaimsActions.updateStatusFailure),
        map(({ error }) => {
          this.snackBar.open(error, 'Dismiss', { duration: 6000, panelClass: 'helix-snackbar-error' });
        }),
      ),
    { dispatch: false },
  );
}
