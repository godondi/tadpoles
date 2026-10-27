import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, takeUntil } from 'rxjs';
import { CaseWorkflowApi, ReviewCase } from '../../core/api/case-workflow-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { PrivateCommand } from '../../shared/workspace/private-command';
import { ComplianceContextComponent } from './compliance-context.component';
import { ComplianceApi } from './compliance-api.service';

@Component({
  imports: [DatePipe, FormsModule, RouterLink, ComplianceContextComponent],
  template: `
    <header class="workspace-heading">
      <div>
        <h1>Compliance review cases</h1>
        <p>
          Independent review assignments. Private findings never publish a client response or change
          a financial record.
        </p>
      </div>
      @if (reference) {
        <a class="button-link" routerLink="/compliance/cases">Back to review cases</a>
      }
      <button (click)="load()" [disabled]="busy()">Refresh review</button>
    </header>
    @if (message()) {
      <p class="notice" role="alert">{{ message() }}</p>
    }
    @if (pending()) {
      <p role="status">
        Original private command awaits confirmation. No replacement key will be generated.
      </p>
      <button (click)="recover()" [disabled]="busy()">Recover original command</button>
      @if (canRetry()) {
        <button (click)="retry()" [disabled]="busy()">Retry original command</button>
      }
    }
    @if (!reference) {
      <form class="filters" (ngSubmit)="page = 0; load()">
        <label
          >Review status<select name="status" [(ngModel)]="status">
            <option value="">All permitted reviews</option>
            @for (s of states; track s) {
              <option>{{ s }}</option>
            }
          </select></label
        ><button>Apply</button>
      </form>
      @if (!items().length) {
        <p>No review cases loaded.</p>
      }
      @for (item of items(); track item.reference) {
        <article class="panel">
          <h2>
            <a [routerLink]="['/compliance/cases', item.reference]">{{ item.reference }}</a>
          </h2>
          <p>{{ item.status }} / reviewer {{ item.reviewer }} / {{ item.reasonCode }}</p>
          <p class="case-prose">{{ item.summary }}</p>
          <p>Updated {{ item.updatedAt | date: 'medium' : 'UTC' }} UTC</p>
        </article>
      }
      <div class="actions">
        <button (click)="page = page - 1; load()" [disabled]="page === 0">Previous</button
        ><span>Page {{ page + 1 }}</span
        ><button (click)="page = page + 1; load()" [disabled]="(page + 1) * 20 >= total()">
          Next
        </button>
      </div>
    }
    @if (detail(); as item) {
      <section class="panel case-workbench">
        <h2>{{ item.reference }}</h2>
        <p>{{ item.status }} / private version {{ item.version }} / reviewer {{ item.reviewer }}</p>
        <p class="case-prose">{{ item.summary }}</p>
        <p>Disposition: {{ item.disposition ?? 'pending' }}</p>
        @if (item.findings) {
          <h3>Private findings</h3>
          <p class="case-prose">{{ item.findings }}</p>
        }
        @if (item.clientSafeExplanation) {
          <h3>Proposed explanation - not published</h3>
          <p class="case-prose">{{ item.clientSafeExplanation }}</p>
        }
        <app-compliance-context [managedReference]="item.reference" />
        @if (item.orderId) {
          <a class="button-link" [routerLink]="['/compliance/investigations', item.orderId]"
            >Review linked order evidence under the active context</a
          >
          <a class="button-link" routerLink="/compliance/evidence"
            >Review role-specific retained exports</a
          >
        } @else {
          <p>
            Account-only review: no order or single-order archive is fabricated. Review the approved
            source summary and request further evidence if necessary.
          </p>
        }
        @if (item.actions?.assign) {
          <form class="workspace-form" (ngSubmit)="assign()">
            <label
              >Independently eligible reviewer<select
                name="reviewer"
                [(ngModel)]="reviewer"
                required
              >
                @for (person of item.reviewers ?? []; track person.id) {
                  <option [ngValue]="person.id">{{ person.username }}</option>
                }
              </select></label
            >
            <label
              ><span>Assignment reason <small class="min-length-hint">(minimum 10 characters)</small></span
              ><textarea
                name="assignmentReason"
                [(ngModel)]="assignmentReason"
                minlength="10"
                maxlength="4000"
                required
              ></textarea>
            </label>
            <button [disabled]="locked()">Retain reviewer reassignment</button>
          </form>
          <form class="workspace-form" (ngSubmit)="transition()">
            <label
              >Allowed lifecycle action<select name="target" [(ngModel)]="target" required>
                <option value="">Choose action</option>
                @for (s of item.actions?.transitions; track s) {
                  <option>{{ s }}</option>
                }
              </select></label
            >
            <label
              ><span>Review / information-request / decision reason <small class="min-length-hint">(minimum 10 characters)</small></span
              ><textarea
                name="reason"
                [(ngModel)]="reason"
                minlength="10"
                maxlength="4000"
                required
              ></textarea>
            </label>
            @if (target === 'DECIDED') {
              <label
                ><span>Decision disposition <small class="min-length-hint">(minimum 3 characters)</small></span
                ><input
                  name="disposition"
                  [(ngModel)]="disposition"
                  minlength="3"
                  maxlength="100"
                  required
              /></label>
              <label
                >Optional client-safe proposal - Operations must explicitly publish<textarea
                  name="proposal"
                  [(ngModel)]="proposal"
                  maxlength="4000"
                ></textarea>
              </label>
            }
            <button [disabled]="locked() || !target">Retain private action</button>
          </form>
        }
        <details>
          <summary>Attributable review history</summary>
          @for (event of item.history ?? []; track event.version) {
            <article>
              <p>
                Version {{ event.version }} / actor {{ event.actor }} / {{ event.kind }} /
                {{ event.recordedAt | date: 'medium' : 'UTC' }} UTC
              </p>
              <p class="case-prose">{{ event.facts }}</p>
            </article>
          }
          <div class="actions">
            <button (click)="historyPage = historyPage - 1; load()" [disabled]="historyPage === 0">
              Newer history</button
            ><span>Page {{ historyPage + 1 }}</span
            ><button
              (click)="historyPage = historyPage + 1; load()"
              [disabled]="(item.history?.length ?? 0) < 20"
            >
              Older history
            </button>
          </div>
        </details>
      </section>
    }
  `,
})
export class ReviewCasesComponent {
  private readonly api = inject(CaseWorkflowApi);
  private readonly contexts = inject(ComplianceApi);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private readonly replaced = new Subject<void>();
  private generation = 0;
  private recovery?: PrivateCommand;
  private blocked = false;
  readonly items = signal<ReviewCase[]>([]);
  readonly detail = signal<ReviewCase | null>(null);
  readonly total = signal(0);
  readonly message = signal('');
  readonly busy = signal(false);
  readonly pending = signal(false);
  readonly canRetry = signal(false);
  readonly states = ['QUEUED', 'IN_REVIEW', 'NEEDS_INFORMATION', 'DECIDED', 'CLOSED'];
  reference = '';
  status = '';
  page = 0;
  historyPage = 0;
  reviewer = 0;
  assignmentReason = '';
  target = '';
  reason = '';
  disposition = '';
  proposal = '';
  constructor() {
    this.api.denied.pipe(takeUntilDestroyed()).subscribe(() => this.clear());
    this.auth.sessionChanged.pipe(takeUntilDestroyed()).subscribe(() => this.clear());
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.clear();
      this.reference = params.get('reference') ?? '';
      const context = this.contexts.context();
      if (this.reference && context && context.caseReference !== this.reference)
        this.contexts.clear('Review changed. Start the matching managed purpose context.');
      this.historyPage = 0;
      this.blocked = false;
      if (this.reference)
        try {
          this.recovery = new PrivateCommand(this.auth.identity()?.userId ?? 0, this.reference);
          this.pending.set(!!this.recovery.pending);
        } catch (error) {
          this.blocked = true;
          this.message.set(
            error instanceof Error ? error.message : 'Recovery storage unavailable.',
          );
        }
      this.load();
    });
  }
  private clear() {
    this.generation++;
    this.replaced.next();
    this.items.set([]);
    this.detail.set(null);
    this.total.set(0);
    this.busy.set(false);
    this.pending.set(false);
    this.canRetry.set(false);
    this.recovery = undefined;
    this.assignmentReason = '';
    this.reason = '';
    this.disposition = '';
    this.proposal = '';
    this.target = '';
  }
  load() {
    const generation = ++this.generation;
    this.replaced.next();
    if (this.reference)
      this.api
        .review(this.reference, this.historyPage)
        .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
        .subscribe({
          next: (item) => {
            if (generation === this.generation) {
              this.detail.set(item);
              this.reviewer = item.reviewer;
              this.target = '';
            }
          },
          error: (error) => {
            if (generation === this.generation) {
              this.detail.set(null);
              this.message.set(error.error?.message ?? 'Review unavailable.');
            }
          },
        });
    else
      this.api
        .reviews(this.status, this.page)
        .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
        .subscribe({
          next: (result) => {
            if (generation === this.generation) {
              this.items.set(result.items);
              this.total.set(result.total);
            }
          },
          error: (error) => {
            if (generation === this.generation) {
              this.items.set([]);
              this.message.set(error.error?.message ?? 'Review queue unavailable.');
            }
          },
        });
  }
  locked() {
    return this.busy() || this.pending() || this.blocked;
  }
  assign() {
    if (!this.detail()?.actions?.assign) return;
    this.send({ action: 'ASSIGN', reviewer: this.reviewer, reason: this.assignmentReason });
  }
  transition() {
    if (!this.detail()?.actions?.transitions.includes(this.target)) return;
    this.send({
      action: 'TRANSITION',
      status: this.target,
      reason: this.reason,
      ...(this.target === 'DECIDED'
        ? {
            disposition: this.disposition,
            ...(this.proposal.trim() ? { clientSafeExplanation: this.proposal.trim() } : {}),
          }
        : {}),
    });
  }
  private send(body: Record<string, unknown>) {
    if (this.locked() || !this.recovery || !this.detail()) return;
    try {
      const request = this.recovery.prepare('review', {
        ...body,
        expectedVersion: this.detail()!.version,
      });
      this.execute(request.key, request.body);
    } catch (error) {
      this.message.set(error instanceof Error ? error.message : 'Unable to retain recovery key.');
    }
  }
  retry() {
    if (!this.busy() && this.recovery?.pending && this.recovery.body)
      this.execute(this.recovery.pending.key, this.recovery.body);
  }
  private execute(key: string, body: Record<string, unknown>) {
    const generation = this.generation;
    this.busy.set(true);
    this.pending.set(true);
    this.canRetry.set(true);
    this.api
      .reviewCommand(this.reference, key, body)
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
      .subscribe({
        next: () => {
          if (generation !== this.generation) return;
          this.busy.set(false);
          this.contexts.clear('Review changed. Start a fresh managed context.');
          try {
            this.recovery!.committed();
            this.pending.set(false);
            this.canRetry.set(false);
            this.reason = '';
            this.proposal = '';
            this.assignmentReason = '';
            this.target = '';
            this.load();
          } catch {
            this.message.set(
              'Command committed; recovery storage cleanup failed. Recover the original receipt.',
            );
          }
        },
        error: (error) => {
          if (generation !== this.generation) return;
          this.busy.set(false);
          if ([400, 409].includes(error.status) && error.error?.code !== 'IDEMPOTENCY_CONFLICT') {
            try {
              this.recovery!.committed();
              this.pending.set(false);
              this.canRetry.set(false);
              this.load();
            } catch {
              this.message.set('Command rejected; recovery cleanup failed.');
              return;
            }
          }
          this.message.set(
            error.error?.message ?? 'Outcome unknown. Preserve and recover the original key.',
          );
        },
      });
  }
  recover() {
    if (this.busy() || !this.recovery?.pending) return;
    const generation = this.generation;
    this.busy.set(true);
    this.api
      .recover(this.reference, this.recovery.pending.key, true)
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
      .subscribe({
        next: () => {
          if (generation !== this.generation) return;
          this.busy.set(false);
          try {
            this.recovery!.committed();
            this.pending.set(false);
            this.canRetry.set(false);
            this.contexts.clear('Review receipt recovered. Start a fresh context.');
            this.load();
          } catch {
            this.message.set('Receipt confirmed; browser cleanup unavailable.');
          }
        },
        error: (error) => {
          if (generation === this.generation) {
            this.busy.set(false);
            this.message.set(
              error.status === 404
                ? 'No confirmed receipt yet. Do not replace the key.'
                : (error.error?.message ?? 'Recovery unavailable.'),
            );
          }
        },
      });
  }
}
