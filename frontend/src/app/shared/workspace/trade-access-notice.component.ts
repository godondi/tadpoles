import { Component, DestroyRef, PLATFORM_ID, inject, signal } from '@angular/core';
import { DatePipe, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { buildApiUrl } from '../../core/api/api-url';
import { startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-trade-access-notice',
  imports: [DatePipe],
  template: `@if (revoked()) {
    <p class="trade-access-notice" role="status">
      Trade access revoked. Contact support for additional details{{
        until()
          ? ' Access is scheduled to return ' + (until() | date: 'longDate') + '.'
          : ' This restriction is indefinite.'
      }}
    </p>
  }`,
  styles: [
    `
      .trade-access-notice {
        margin: 0;
        padding: 12px 22px;
        background: var(--surface-muted);
        color: var(--text-primary);
        border-bottom: 1px solid var(--border-gray);
        font-weight: 700;
      }
    `,
  ],
})
export class TradeAccessNoticeComponent {
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);
  readonly revoked = signal(false);
  readonly until = signal<string | null>(null);
  constructor() {
    if (isPlatformBrowser(this.platform)) {
      timer(30_000, 30_000)
        .pipe(
          startWith(0),
          switchMap(() =>
            this.http.get<{ revoked: boolean; revokedUntil: string }>(
              buildApiUrl('/api/workspace/client/trade-access', this.platform),
            ),
          ),
          takeUntilDestroyed(inject(DestroyRef)),
        )
        .subscribe({
          next: (s) => {
            this.revoked.set(s.revoked);
            this.until.set(s.revokedUntil || null);
          },
          error: () => {
            this.revoked.set(false);
            this.until.set(null);
          },
        });
    }
  }
}
