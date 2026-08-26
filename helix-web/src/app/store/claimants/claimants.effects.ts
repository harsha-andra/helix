import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { ClaimantsApiService } from '../../core/services/claimants-api.service';
import { ClaimantsActions } from './claimants.actions';

@Injectable()
export class ClaimantsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(ClaimantsApiService);

  search$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ClaimantsActions.search),
      map((a) => a.query),
      debounceTime(300),
      distinctUntilChanged(),
      // switchMap is deliberate: every new keystroke cancels whatever search request is
      // still in flight for the previous keystroke, so a slow response for "sm" can never
      // land after (and overwrite) the fresher results for "smith".
      switchMap((query) =>
        query.trim().length === 0
          ? of(ClaimantsActions.searchSuccess({ results: [] }))
          : this.api.search(query).pipe(
              map((results) => ClaimantsActions.searchSuccess({ results })),
              catchError((err) =>
                of(
                  ClaimantsActions.searchFailure({
                    error: err instanceof HttpErrorResponse ? err.message : 'Search failed.',
                  }),
                ),
              ),
            ),
      ),
    ),
  );
}
