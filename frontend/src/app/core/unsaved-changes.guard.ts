import { CanDeactivateFn } from '@angular/router';

/** A page that can hold work the pharmacist has not confirmed yet. */
export interface HasUnsavedWork {
  /** Resolves true if it is fine to leave (nothing to lose, or the user agreed to discard it). */
  confirmLeave(): Promise<boolean>;
}

export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedWork> = (page) => page.confirmLeave();
