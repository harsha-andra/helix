import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Claimant, ClaimDetail, PolicyDetail, PolicySummary } from '../../core/models';
import { WizardClaimLine, WizardDocument, WizardDraft } from './wizard.model';

export const WizardActions = createActionGroup({
  source: 'Wizard',
  events: {
    'Go To Step': props<{ step: number }>(),
    Next: emptyProps(),
    Back: emptyProps(),

    'Search Policy': props<{ query: string }>(),
    'Search Policy Success': props<{ results: PolicySummary[] }>(),
    'Search Policy Failure': props<{ error: string }>(),
    'Select Policy': props<{ policy: PolicySummary }>(),
    'Clear Policy': emptyProps(),
    'Policy Detail Load Success': props<{ policy: PolicyDetail }>(),
    'Policy Detail Load Failure': props<{ error: string }>(),

    'Select Claimant': props<{ claimant: Claimant }>(),
    'Clear Claimant': emptyProps(),
    'Set Incident Details': props<{ incidentDate: string; description: string }>(),

    'Add Line': props<{ line: Omit<WizardClaimLine, 'tempId'> }>(),
    'Update Line': props<{ tempId: string; changes: Partial<Omit<WizardClaimLine, 'tempId'>> }>(),
    'Remove Line': props<{ tempId: string }>(),

    'Add Document': props<{ document: Omit<WizardDocument, 'tempId'> }>(),
    'Remove Document': props<{ tempId: string }>(),

    Submit: emptyProps(),
    'Submit Success': props<{ claim: ClaimDetail }>(),
    'Submit Failure': props<{ error: string }>(),

    'Save Draft': emptyProps(),
    'Draft Saved': props<{ savedAt: string }>(),
    'Restore Draft': emptyProps(),
    'Draft Restored': props<{ draft: WizardDraft | null }>(),
    'Discard Draft': emptyProps(),

    'Reset Wizard': emptyProps(),
  },
});
