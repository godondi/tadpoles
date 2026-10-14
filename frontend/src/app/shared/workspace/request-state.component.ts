import { Component, input, output } from '@angular/core';
import { LoadingComponent } from '../loading/loading.component';
@Component({
  selector: 'app-request-state',
  imports: [LoadingComponent],
  template: `@if (loading()) {
      <app-loading label="Loading…" />
    }
    @if (error()) {
      <section class="notice" role="alert">
        <h2>{{ status() === 503 ? 'Not available yet' : 'Unable to show this page' }}</h2>
        <p>{{ error() }}</p>
        <button type="button" (click)="retry.emit()">Try again</button>
      </section>
    }`,
})
export class RequestStateComponent {
  loading = input(false);
  error = input('');
  status = input(0);
  retry = output<void>();
}
