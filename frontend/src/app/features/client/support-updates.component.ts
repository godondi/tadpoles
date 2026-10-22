import { Component, DestroyRef, inject, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, merge, take, takeUntil, timer } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { SupportApiService, CaseUpdate } from '../../core/api/support-api.service';
@Component({
  selector: 'app-support-updates',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <section class="panel" aria-label="Case updates">
      <h2>Case updates · {{ unread() }} unread</h2>
      <p>Updates are in-app only. Loading them does not mark them read.</p>
      @if (lastSuccess()) {
        <p>Last successful check {{ lastSuccess() | date: 'medium' : 'UTC' }} UTC.</p>
      }
      @if (message()) {
        <p class="notice" role="status">{{ message() }}</p>
      }
      @for (item of items(); track item.id) {
        <p>
          <a [routerLink]="['/app/support', item.reference]"
            >{{ item.reference }} · {{ item.subject }}</a
          >
          · {{ item.kind }} · {{ item.status }}
        </p>
      }
      <div class="actions">
        <button type="button" (click)="markRead()" [disabled]="busy() || !cursor">
          Mark displayed updates read
        </button>
        @if (hasMore()) {
          <button type="button" (click)="next()" [disabled]="busy()">Next updates</button>
        }
        <button type="button" (click)="resume()" [disabled]="busy()">
          Refresh and resume updates
        </button>
      </div>
    </section>
  `,
})
export class SupportUpdatesComponent {
  readonly changed = output<void>();
  private api = inject(SupportApiService);
  private auth = inject(AuthService);
  private destroy = inject(DestroyRef);
  private poll?: Subscription;
  readonly items = signal<CaseUpdate[]>([]);
  readonly unread = signal(0);
  readonly hasMore = signal(false);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly lastSuccess = signal<string | null>(null);
  cursor = 0;
  constructor() {
    this.resume();
    merge(this.auth.sessionChanged, this.api.denied)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        this.poll?.unsubscribe();
        this.items.set([]);
        this.unread.set(0);
        this.cursor = 0;
        this.busy.set(false);
        this.message.set('');
        this.lastSuccess.set(null);
        this.hasMore.set(false);
      });
  }
  resume() {
    this.poll?.unsubscribe();
    this.load();
    this.poll = timer(15000, 15000)
      .pipe(take(200), takeUntilDestroyed(this.destroy), takeUntil(this.auth.sessionChanged))
      .subscribe({
        next: () => {
          if (!this.hasMore()) this.load();
        },
        complete: () =>
          this.message.set('Automatic checks paused. Refresh and resume updates to continue.'),
      });
  }
  next() {
    this.items.set([]);
    this.hasMore.set(false);
    this.load();
  }
  load() {
    if (this.busy()) return;
    this.busy.set(true);
    this.api
      .updates(this.cursor)
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.auth.sessionChanged))
      .subscribe({
        next: (batch) => {
          const previous = this.cursor;
          const current = batch.reset ? [] : this.items();
          const unique = new Map(current.map((item) => [item.id, item]));
          for (const item of batch.items) unique.set(item.id, item);
          const rows = [...unique.values()].sort((a, b) => a.id - b.id);
          this.items.set(rows.slice(0, 100));
          this.cursor = this.items().at(-1)?.id ?? batch.cursor;
          this.hasMore.set(batch.hasMore || rows.length > 100);
          this.unread.set(batch.unread);
          this.busy.set(false);
          this.message.set('');
          this.lastSuccess.set(new Date().toISOString());
          if (previous > 0 && (this.cursor > previous || batch.reset)) this.changed.emit();
        },
        error: (error) => {
          this.busy.set(false);
          if ([401, 403].includes(error.status)) {
            this.poll?.unsubscribe();
            this.items.set([]);
            this.unread.set(0);
            this.cursor = 0;
            this.lastSuccess.set(null);
            this.message.set('Case update access is unavailable.');
          } else
            this.message.set(
              'Unable to check updates. Displayed activity may be stale; refresh and resume.',
            );
        },
      });
  }
  markRead() {
    if (!this.cursor || this.busy()) return;
    this.busy.set(true);
    this.api
      .markRead(this.cursor)
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.auth.sessionChanged))
      .subscribe({
        next: (result) => {
          this.unread.set(result.unread);
          this.busy.set(false);
        },
        error: () => {
          this.busy.set(false);
          this.message.set('Read acknowledgement unconfirmed. Retry the same acknowledgement.');
        },
      });
  }
}
