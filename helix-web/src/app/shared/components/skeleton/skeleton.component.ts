import { Component, Input } from '@angular/core';

@Component({
  selector: 'helix-skeleton',
  standalone: true,
  template: `<span class="helix-skeleton" [style.width]="width" [style.height]="height" [style.borderRadius]="radius"></span>`,
  styles: [
    `
      .helix-skeleton {
        display: block;
        background: linear-gradient(
          100deg,
          var(--helix-surface-sunken) 30%,
          var(--helix-border) 50%,
          var(--helix-surface-sunken) 70%
        );
        background-size: 200% 100%;
        animation: helix-shimmer 1.4s ease-in-out infinite;
      }
      @keyframes helix-shimmer {
        0% {
          background-position: 200% 0;
        }
        100% {
          background-position: -200% 0;
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .helix-skeleton {
          animation: none;
        }
      }
    `,
  ],
})
export class SkeletonComponent {
  @Input() width = '100%';
  @Input() height = '16px';
  @Input() radius = 'var(--radius-sm)';
}
