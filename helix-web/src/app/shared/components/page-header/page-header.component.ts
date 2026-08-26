import { Component, Input } from '@angular/core';

@Component({
  selector: 'helix-page-header',
  standalone: true,
  template: `
    <div class="page-header">
      <div class="page-header__text">
        <h1 class="page-header__title">{{ title }}</h1>
        @if (subtitle) {
          <p class="page-header__subtitle">{{ subtitle }}</p>
        }
      </div>
      <div class="page-header__actions">
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styles: [
    `
      .page-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--space-4);
        flex-wrap: wrap;
      }
      .page-header__title {
        font: var(--text-h1);
        color: var(--helix-text);
      }
      .page-header__subtitle {
        margin-top: var(--space-1);
        font: var(--text-body);
        color: var(--helix-text-muted);
      }
      .page-header__actions {
        display: flex;
        gap: var(--space-2);
        align-items: center;
      }
    `,
  ],
})
export class PageHeaderComponent {
  @Input() title = '';
  @Input() subtitle = '';
}
