import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, RouterLink],
  template: `
    <div class="not-found">
      <mat-icon class="not-found__icon">search_off</mat-icon>
      <h1 class="not-found__title">404 — Page not found</h1>
      <p class="not-found__message">The page you're looking for doesn't exist or may have moved.</p>
      <a mat-flat-button color="primary" routerLink="/dashboard">Back to dashboard</a>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
        background: var(--helix-surface-alt);
      }
      .not-found {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: var(--space-3);
        text-align: center;
        padding: var(--space-6);
      }
      .not-found__icon {
        width: 56px;
        height: 56px;
        font-size: 56px;
        color: var(--helix-text-subtle);
      }
      .not-found__title {
        font: var(--text-h1);
        color: var(--helix-text);
      }
      .not-found__message {
        font: var(--text-body-lg);
        color: var(--helix-text-muted);
        margin-bottom: var(--space-4);
      }
    `,
  ],
})
export class NotFoundComponent {}
