import { Component, input } from '@angular/core';

@Component({
  selector: 'app-loading',
  template: `<div role="status" aria-live="polite">
    <span class="spinner" aria-hidden="true"></span><span>{{ label() }}</span>
  </div>`,
  styles: `
    :host {
      display: block;
    }
    div {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 32px;
      color: var(--text-secondary);
    }
    .spinner {
      width: 24px;
      height: 24px;
      border: 3px solid var(--border-gray);
      border-top-color: var(--dark-green);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .spinner {
        animation: none;
      }
    }
  `,
})
export class LoadingComponent {
  readonly label = input('Loading…');
}
