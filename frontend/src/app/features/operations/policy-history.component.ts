import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { InstrumentPolicyApi } from '../../core/api/instrument-policy-api.service';
import { EvidencePage } from '../../core/api/operations-api.service';
import { EvidenceTableComponent } from './evidence-table.component';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-policy-history',
  imports: [EvidenceTableComponent],
  template: `
    <section class="panel">
      <h2>Policy change history</h2>
      <p>
        Changes apply immediately. System setup and user changes are identified separately. A full
        halt prevents both entries and exits; active sell-only changes still require provider
        validation.
      </p>
      <button type="button" (click)="load(0)" [disabled]="loading()">Refresh history</button>
      @if (message()) {
        <p role="alert">{{ message() }}</p>
      }
      @if (data(); as history) {
        <app-evidence-table [rows]="history.items" caption="Recorded policy changes" />
        <div class="actions">
          <button type="button" (click)="load(history.page - 1)" [disabled]="history.page === 0">
            Previous</button
          ><button type="button" (click)="load(history.page + 1)" [disabled]="!history.hasMore">
            Next
          </button>
        </div>
      }
    </section>
  `,
})
export class PolicyHistoryComponent {
  readonly instrumentId = input.required<number>();
  private readonly api = inject(InstrumentPolicyApi);
  private readonly destroy = inject(DestroyRef);
  readonly data = signal<EvidencePage | null>(null);
  readonly loading = signal(false);
  readonly message = signal('');
  private request?: Subscription;
  constructor() {
    effect(() => {
      this.instrumentId();
      this.load(0);
    });
    inject(AuthService)
      .sessionChanged.pipe(takeUntilDestroyed())
      .subscribe(() => {
        this.request?.unsubscribe();
        this.data.set(null);
      });
    this.destroy.onDestroy(() => this.request?.unsubscribe());
  }
  load(page: number) {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.message.set('');
    this.request = this.api.history(this.instrumentId(), page).subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.data.set(null);
        this.loading.set(false);
        this.message.set('Policy history is unavailable.');
      },
    });
  }
}
