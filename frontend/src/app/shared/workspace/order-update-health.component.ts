import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { OrderStatusService } from '../../core/api/order-status.service';
@Component({
  selector: 'app-order-update-health',
  standalone: true,
  imports: [DatePipe],
  template: `
    <aside class="notice" aria-live="polite">
      <p>
        Order updates: {{ status.health().toLowerCase() }}.
        @if (status.lastSuccess(); as time) {
          Last successful check {{ time | date: 'medium' : 'UTC' }} UTC.
        }
        @if (status.health() === 'STALE') {
          The latest status could not be checked; displayed data may be stale.
        }
        @if (status.health() === 'EXHAUSTED') {
          Automatic checks paused after 10 minutes.
        }
      </p>
      @if (status.health() === 'STALE' || status.health() === 'EXHAUSTED') {
        <button type="button" (click)="status.resume()">Resume automatic updates</button>
      }
    </aside>
  `,
})
export class OrderUpdateHealthComponent {
  readonly status = inject(OrderStatusService);
}
