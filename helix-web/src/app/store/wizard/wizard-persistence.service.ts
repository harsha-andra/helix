import { Injectable } from '@angular/core';
import { WizardDraft } from './wizard.model';

const STORAGE_KEY = 'helix.wizard.draft';

/** Thin localStorage wrapper isolated behind a service so the effect stays testable. */
@Injectable({ providedIn: 'root' })
export class WizardPersistenceService {
  save(draft: WizardDraft): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
    } catch {
      /* storage unavailable (private mode, quota) — draft simply won't persist */
    }
  }

  load(): WizardDraft | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as WizardDraft) : null;
    } catch {
      return null;
    }
  }

  hasDraft(): boolean {
    try {
      return localStorage.getItem(STORAGE_KEY) !== null;
    } catch {
      return false;
    }
  }

  clear(): void {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }
}
