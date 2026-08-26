import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Claimant } from '../../core/models';

export const ClaimantsActions = createActionGroup({
  source: 'Claimants',
  events: {
    'Search': props<{ query: string }>(),
    'Search Success': props<{ results: Claimant[] }>(),
    'Search Failure': props<{ error: string }>(),
    'Clear Results': emptyProps(),
  },
});
