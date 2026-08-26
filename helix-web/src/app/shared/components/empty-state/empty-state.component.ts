import { Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Shared visual for "nothing here" and "something went wrong" states — pass `variant`
 * to switch tone. Used instead of ad-hoc empty markup so every list/table looks consistent. */
@Component({
  selector: 'helix-empty-state',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <div class="empty-state" [class.empty-state--error]="variant === 'error'">
      <mat-icon class="empty-state__icon">{{ icon }}</mat-icon>
      <h3 class="empty-state__title">{{ title }}</h3>
      @if (message) {
        <p class="empty-state__message">{{ message }}</p>
      }
      <div class="empty-state__actions">
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styles: [
    `
      .empty-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: var(--space-2);
        padding: var(--space-12) var(--space-6);
        color: var(--helix-text-muted);
      }
      .empty-state__icon {
        width: 40px;
        height: 40px;
        font-size: 40px;
        color: var(--helix-text-subtle);
        margin-bottom: var(--space-2);
      }
      .empty-state--error .empty-state__icon {
        color: var(--helix-danger);
      }
      .empty-state__title {
        font: var(--text-h3);
        color: var(--helix-text);
      }
      .empty-state__message {
        font: var(--text-body);
        max-width: 44ch;
      }
      .empty-state__actions:empty {
        display: none;
      }
      .empty-state__actions {
        margin-top: var(--space-2);
      }
    `,
  ],
})
export class EmptyStateComponent {
  @Input() icon = 'inbox';
  @Input() title = 'Nothing here yet';
  @Input() message = '';
  @Input() variant: 'empty' | 'error' = 'empty';
}
