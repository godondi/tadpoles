import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, takeUntil } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  SupportApiService,
  SupportCase,
  CaseDetail,
  CASE_CATEGORIES,
  NewCase,
} from '../../core/api/support-api.service';
import { WorkspaceApi, OrderDetail, Page } from '../../core/api/workspace-api.service';
import { SupportUpdatesComponent } from './support-updates.component';
import { OperationsContextComponent } from '../operations/operations-context.component';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';
import { PageResource } from '../../shared/workspace/page-resource';

@Component({
  selector: 'app-support-page',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, OperationsContextComponent, SupportUpdatesComponent],
  template: `
    <header class="workspace-heading">
      <div>
        <h1>{{ staff ? 'Support cases' : 'Support' }}</h1>
        <p>
          Case progress is separate from order status. Submission does not promise active review.
        </p>
      </div>
      @if (!staff && !isNew) {
        <a class="button-link" routerLink="/app/support/new">Report an issue</a>
      }
    </header>
    @if (isNew) {
      <section class="panel">
        <h2>Report an issue</h2>
        <p>
          Do not include passwords, tokens, full bank details or identity documents. Only describe
          the issue.
        </p>
        @if (orderId) {
          @if (order.data(); as data) {
            <p>
              Order {{ data.order.orderId }} · {{ data.order.ticker }} · {{ data.order.status }}
            </p>
          } @else {
            <p role="status">{{ order.error() || 'Loading owned order…' }}</p>
          }
        }
        @if (receipt(); as saved) {
          <div class="notice" role="status">
            Submitted. Your case reference is <strong>{{ saved.reference }}</strong
            >. <a [routerLink]="['/app/support', saved.reference]">View case</a>
          </div>
        } @else {
          <form class="workspace-form" (ngSubmit)="submit()">
            <label
              >Category
              <select name="category" [(ngModel)]="category" [disabled]="busy() || uncertain()">
                @for (item of categories; track item) {
                  <option [value]="item">{{ label(item) }}</option>
                }
              </select></label
            >
            <label
              >Subject
              <input
                name="subject"
                [(ngModel)]="subject"
                minlength="5"
                maxlength="120"
                required
                [readonly]="busy() || uncertain()"
            /></label>
            <label
              >Description
              <textarea
                name="description"
                [(ngModel)]="description"
                minlength="10"
                maxlength="4000"
                required
                rows="7"
                [readonly]="busy() || uncertain()"
              ></textarea>
            </label>
            <button type="submit" [disabled]="busy() || (!!orderId && !order.data())">
              {{ uncertain() ? 'Retry with same request key' : 'Submit case' }}
            </button>
            @if (requestKey) {
              <button type="button" (click)="recover()" [disabled]="busy()">
                Recover submission
              </button>
            }
          </form>
        }
        @if (notice()) {
          <p class="notice" role="status">{{ notice() }}</p>
        }
      </section>
    } @else if (reference) {
      <nav class="actions" aria-label="Case navigation">
        <a class="button-link" [routerLink]="base">Back to cases</a>
        <button type="button" (click)="load()" [disabled]="detail.loading()">
          Refresh conversation
        </button>
      </nav>
      <p>Refresh to check for replies. Public updates also appear in Support.</p>
      @if (detail.loading()) {
        <p role="status">Loading case…</p>
      }
      @if (detail.error()) {
        <p class="notice" role="alert">{{ detail.error() }}</p>
        <button (click)="load()">Retry</button>
      }
      @if (detail.data(); as item) {
        <section class="panel">
          <h2>{{ item.subject }}</h2>
          <p>
            <strong>{{ item.reference }}</strong> · {{ label(item.status) }} ·
            {{ label(item.category) }}
          </p>
          <p>
            Created {{ item.createdAt | date: 'medium' : 'UTC' }} UTC · Public update
            {{ item.updatedAt | date: 'medium' : 'UTC' }} UTC
          </p>
          @if (item.orderId) {
            <a [routerLink]="[staff ? '/operations/orders' : '/app/orders', item.orderId]"
              >Linked order {{ item.orderId }}</a
            >
          }
          @if (staff) {
            <app-operations-context [managedReference]="item.reference" />
          }
          <h3>Public conversation</h3>
          @for (event of item.events; track event.version) {
            <article class="panel">
              <p>
                {{ label(event.authorRole) }} · {{ event.recordedAt | date: 'medium' : 'UTC' }} UTC
                · {{ label(event.status) }}
              </p>
              <p class="case-prose">{{ event.body }}</p>
            </article>
          }
          <div class="actions">
            <button (click)="changePage(-1)" [disabled]="page === 0">Previous activity</button
            ><span>Page {{ page + 1 }}</span
            ><button (click)="changePage(1)" [disabled]="(page + 1) * 20 >= item.totalEvents">
              Next activity
            </button>
          </div>
          <div class="form-field">
            <label
              >Public message
              <textarea
                name="reply"
                [(ngModel)]="reply"
                minlength="10"
                maxlength="4000"
                rows="5"
                [readonly]="writeBusy()"
              ></textarea>
            </label>
          </div>
          @if (item.status !== 'RESOLVED') {
            <button type="button" (click)="send('replies')" [disabled]="writeBusy()">
              Send public reply
            </button>
          } @else if (!staff) {
            <button type="button" (click)="send('reopen')" [disabled]="writeBusy()">
              Reopen with public reason
            </button>
          }
          @if (staff && item.status !== 'RESOLVED') {
            <label
              >New public status
              <select name="nextStatus" [(ngModel)]="nextStatus">
                <option value="IN_REVIEW">In review</option>
                <option value="AWAITING_CLIENT">Awaiting client</option>
                <option value="RESOLVED">Resolved</option>
              </select></label
            >
            <button type="button" (click)="send('transitions')" [disabled]="writeBusy()">
              Publish status and explanation
            </button>
          }
          @if (staff) {
            <h3>Internal notes — staff only</h3>
            <p>These notes are never included in the public conversation.</p>
            <button type="button" (click)="loadNotes()">Load internal notes</button>
            @if (notes.error()) {
              <p role="alert">{{ notes.error() }}</p>
            }
            @if (notes.data(); as result) {
              @for (note of result.items; track note.id) {
                <article>
                  <p>Staff {{ note.actor }} · {{ note.recordedAt | date: 'medium' : 'UTC' }} UTC</p>
                  <p class="case-prose">{{ note.body }}</p>
                </article>
              }
              <button (click)="notePage = notePage - 1; loadNotes()" [disabled]="notePage === 0">
                Previous notes</button
              ><button
                (click)="notePage = notePage + 1; loadNotes()"
                [disabled]="(notePage + 1) * 20 >= result.total"
              >
                Next notes
              </button>
            }
            <label
              >Internal note
              <textarea
                name="internalNote"
                [(ngModel)]="internalNote"
                minlength="10"
                maxlength="4000"
                rows="4"
                [readonly]="writeBusy()"
              ></textarea>
            </label>
            <button type="button" (click)="send('notes')" [disabled]="writeBusy()">
              Save internal note
            </button>
          }
          @if (writePending()) {
            <button type="button" (click)="recoverWrite()" [disabled]="writeBusy()">
              Recover pending activity
            </button>
          }
          @if (writeNotice()) {
            <p class="notice" role="status">{{ writeNotice() }}</p>
          }
        </section>
      }
    } @else {
      @if (!staff) {
        <app-support-updates (changed)="load()" />
      }
      @if (staff) {
        <p>
          All report categories within your current account grants appear here automatically.
          Filters are optional. Case access does not grant evidence access.
        </p>
      }
      <div class="actions">
        <button type="button" (click)="load()" [disabled]="list.loading()">Refresh cases</button>
      </div>
      <form class="filters" (ngSubmit)="page = 0; load()">
        <label
          >Reference or subject <input name="search" [(ngModel)]="search" maxlength="120" /></label
        ><label
          >Status
          <select name="status" [(ngModel)]="status">
            <option value="">All statuses</option>
            @for (item of states; track item) {
              <option [value]="item">{{ label(item) }}</option>
            }
          </select></label
        ><button>Search</button>
      </form>
      @if (list.loading()) {
        <p role="status">Loading cases…</p>
      }
      @if (list.error()) {
        <p class="notice" role="alert">{{ list.error() }}</p>
        <button (click)="load()">Retry</button>
      }
      @if (list.data(); as result) {
        <section class="panel">
          @if (!result.total) {
            <p>No matching cases.</p>
          } @else {
            <div class="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Reference and subject</th>
                    <th>Category</th>
                    <th>Status</th>
                    <th>Public update (UTC)</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of result.items; track item.reference) {
                    <tr>
                      <td>
                        <a [routerLink]="[base, item.reference]">{{ item.reference }}</a
                        ><br />{{ item.subject }}
                      </td>
                      <td>{{ label(item.category) }}</td>
                      <td>{{ label(item.status) }}</td>
                      <td>{{ item.updatedAt | date: 'medium' : 'UTC' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <div class="actions">
              <button (click)="changePage(-1)" [disabled]="page === 0">Previous cases</button
              ><span>{{ result.total }} cases · Page {{ page + 1 }}</span
              ><button (click)="changePage(1)" [disabled]="(page + 1) * 20 >= result.total">
                Next cases
              </button>
            </div>
          }
        </section>
      }
    }
  `,
  styles: [
    `
      .case-prose {
        white-space: pre-wrap;
        overflow-wrap: anywhere;
      }
    `,
  ],
})
export class SupportPageComponent {
  private api = inject(SupportApiService);
  private workspace = inject(WorkspaceApi);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private destroy = inject(DestroyRef);
  readonly staff = !!this.route.snapshot.data['staff'];
  readonly isNew = !!this.route.snapshot.data['new'];
  readonly base = this.staff ? '/operations/cases' : '/app/support';
  readonly list = new PageResource<Page<SupportCase>>();
  readonly detail = new PageResource<CaseDetail>();
  readonly order = new PageResource<OrderDetail>();
  readonly categories = CASE_CATEGORIES;
  readonly states = ['OPEN', 'IN_REVIEW', 'AWAITING_CLIENT', 'RESOLVED'];
  readonly receipt = signal<SupportCase | null>(null);
  readonly notice = signal('');
  readonly busy = signal(false);
  readonly uncertain = signal(false);
  readonly notes = new PageResource<
    Page<{ id: string; actor: number; body: string; recordedAt: string }>
  >();
  readonly writeBusy = signal(false);
  readonly writeNotice = signal('');
  reply = '';
  internalNote = '';
  nextStatus = 'IN_REVIEW';
  notePage = 0;
  readonly writePending = signal(false);
  private routeChanged = new Subject<void>();
  private pendingWrite?: {
    key: string;
    action: string;
    body: { expectedVersion: number; body: string; status?: string };
  };
  reference = '';
  orderId?: number;
  category = 'OTHER';
  subject = '';
  description = '';
  search = '';
  status = '';
  page = 0;
  requestKey = '';
  private storageKey = '';
  private submitted?: NewCase;
  constructor() {
    if (this.staff && !this.isNew) {
      inject(CatalogRefreshService)
        .watch()
        .pipe(takeUntilDestroyed())
        .subscribe(() => {
          if (!this.reference && !this.list.loading())
            this.list.load(this.api.list(true, this.status, this.search, this.page), true);
        });
    }
    this.api.denied.pipe(takeUntilDestroyed()).subscribe(() => {
      this.busy.set(false);
      this.writeBusy.set(false);
      this.list.data.set(null);
      this.detail.data.set(null);
      this.notes.data.set(null);
      this.order.data.set(null);
      this.receipt.set(null);
      this.subject = '';
      this.description = '';
      this.reply = '';
      this.internalNote = '';
      this.submitted = undefined;
      this.pendingWrite = undefined;
      this.writePending.set(false);
    });
    this.auth.sessionChanged.pipe(takeUntilDestroyed()).subscribe(() => {
      this.receipt.set(null);
      this.subject = '';
      this.description = '';
      this.submitted = undefined;
      this.notice.set('');
      this.busy.set(false);
      this.requestKey = '';
      this.storageKey = '';
      this.reply = '';
      this.internalNote = '';
      this.pendingWrite = undefined;
      this.writePending.set(false);
      this.writeBusy.set(false);
      this.writeNotice.set('');
    });
    if (this.isNew) {
      const raw = this.route.snapshot.queryParamMap.get('orderId');
      if (raw && /^[1-9]\d*$/.test(raw) && Number.isSafeInteger(Number(raw))) {
        this.orderId = Number(raw);
        this.order.load(this.workspace.order(String(this.orderId)));
        this.category = 'ORDER_QUESTION';
      }
      const category = this.route.snapshot.queryParamMap.get('category');
      if (this.categories.some((c) => c === category)) this.category = category!;
      const owner = this.auth.identity()?.userId;
      if (owner) {
        this.storageKey = 'support.pending.create.' + owner;
        try {
          const key = sessionStorage.getItem(this.storageKey);
          if (key && /^[A-Za-z0-9_-]{16,128}$/.test(key)) {
            this.requestKey = key;
            this.recover();
          }
        } catch {
          this.notice.set(
            'Browser recovery storage is unavailable. Keep this page open until the outcome is confirmed.',
          );
        }
      }
    } else {
      this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
        this.routeChanged.next();
        this.reference = params.get('reference') ?? '';
        this.page = 0;
        this.reply = '';
        this.internalNote = '';
        this.pendingWrite = undefined;
        this.writeBusy.set(false);
        this.writeNotice.set('');
        this.load();
        this.restoreWrite();
      });
    }
  }
  label(value: string) {
    return value.replaceAll('_', ' ').toLowerCase();
  }
  load() {
    if (this.reference) this.detail.load(this.api.detail(this.staff, this.reference, this.page));
    else this.list.load(this.api.list(this.staff, this.status, this.search, this.page));
  }
  changePage(delta: number) {
    this.page = Math.max(0, this.page + delta);
    this.load();
  }
  private remember() {
    try {
      if (!this.storageKey) return false;
      sessionStorage.setItem(this.storageKey, this.requestKey);
      return sessionStorage.getItem(this.storageKey) === this.requestKey;
    } catch {
      this.notice.set('Enable session storage to preserve the request key before submitting.');
      return false;
    }
  }
  private committed(value: SupportCase) {
    this.receipt.set(value);
    this.uncertain.set(false);
    this.busy.set(false);
    this.notice.set('');
    try {
      if (this.storageKey) sessionStorage.removeItem(this.storageKey);
    } catch {
      /* The server remains authoritative. */
    }
  }
  submit() {
    if (this.busy() || (this.orderId && !this.order.data())) return;
    const body = this.submitted ?? {
      category: this.category,
      subject: this.subject.trim(),
      description: this.description.trim(),
      ...(this.orderId ? { orderId: this.orderId } : {}),
    };
    if (
      body.subject.length < 5 ||
      body.subject.length > 120 ||
      body.description.length < 10 ||
      body.description.length > 4000 ||
      /[\x00-\x1f\x7f-\x9f]/.test(this.submitted?.subject ?? this.subject) ||
      /[\x00-\x08\x0b-\x1f\x7f-\x9f]/.test(this.submitted?.description ?? this.description)
    ) {
      this.notice.set(
        'Use a subject of 5–120 characters and description of 10–4000 characters, without control characters.',
      );
      return;
    }
    this.requestKey ||= crypto.randomUUID();
    if (!this.remember()) return;
    this.submitted = body;
    this.busy.set(true);
    this.api
      .create(this.requestKey, body)
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.auth.sessionChanged))
      .subscribe({
        next: (value) => this.committed(value),
        error: (error) => {
          this.busy.set(false);
          if (error.status === 400) {
            this.submitted = undefined;
            this.uncertain.set(false);
            this.notice.set(
              'Check the category, subject and description limits. Correct the input using the same request key.',
            );
            return;
          }
          if ([401, 403].includes(error.status)) {
            this.subject = '';
            this.description = '';
            this.submitted = undefined;
            this.order.data.set(null);
          }
          this.uncertain.set(true);
          this.notice.set(
            error.status === 409
              ? 'This request key belongs to different content. Recover the original submission.'
              : 'Outcome unconfirmed. Recover or retry with the same request key.',
          );
        },
      });
  }
  recover() {
    if (!this.requestKey || this.busy()) return;
    this.busy.set(true);
    this.api
      .recover(this.requestKey)
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.auth.sessionChanged))
      .subscribe({
        next: (value) => this.committed(value),
        error: (error) => {
          this.busy.set(false);
          this.notice.set(
            error.status === 404
              ? 'No committed case found yet. Re-enter the original content if needed and retry with the same request key.'
              : 'Recovery unavailable. Keep the same request key and try again.',
          );
        },
      });
  }

  loadNotes() {
    this.notes.load(this.api.notes(this.reference, this.notePage));
  }
  send(action: string) {
    const current = this.detail.data();
    if (!current || this.writeBusy()) return;
    if (this.pendingWrite && this.pendingWrite.action !== action) {
      this.writeNotice.set('Recover the pending action before starting another action.');
      return;
    }
    const body = this.pendingWrite?.body.body
      ? this.pendingWrite.body
      : {
          expectedVersion: this.pendingWrite?.body.expectedVersion ?? current.version,
          body: (action === 'notes' ? this.internalNote : this.reply).trim(),
          ...(action === 'transitions' ? { status: this.nextStatus } : {}),
        };
    if (body.body.length < 10 || body.body.length > 4000) {
      this.writeNotice.set('Enter 10–4000 characters.');
      return;
    }
    this.pendingWrite ??= { key: crypto.randomUUID(), action, body };
    this.pendingWrite.body = body;
    if (!this.rememberWrite()) return;
    this.writeBusy.set(true);
    this.api
      .write(this.staff, this.reference, action, this.pendingWrite.key, this.pendingWrite.body)
      .pipe(
        takeUntilDestroyed(this.destroy),
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.routeChanged),
      )
      .subscribe({
        next: () => {
          this.clearWrite();
          this.writeBusy.set(false);
          this.writeNotice.set('Activity saved.');
          if (action === 'notes') {
            this.internalNote = '';
            this.loadNotes();
          } else {
            this.reply = '';
            this.load();
          }
        },
        error: (error) => {
          this.writeBusy.set(false);
          if (error.status === 401 || error.status === 403) {
            this.detail.data.set(null);
            this.notes.data.set(null);
            this.reply = '';
            this.internalNote = '';
          }
          const rejectedTransition =
            error.status === 409 &&
            [
              'Invalid case transition',
              'Only resolved cases can be reopened',
              'Reopen the resolved case before replying',
            ].includes(error.error?.message);
          if (error.status === 400 || rejectedTransition) {
            this.clearWrite();
            this.writeNotice.set(
              'Activity was not accepted. Review the message and current status, then submit a corrected action.',
            );
            if (rejectedTransition) this.load();
          } else if (error.status === 409 && error.error?.message?.includes('Case changed')) {
            this.clearWrite();
            this.load();
            this.writeNotice.set('Case changed. Review the new activity before submitting again.');
          } else
            this.writeNotice.set(
              'Outcome unconfirmed. Repeat the same action to retry with the same request key.',
            );
        },
      });
  }

  private actionStorageKey() {
    const owner = this.auth.identity()?.userId;
    return owner && this.reference ? 'support.pending.action.' + owner + '.' + this.reference : '';
  }
  private rememberWrite() {
    const pending = this.pendingWrite;
    if (!pending) return false;
    this.writePending.set(true);
    try {
      const key = this.actionStorageKey();
      if (!key) return false;
      sessionStorage.setItem(
        key,
        JSON.stringify({
          key: pending.key,
          action: pending.action,
          version: pending.body.expectedVersion,
          status: pending.body.status,
        }),
      );
      return true;
    } catch {
      this.writeNotice.set('Enable session storage to preserve the request key before submitting.');
      return false;
    }
  }
  private clearWrite() {
    try {
      const key = this.actionStorageKey();
      if (key) sessionStorage.removeItem(key);
    } catch {}
    this.pendingWrite = undefined;
    this.writePending.set(false);
  }
  private restoreWrite() {
    this.writePending.set(false);
    try {
      const key = this.actionStorageKey();
      const raw = key ? sessionStorage.getItem(key) : null;
      if (!raw) return;
      const value = JSON.parse(raw);
      if (
        !/^[A-Za-z0-9_-]{16,128}$/.test(value.key) ||
        !['replies', 'notes', 'transitions', 'reopen'].includes(value.action) ||
        !Number.isSafeInteger(value.version) ||
        value.version < 1
      )
        return;
      this.pendingWrite = {
        key: value.key,
        action: value.action,
        body: {
          expectedVersion: value.version,
          body: '',
          ...(value.status ? { status: value.status } : {}),
        },
      };
      this.nextStatus = value.status ?? 'IN_REVIEW';
      this.writePending.set(true);
      this.recoverWrite();
    } catch {}
  }
  recoverWrite() {
    const pending = this.pendingWrite;
    if (!pending || this.writeBusy()) return;
    this.writeBusy.set(true);
    this.api
      .recoverWrite(this.staff, this.reference, pending.key)
      .pipe(
        takeUntilDestroyed(this.destroy),
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.routeChanged),
      )
      .subscribe({
        next: () => {
          this.clearWrite();
          this.writeBusy.set(false);
          this.writeNotice.set('Recovered committed activity.');
          this.load();
        },
        error: (error) => {
          this.writeBusy.set(false);
          this.writeNotice.set(
            error.status === 404
              ? 'No committed activity found yet. Re-enter the original message if needed and repeat the same action with the same request key.'
              : 'Recovery unavailable. Keep the same request key.',
          );
        },
      });
  }
}
