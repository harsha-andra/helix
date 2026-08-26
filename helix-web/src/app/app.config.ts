import { ApplicationConfig, isDevMode, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { demoApiInterceptor } from './core/interceptors/demo-api.interceptor';
import { claimsFeature } from './store/claims/claims.reducer';
import { claimantsFeature } from './store/claimants/claimants.reducer';
import { wizardFeature } from './store/wizard/wizard.reducer';
import { ClaimsEffects } from './store/claims/claims.effects';
import { ClaimantsEffects } from './store/claimants/claimants.effects';
import { WizardEffects } from './store/wizard/wizard.effects';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideAnimationsAsync(),
    provideNativeDateAdapter(),
    provideHttpClient(withInterceptors([authInterceptor, demoApiInterceptor])),

    // Feature stores are registered once at the root so their state survives navigation —
    // e.g. the wizard slice keeps every step's progress even if the user leaves /claims/new
    // and comes back later.
    provideStore({
      [claimsFeature.name]: claimsFeature.reducer,
      [claimantsFeature.name]: claimantsFeature.reducer,
      [wizardFeature.name]: wizardFeature.reducer,
    }),
    provideEffects([ClaimsEffects, ClaimantsEffects, WizardEffects]),
    provideStoreDevtools({ maxAge: 25, logOnly: !isDevMode(), connectInZone: true }),
  ],
};
