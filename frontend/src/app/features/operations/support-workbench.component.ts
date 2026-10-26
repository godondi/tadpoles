import {
  Component,
  DestroyRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, takeUntil } from 'rxjs';
import {
  CaseExportPage,
  CaseWorkflowApi,
  EligibleStaff,
  Workbench,
  WorkflowTask,
} from '../../core/api/case-workflow-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { OperationsApi } from '../../core/api/operations-api.service';
import { PrivateCommand } from '../../shared/workspace/private-command';
import { EvidenceFactsComponent } from '../../shared/workspace/evidence-facts.component';
import { CASE_CATEGORIES } from '../../core/api/support-api.service';
import { utcFilter } from '../../shared/workspace/utc-filter';

@Component({
  selector: 'app-support-workbench',
  imports: [FormsModule, DatePipe, RouterLink, EvidenceFactsComponent],
  template: `
    <section class="panel case-workbench" aria-label="Private case workbench">
      <header class="workspace-heading">
        <div>
          <h2>Operations workbench - private</h2>
          <p>Private activity does not notify the client or change order execution.</p>
        </div>
        <button (click)="load()" [disabled]="busy()">Refresh workbench</button>
      </header>
      @if (message()) {
        <p role="alert" class="notice">{{ message() }}</p>
      }
      @if (pending()) {
        <p role="status">An original command needs confirmation. Do not create a replacement.</p>
        <button (click)="recover()" [disabled]="busy()">Recover original command</button>
        @if (canRetry()) {
          <button (click)="retry()" [disabled]="busy()">Retry original command</button>
        }
      }
      @if (data(); as w) {
        <p>
          Original: {{ w.originalCategory }} / Effective: {{ w.effectiveCategory }} / Private
          version {{ w.workflowVersion }} / Checklist {{ w.checklistRevision }}
        </p>
        <p>
          {{ w.responseTarget }}. Owner {{ w.assignee ?? 'unassigned' }}; priority {{ w.priority }}.
          Next action: {{ w.nextAction ?? 'not recorded' }}; due
          {{ (w.dueAt | date: 'medium' : 'UTC') ?? 'not set' }} UTC.
        </p>
        <details>
          <summary>Original structured intake</summary>
          <app-evidence-facts [value]="w.intake" />
        </details>
        @if (w.actions.workflow) {
          <div class="workbench-grid">
            <form class="workspace-form" (ngSubmit)="send('assignment', { assignee: assignee })">
              <label
                >Operations owner<select name="assignee" [(ngModel)]="assignee">
                  <option [ngValue]="null">Unassigned</option>
                  @for (person of assignees(); track person.id) {
                    <option [ngValue]="person.id">{{ person.username }}</option>
                  }
                </select></label
              ><button [disabled]="locked()">Save owner</button>
            </form>
            <form
              class="workspace-form"
              (ngSubmit)="send('priority', { priority, reason: priorityReason })"
            >
              <label
                >Priority<select name="priority" [(ngModel)]="priority">
                  <option>LOW</option>
                  <option>NORMAL</option>
                  <option>HIGH</option>
                </select></label
              >
              <label
                >Priority reason<textarea
                  name="priorityReason"
                  [(ngModel)]="priorityReason"
                  minlength="10"
                  maxlength="2000"
                  required
                ></textarea>
              </label>
              <button [disabled]="locked()">Save priority</button>
            </form>
            <form
              class="workspace-form"
              (ngSubmit)="send('classification', { category, reason: classificationReason })"
            >
              <label
                >Effective category<select name="category" [(ngModel)]="category">
                  @for (c of categories; track c) {
                    <option>{{ c }}</option>
                  }
                </select></label
              >
              <label
                >Classification reason<textarea
                  name="classificationReason"
                  [(ngModel)]="classificationReason"
                  minlength="10"
                  maxlength="2000"
                  required
                ></textarea>
              </label>
              <button [disabled]="locked()">Reclassify with fresh checklist</button>
            </form>
            <form class="workspace-form" (ngSubmit)="next()">
              <label
                >Next action<textarea
                  name="nextAction"
                  [(ngModel)]="nextAction"
                  minlength="10"
                  maxlength="500"
                  required
                ></textarea>
              </label>
              <label
                >Optional due time (UTC)<input
                  type="datetime-local"
                  name="dueAt"
                  [(ngModel)]="dueAt"
              /></label>
              <button [disabled]="locked()">Record next action</button>
            </form>
          </div>
        }
        <details class="workbench-section">
          <summary>Scoped evidence and diagnostics</summary>
        @if (instrumentId(); as instrument) {
          <a class="button-link" [routerLink]="['/operations/instruments', instrument]"
            >Review instrument policy separately (independent authorization required)</a
          >
        }
        <p>
          Current diagnostics do not reconstruct historical facts. No test trade or financial repair
          is performed.
        </p>
        @if (diagnosticKind) {
          <form class="filters" (ngSubmit)="diagnose()">
            <label
              >Evidence kind<select name="diagnosticKind" [(ngModel)]="diagnosticKind">
                @if (w.diagnosticsAllowed.account) {
                  <option value="account">Account resources / movement</option>
                  <option value="availability">Current trading availability</option>
                }
                @if (w.diagnosticsAllowed.historical) {
                  <option value="historical">Retained historical source</option>
                }
                @if (w.diagnosticsAllowed.order) {
                  <option value="order">Purpose-gated linked order</option>
                }
              </select></label
            >
            <label
              >Owned evidence reference (optional when retained on this case)<input
                name="evidence"
                [(ngModel)]="evidence"
                maxlength="200"
            /></label>
            <button [disabled]="diagnosticBusy() || !diagnosticKind">
              Load independently authorized evidence
            </button>
          </form>
          <p>
            Omitted references use the original owned intake/order when available. Historical/order
            checks otherwise need a source ID. Account-wide availability can be reviewed without
            inventing an instrument or quote.
          </p>
        } @else {
          <p role="status">
            No diagnostic permission is available. Assignment and case access do not grant account,
            historical or order evidence.
          </p>
        }
        @if (diagnosticError()) {
          <p role="alert">{{ diagnosticError() }}</p>
        }
        @if (diagnostic(); as facts) {
          <app-evidence-facts [value]="facts" />
        }
        </details>
        <details class="workbench-section">
          <summary>Private Operations evidence library</summary>
        <p>
          Associations retain original requester, purpose, digest and expiry. Metadata alone never
          authorizes a download. Use the protected Operations export workflow with its original
          context; Compliance archives are separate.
        </p>
        @if (libraryLoading()) {
          <p role="status">Loading associated export metadata...</p>
        }
        @if (libraryError()) {
          <p role="alert">{{ libraryError() }}</p>
          <button (click)="load()">Retry evidence library</button>
        }
        @if (library(); as exports) {
          @for (item of exports.items; track item.exportId) {
            <article class="panel case-prose">
              <h4>Order {{ item.orderId }} / {{ item.exportId }}</h4>
              <p>
                Requester {{ item.requester }}; associated
                {{ item.associatedAt | date: 'medium' : 'UTC' }} UTC; expires
                {{ item.expiresAt | date: 'medium' : 'UTC' }} UTC.
              </p>
              <p>Archive SHA-256: {{ item.digest }}</p>
            </article>
          } @empty {
            <p>No Operations export has been associated with this case.</p>
          }
          <p>{{ exports.note }}</p>
        }
        <div class="actions">
          <button (click)="exportHistory(-1)" [disabled]="libraryLoading() || exportPage === 0">
            Previous export associations
          </button>
          <span>Page {{ exportPage + 1 }}</span
          ><button (click)="exportHistory(1)" [disabled]="libraryLoading() || !library()?.hasMore">
            Next export associations
          </button>
        </div>
        </details>
        <details class="workbench-section">
          <summary>Category checklist</summary>
        @for (task of w.tasks; track task.code) {
          <article class="panel">
            <h4>{{ task.label }}</h4>
            <p>{{ task.state }} - {{ task.reason ?? 'Not checked' }}</p>
            @if (w.actions.workflow) {
              <form class="workspace-form" (ngSubmit)="saveTask(task)">
                <label
                  >State<select [name]="task.code + 'state'" [(ngModel)]="task.state">
                    <option>PENDING</option>
                    <option>COMPLETED</option>
                    <option>UNAVAILABLE</option>
                    <option>NOT_APPLICABLE</option>
                  </select></label
                >
                <label
                  >Check / unavailable reason<textarea
                    [name]="task.code + 'reason'"
                    [(ngModel)]="task.reason"
                    minlength="10"
                    maxlength="2000"
                    required
                  ></textarea>
                </label>
                <label
                  >Evidence kind<select
                    [name]="task.code + 'kind'"
                    [(ngModel)]="task.evidenceKind"
                    (ngModelChange)="
                      task.evidenceReference = $event === 'ACCOUNT' ? reference : undefined
                    "
                  >
                    <option value="">No evidence reference</option>
                    @for (kind of evidenceKinds; track kind) {
                      <option>{{ kind }}</option>
                    }
                  </select></label
                >
                <label
                  >Owned evidence reference<input
                    [name]="task.code + 'reference'"
                    [(ngModel)]="task.evidenceReference"
                    maxlength="200"
                /></label>
                <button [disabled]="locked()">Retain task result</button>
              </form>
            }
          </article>
        }
        </details>
        <details class="workbench-section">
          <summary>Compliance handoff</summary>
        <p>Mandatory referrals block resolution until a decision with disposition is retained.</p>
        @for (review of w.referrals; track review.reference) {
          <article>
            <strong>{{ review.reference }}</strong> / {{ review.status }} / next owner
            {{ review.nextOwner }}
            <p>Disposition: {{ review.disposition ?? 'pending' }}</p>
            @if (review.informationRequest) {
              <p class="case-prose">Information requested: {{ review.informationRequest }}</p>
            }
            @if (review.clientSafeExplanation) {
              <p class="case-prose">Private proposal: {{ review.clientSafeExplanation }}</p>
            }
          </article>
        }
        @if (w.actions.workflow) {
          <form
            class="workspace-form"
            (ngSubmit)="send('referral', { reviewer, reasonCode, summary })"
          >
            <label
              >Independently eligible reviewer<select
                name="reviewer"
                [(ngModel)]="reviewer"
                required
              >
                <option [ngValue]="null">Choose reviewer</option>
                @for (person of reviewers(); track person.id) {
                  <option [ngValue]="person.id">{{ person.username }}</option>
                }
              </select></label
            >
            <label
              >Referral reason<select name="reasonCode" [(ngModel)]="reasonCode">
                <option>POLICY</option>
                <option>INTEGRITY</option>
                <option>ELIGIBILITY</option>
                <option>PROVENANCE</option>
              </select></label
            >
            <label
              >Approved referral summary only<textarea
                name="summary"
                [(ngModel)]="summary"
                minlength="10"
                maxlength="2000"
                required
              ></textarea>
            </label>
            <button [disabled]="locked() || !reviewer">Create mandatory private referral</button>
          </form>
        }
        @if (w.actions.resolve) {
          <h3>Explicit public resolution</h3>
          <form
            class="workspace-form"
            (ngSubmit)="
              send('resolution', {
                code: resolution,
                expectedVersion: w.publicVersion,
                body: explanation,
              })
            "
          >
            <label
              >Category disposition<select name="resolution" [(ngModel)]="resolution" required>
                <option value="">Choose disposition</option>
                @for (code of w.definitions.resolutions; track code) {
                  <option>{{ code }}</option>
                }
              </select></label
            >
            <label
              >Client-safe public explanation<textarea
                name="explanation"
                [(ngModel)]="explanation"
                minlength="10"
                maxlength="4000"
                required
              ></textarea>
            </label>
            <p>
              Only this deliberate action publishes the explanation and resolves the public case.
            </p>
            <button [disabled]="locked() || w.mandatoryReferralPending">
              Publish explanation and resolve atomically
            </button>
          </form>
        }
        </details>
        <details>
          <summary>Private attributable workflow history</summary>
          @for (event of w.history; track event.version) {
            <article>
              <p>
                Version {{ event.version }} / {{ event.kind }} / actor
                {{ event.actor ?? 'migration' }} /
                {{ event.recordedAt | date: 'medium' : 'UTC' }} UTC
              </p>
              <p class="case-prose">{{ event.facts }}</p>
            </article>
          }
          <div class="actions">
            <button (click)="history(-1)" [disabled]="page === 0">Previous history</button
            ><span>Page {{ page + 1 }}</span
            ><button (click)="history(1)" [disabled]="w.history.length < 20">Older history</button>
          </div>
        </details>
      }
    </section>
  `,
})
export class SupportWorkbenchComponent implements OnChanges {
  @Input({ required: true }) reference = '';
  @Output() publicChanged = new EventEmitter<void>();
  private readonly api = inject(CaseWorkflowApi);
  private readonly auth = inject(AuthService);
  private readonly contexts = inject(OperationsApi);
  private readonly destroy = inject(DestroyRef);
  private readonly replaced = new Subject<void>();
  private generation = 0;
  private recovery?: PrivateCommand;
  private storageBlocked = false;
  readonly data = signal<Workbench | null>(null);
  readonly message = signal('');
  readonly busy = signal(false);
  readonly pending = signal(false);
  readonly canRetry = signal(false);
  readonly assignees = signal<EligibleStaff[]>([]);
  readonly reviewers = signal<EligibleStaff[]>([]);
  readonly diagnostic = signal<Record<string, unknown> | null>(null);
  readonly diagnosticBusy = signal(false);
  readonly diagnosticError = signal('');
  readonly library = signal<CaseExportPage | null>(null);
  readonly libraryLoading = signal(false);
  readonly libraryError = signal('');
  readonly categories = CASE_CATEGORIES;
  readonly evidenceKinds = [
    'ACCOUNT',
    'MOVEMENT',
    'ORDER',
    'HISTORICAL',
    'POLICY_CHANGE',
    'DUPLICATE_CASE',
    'EXTERNAL_REFERENCE',
  ];
  assignee: number | null = null;
  reviewer: number | null = null;
  priority = 'NORMAL';
  priorityReason = '';
  category = 'OTHER';
  classificationReason = '';
  nextAction = '';
  dueAt = '';
  diagnosticKind = 'account';
  evidence = '';
  reasonCode = 'POLICY';
  summary = '';
  resolution = '';
  explanation = '';
  page = 0;
  exportPage = 0;
  constructor() {
    this.auth.sessionChanged.pipe(takeUntilDestroyed()).subscribe(() => this.clear());
    this.api.denied.pipe(takeUntilDestroyed()).subscribe(() => this.clear());
    this.contexts.changed.pipe(takeUntilDestroyed()).subscribe(() => {
      this.generation++;
      this.replaced.next();
      this.diagnostic.set(null);
      this.diagnosticBusy.set(false);
      this.busy.set(false);
      this.library.set(null);
      this.libraryLoading.set(false);
    });
  }
  clear() {
    this.generation++;
    this.replaced.next();
    this.data.set(null);
    this.diagnostic.set(null);
    this.assignees.set([]);
    this.reviewers.set([]);
    this.recovery = undefined;
    this.pending.set(false);
    this.canRetry.set(false);
    this.busy.set(false);
    this.explanation = '';
    this.summary = '';
    this.priorityReason = '';
    this.classificationReason = '';
    this.nextAction = '';
    this.evidence = '';
    this.diagnosticBusy.set(false);
    this.diagnosticError.set('');
    this.library.set(null);
    this.libraryError.set('');
    this.libraryLoading.set(false);
  }
  ngOnChanges() {
    this.clear();
    this.page = 0;
    this.exportPage = 0;
    this.storageBlocked = false;
    try {
      this.recovery = new PrivateCommand(this.auth.identity()?.userId ?? 0, this.reference);
      this.pending.set(!!this.recovery.pending);
    } catch (error) {
      this.storageBlocked = true;
      this.message.set(error instanceof Error ? error.message : 'Recovery storage unavailable.');
    }
    this.load();
  }
  load() {
    const generation = ++this.generation;
    this.replaced.next();
    this.diagnosticBusy.set(false);
    this.library.set(null);
    this.libraryError.set('');
    this.libraryLoading.set(false);
    this.api
      .workbench(this.reference, this.page)
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
      .subscribe({
        next: (w) => {
          if (generation !== this.generation) return;
          this.data.set(w);
          this.assignee = w.assignee;
          this.priority = w.priority;
          this.category = w.effectiveCategory;
          this.diagnosticKind = w.diagnosticsAllowed.account
            ? 'account'
            : w.diagnosticsAllowed.historical
              ? 'historical'
              : w.diagnosticsAllowed.order
                ? 'order'
                : '';
          this.libraryLoading.set(true);
          this.api
            .exports(this.reference, this.exportPage)
            .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
            .subscribe({
              next: (result) => {
                if (generation === this.generation) {
                  this.library.set(result);
                  this.libraryLoading.set(false);
                }
              },
              error: (error) => {
                if (generation === this.generation) {
                  this.libraryError.set(
                    error.error?.message ?? 'Associated export metadata unavailable.',
                  );
                  this.libraryLoading.set(false);
                }
              },
            });
          if (w.actions.workflow)
            for (const reviewers of [false, true])
              this.api
                .staff(this.reference, reviewers)
                .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
                .subscribe({
                  next: (result) => {
                    if (generation === this.generation)
                      (reviewers ? this.reviewers : this.assignees).set(result.items);
                  },
                  error: (error) => {
                    if (generation === this.generation)
                      this.message.set(
                        error.error?.message ?? 'Eligible owner directory unavailable.',
                      );
                  },
                });
        },
        error: (error) => {
          if (generation === this.generation) {
            this.data.set(null);
            this.message.set(error.error?.message ?? 'Workbench unavailable.');
          }
        },
      });
  }
  locked() {
    return this.busy() || this.pending() || this.storageBlocked;
  }
  instrumentId() {
    const id = this.data()?.intake['instrumentId'];
    return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? id : undefined;
  }
  history(delta: number) {
    this.page = Math.max(0, this.page + delta);
    this.load();
  }
  exportHistory(delta: number) {
    this.exportPage = Math.max(0, this.exportPage + delta);
    this.load();
  }
  next() {
    try {
      this.send('next-action', {
        nextAction: this.nextAction,
        dueAt: utcFilter(this.dueAt) ?? null,
      });
    } catch (error) {
      this.message.set(error instanceof Error ? error.message : 'Invalid UTC due time.');
    }
  }
  saveTask(task: WorkflowTask) {
    this.send('tasks', {
      code: task.code,
      state: task.state,
      reason: task.reason,
      ...(task.evidenceKind
        ? { evidenceKind: task.evidenceKind, evidenceReference: task.evidenceReference }
        : {}),
    });
  }
  send(action: string, body: Record<string, unknown>) {
    if (this.locked() || !this.data() || !this.recovery) return;
    try {
      const request = this.recovery.prepare(action, {
        ...body,
        expectedWorkflowVersion: this.data()!.workflowVersion,
        ...(this.contexts.context() ? { contextId: this.contexts.context()!.contextId } : {}),
      });
      // Context identifiers are only accepted by evidence-bearing commands.
      if (!['tasks', 'resolution'].includes(action)) delete request.body['contextId'];
      this.execute(request.action, request.key, request.body);
    } catch (error) {
      this.message.set(
        error instanceof Error ? error.message : 'Unable to preserve original request key.',
      );
    }
  }
  retry() {
    if (this.busy() || !this.recovery?.pending || !this.recovery.body) return;
    this.execute(this.recovery.pending.action, this.recovery.pending.key, this.recovery.body);
  }
  private execute(action: string, key: string, body: Record<string, unknown>) {
    const generation = this.generation;
    this.busy.set(true);
    this.pending.set(true);
    this.canRetry.set(true);
    this.api
      .command(this.reference, action, key, body)
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
      .subscribe({
        next: () => {
          if (generation !== this.generation) return;
          this.busy.set(false);
          try {
            this.recovery!.committed();
            this.pending.set(false);
            this.canRetry.set(false);
          } catch {
            this.message.set(
              'Command committed, but browser recovery cleanup failed. Recover the original receipt before further changes.',
            );
            return;
          }
          this.message.set(
            'Command retained. Public activity changes only through an explicit publication.',
          );
          if (action === 'resolution') this.publicChanged.emit();
          this.load();
        },
        error: (error) => {
          if (generation !== this.generation) return;
          this.busy.set(false);
          if ([400, 409].includes(error.status) && error.error?.code !== 'IDEMPOTENCY_CONFLICT') {
            try {
              this.recovery!.committed();
              this.pending.set(false);
              this.canRetry.set(false);
            } catch {
              this.message.set('Server rejected the command; recovery cleanup failed.');
              return;
            }
            this.load();
          }
          this.message.set(
            error.error?.message ?? 'Outcome unknown. Recover or retry only the original command.',
          );
        },
      });
  }
  recover() {
    if (this.busy() || !this.recovery?.pending) return;
    const generation = this.generation;
    this.busy.set(true);
    this.api
      .recover(this.reference, this.recovery.pending.key)
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
      .subscribe({
        next: () => {
          if (generation !== this.generation) return;
          this.busy.set(false);
          try {
            this.recovery!.committed();
            this.pending.set(false);
            this.canRetry.set(false);
            this.load();
            this.publicChanged.emit();
          } catch {
            this.message.set('Receipt confirmed; browser recovery cleanup unavailable.');
          }
        },
        error: (error) => {
          if (generation === this.generation) {
            this.busy.set(false);
            this.message.set(
              error.status === 404
                ? 'No receipt confirmed yet. Keep the original key; do not replace it.'
                : (error.error?.message ?? 'Recovery unavailable.'),
            );
          }
        },
      });
  }
  diagnose() {
    if (this.diagnosticBusy() || !this.diagnosticKind) return;
    const generation = this.generation;
    this.diagnosticBusy.set(true);
    this.diagnostic.set(null);
    this.diagnosticError.set('');
    this.api
      .diagnostic(
        this.reference,
        this.diagnosticKind,
        this.evidence,
        this.contexts.context()?.contextId,
      )
      .pipe(takeUntilDestroyed(this.destroy), takeUntil(this.replaced))
      .subscribe({
        next: (value) => {
          if (generation === this.generation) {
            this.diagnostic.set(value);
            this.diagnosticBusy.set(false);
          }
        },
        error: (error) => {
          if (generation === this.generation) {
            this.diagnosticBusy.set(false);
            this.diagnosticError.set(
              error.error?.message ?? 'Required evidence is unavailable, not passed.',
            );
          }
        },
      });
  }
}
