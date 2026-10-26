import { TwoDecimalsPipe } from '../../shared/two-decimals.pipe';
import { DatePipe } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, takeUntil } from 'rxjs';
import { AdvisorApi } from './advisor-api.service';
import {
  AdvisorInstrument,
  AdvisorPage,
  AdvisorPortfolio,
  AdvisorPosition,
  AdvisorSaveState,
  GuidanceResponse,
  GuidanceStatus,
  Recommendation,
} from './advisor-models';
import { hasPositiveDecimal, normalizeReasoning, validReasoning } from './advisor-utils';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { AuthService } from '../../core/auth/auth.service';

type RecommendationData = { items: Recommendation[]; asOf: string | null };
type GuidanceDraft = { status: GuidanceStatus; reasoning: string; expectedVersion: number | null };

@Component({
  selector: 'app-advisor-client-detail-page',
  imports: [TwoDecimalsPipe, DatePipe, FormsModule, RouterLink, RequestStateComponent],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">ADVISOR WORKSPACE</p>
      <a routerLink="/advisor/clients">Back to assigned clients</a>
      @if (portfolio.data(); as data) {
        <h1 tabindex="-1">{{ data.clientName }}</h1>
        <p>{{ data.clientReference }} · current holdings and advisory records</p>
      } @else {
        <h1 tabindex="-1">Client detail</h1>
        <p>Review holdings and advisory records for the selected assigned client.</p>
      }
    </header>

    @if (routeError()) {
      <section class="notice" role="alert">
        <h2>Client unavailable</h2>
        <p>{{ routeError() }}</p>
        <a routerLink="/advisor/clients">Return to assigned clients</a>
      </section>
    } @else {
      <app-request-state
        [loading]="portfolio.loading() && !portfolio.data()"
        [error]="portfolio.error()"
        [status]="portfolio.status()"
        (retry)="loadPortfolio()"
      />

      @if (portfolio.data(); as data) {
        <section class="panel" aria-labelledby="holdings-heading">
          <div class="section-heading">
            <h2 id="holdings-heading">Current holdings</h2>
            <p class="as-of">Retrieved {{ data.retrievedAt | date: 'medium' : 'UTC' }} UTC</p>
          </div>
          @if (!data.currentPositions.length) {
            <p class="empty">This client has no current holdings.</p>
          } @else {
            <div class="table-wrapper" tabindex="0" aria-label="Client holdings, scroll horizontally">
              <table>
                <caption>Committed positions. Advisory guidance is not a trade instruction.</caption>
                <thead>
                  <tr>
                    <th scope="col">Instrument</th>
                    <th scope="col">Owned</th>
                    <th scope="col">Reserved</th>
                    <th scope="col">Available</th>
                    <th scope="col">Guidance</th>
                    <th scope="col">Rationale</th>
                    <th scope="col">Action</th>
                  </tr>
                </thead>
                <tbody>
                  @for (position of data.currentPositions; track position.instrumentId) {
                    <tr>
                      <th scope="row">{{ position.ticker }}<small>{{ position.name }} · {{ position.currency }}</small></th>
                      <td>{{ position.owned | twoDecimals }}</td>
                      <td>{{ position.reserved | twoDecimals }}</td>
                      <td>{{ position.available | twoDecimals }}</td>
                      <td>
                        @if (guidance.error()) {
                          <span role="status">Unavailable</span>
                        } @else if (guidance.loading() && !guidance.data()) {
                          <span role="status">Loading…</span>
                        } @else {
                          <label class="sr-only" [for]="'guidance-status-' + position.instrumentId">Guidance for {{ position.ticker }}</label>
                          <select
                            [id]="'guidance-status-' + position.instrumentId"
                            [disabled]="guidanceSaveState(position.instrumentId).saving"
                            [value]="guidanceDraft(position.instrumentId).status"
                            (change)="updateGuidanceStatus(position.instrumentId, $event)"
                          >
                            <option value="NONE">N/A</option>
                            <option value="HOLD">HOLD</option>
                            <option value="SELL">SELL</option>
                          </select>
                          @if (guidanceFor(position.instrumentId); as record) {
                            <small>{{ record.authorName || 'Author unavailable' }} · {{ record.updatedAt | date: 'medium' : 'UTC' }} UTC</small>
                          }
                        }
                      </td>
                      <td>
                        @if (!guidance.error() && guidance.data()) {
                          <label class="sr-only" [for]="'guidance-reasoning-' + position.instrumentId">Reasoning for {{ position.ticker }}</label>
                          <textarea
                            [id]="'guidance-reasoning-' + position.instrumentId"
                            [disabled]="guidanceSaveState(position.instrumentId).saving"
                            rows="2"
                            maxlength="2000"
                            [value]="guidanceDraft(position.instrumentId).reasoning"
                            (input)="updateGuidanceReasoning(position.instrumentId, $event)"
                          ></textarea>
                        } @else {
                          <span>Unavailable</span>
                        }
                      </td>
                      <td>
                        @if (!guidance.error() && guidance.data()) {
                          <button
                            type="button"
                            (click)="saveGuidance(position)"
                            [disabled]="guidanceSaveState(position.instrumentId).saving || guidanceSaveState(position.instrumentId).status === 409 || guidance.loading()"
                          >
                            {{ guidanceSaveState(position.instrumentId).saving ? 'Saving…' : 'Save' }}
                          </button>
                          @if (guidanceSaveState(position.instrumentId).error) {
                            <p class="field-error" role="alert">{{ guidanceSaveState(position.instrumentId).error }}</p>
                          }
                          @if (guidanceSaveState(position.instrumentId).status === 409) {
                            <button type="button" (click)="rebaseGuidance(position.instrumentId)" [disabled]="guidance.loading() || !!guidance.error()">Use refreshed version with my draft</button>
                          }
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>

        <app-request-state [loading]="guidance.loading() && !guidance.data()" [error]="guidance.error()" [status]="guidance.status()" (retry)="loadGuidance()" />

        <section class="panel" aria-labelledby="recommendations-heading">
          <div class="section-heading">
            <h2 id="recommendations-heading">Advisor recommends</h2>
            @if (recommendations.data()?.asOf; as asOf) {
              <p class="as-of">As of {{ asOf | date: 'medium' : 'UTC' }} UTC</p>
            }
          </div>
          <app-request-state
            [loading]="recommendations.loading() && !recommendations.data()"
            [error]="recommendations.error()"
            [status]="recommendations.status()"
            (retry)="loadRecommendations()"
          />
          @if (!recommendations.error() && recommendations.data(); as data) {
            @if (!visibleRecommendations().length) {
              <p class="empty">No recommendations are recorded.</p>
            } @else {
              <div class="table-wrapper" tabindex="0" aria-label="Advisor recommendations, scroll horizontally">
                <table>
                  <caption>Active recommendations for instruments the client does not hold</caption>
                  <thead>
                    <tr>
                      <th scope="col">Ticker / Instrument</th>
                      <th scope="col">Company / instrument name</th>
                      <th scope="col">Reasoning</th>
                      <th scope="col">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (recommendation of visibleRecommendations(); track recommendation.recommendationId) {
                      <tr>
                        <th scope="row">{{ recommendation.ticker }}</th>
                        <td>{{ recommendation.instrumentName }}</td>
                        <td>
                          <label class="sr-only" [for]="'recommendation-reasoning-' + recommendation.recommendationId">Reasoning for {{ recommendation.ticker }}</label>
                          <textarea
                            [id]="'recommendation-reasoning-' + recommendation.recommendationId"
                            [disabled]="recommendationSaveState(recommendation.recommendationId).saving || recommendationMutation().saving"
                            rows="2"
                            maxlength="2000"
                            [value]="recommendationDraft(recommendation).reasoning"
                            (input)="updateRecommendationReasoning(recommendation.recommendationId, $event)"
                          ></textarea>
                          @if (recommendationSaveState(recommendation.recommendationId).error) {
                            <p class="field-error" role="alert">{{ recommendationSaveState(recommendation.recommendationId).error }}</p>
                          }
                        </td>
                        <td>
                          <button
                            type="button"
                            (click)="saveRecommendation(recommendation)"
                            [disabled]="recommendationSaveState(recommendation.recommendationId).saving || recommendationSaveState(recommendation.recommendationId).status === 409 || recommendations.loading() || recommendationMutation().saving"
                          >
                            {{ recommendationSaveState(recommendation.recommendationId).saving ? 'Saving…' : 'Save' }}
                          </button>
                          @if (recommendationSaveState(recommendation.recommendationId).status === 409) {
                            <button type="button" (click)="rebaseRecommendation(recommendation.recommendationId)" [disabled]="recommendations.loading() || !!recommendations.error()">Use refreshed version with my draft</button>
                          }
                          <button type="button" (click)="requestArchive(recommendation, $event)" [disabled]="recommendationSaveState(recommendation.recommendationId).saving || recommendationMutation().saving">Archive</button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          }

          @if (canRecommend() && instruments.data(); as catalog) {
            <form class="recommendation-form" (ngSubmit)="createRecommendation()">
              <h3>Add a recommendation</h3>
              <p class="muted">Reasoning is required and must be 1–2000 trimmed characters. Advice never places an order.</p>
              <label for="recommendation-instrument">Ticker / Instrument</label>
              <select
                id="recommendation-instrument"
                name="instrumentId"
                [disabled]="recommendationMutation().saving"
                [value]="newRecommendationInstrument() ?? ''"
                (change)="setNewInstrument($event)"
              >
                <option value="">Select an eligible instrument</option>
                @for (instrument of eligibleInstruments(catalog); track instrument.instrumentId) {
                  <option [value]="instrument.instrumentId">{{ instrument.ticker }} · {{ instrument.instrumentName }}</option>
                }
              </select>
              <label for="new-recommendation-reasoning">Reasoning</label>
              <textarea
                id="new-recommendation-reasoning"
                name="reasoning"
                [disabled]="recommendationMutation().saving"
                rows="3"
                maxlength="2000"
                [(ngModel)]="newRecommendationReasoning"
              ></textarea>
              @if (recommendationMutation().error) {
                <p class="field-error" role="alert">{{ recommendationMutation().error }}</p>
              }
              <button type="submit" [disabled]="recommendationMutation().saving">
                {{ recommendationMutation().saving ? 'Saving…' : 'Add recommendation' }}
              </button>
            </form>
          } @else if (instruments.error()) {
            <p class="notice" role="alert">Instrument selection is unavailable. Retry before adding a recommendation.</p>
            <button type="button" (click)="loadInstruments()">Retry instrument selection</button>
          }
        </section>

        @if (pendingArchive(); as pending) {
          <section #archiveConfirmation class="notice" role="group" tabindex="-1" aria-labelledby="archive-heading" (keydown.escape)="cancelArchive()">
            <h2 id="archive-heading">Archive recommendation?</h2>
            <p>Archive {{ pending.ticker }} · {{ pending.instrumentName }}? The recommendation will be removed from the active table while its history remains preserved.</p>
            @if (recommendationMutation().error) {
              <p role="alert">{{ recommendationMutation().error }}</p>
            }
            <button type="button" (click)="cancelArchive()">Cancel</button>
            <button type="button" (click)="confirmArchive()" [disabled]="recommendationMutation().saving">Confirm archive</button>
          </section>
        }
      }
    }
  `,
})
export class AdvisorClientDetailPageComponent {
  private readonly api = inject(AdvisorApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly archiveConfirmation = viewChild<ElementRef<HTMLElement>>('archiveConfirmation');
  private archiveTrigger: HTMLElement | null = null;
  private readonly clientChanged = new Subject<void>();
  private generation = 0;

  readonly portfolio = new PageResource<AdvisorPortfolio>();
  readonly guidance = new PageResource<GuidanceResponse>();
  readonly recommendations = new PageResource<RecommendationData>();
  readonly instruments = new PageResource<AdvisorPage<AdvisorInstrument>>();
  readonly routeError = signal('');
  readonly selectedClientId = signal<number | null>(null);
  readonly guidanceDrafts = signal<Record<number, GuidanceDraft>>({});
  readonly guidanceSaves = signal<Record<number, AdvisorSaveState>>({});
  readonly recommendationDrafts = signal<Record<number, { reasoning: string; expectedVersion: number }>>({});
  readonly recommendationSaves = signal<Record<number, AdvisorSaveState>>({});
  readonly recommendationMutation = signal<AdvisorSaveState>({ saving: false, error: '', status: 0 });
  readonly pendingArchive = signal<Recommendation | null>(null);
  readonly newRecommendationInstrument = signal<number | null>(null);
  newRecommendationReasoning = '';

  constructor() {
    this.auth.sessionChanged.pipe(takeUntilDestroyed()).subscribe(() => this.openClient(null));
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => this.openClient(params.get('id')));
    this.destroyRef.onDestroy(() => this.clientChanged.next());
    effect(() => {
      if ([this.portfolio.status(), this.guidance.status(), this.recommendations.status(), this.instruments.status()].some((status) => [401, 403, 404].includes(status))) {
        this.denyAccess();
      }
    });
  }

  openClient(rawId: string | null): void {
    const clientId = Number(rawId);
    this.clientChanged.next();
    this.generation++;
    this.clearResource(this.portfolio);
    this.clearResource(this.guidance);
    this.clearResource(this.recommendations);
    this.clearResource(this.instruments);
    this.routeError.set(
      /^[1-9]\d*$/.test(rawId ?? '') && Number.isSafeInteger(clientId) ? '' : 'A positive client ID is required.',
    );
    this.selectedClientId.set(!this.routeError() ? clientId : null);
    this.guidanceDrafts.set({});
    this.guidanceSaves.set({});
    this.recommendationDrafts.set({});
    this.recommendationSaves.set({});
    this.pendingArchive.set(null);
    this.newRecommendationInstrument.set(null);
    this.newRecommendationReasoning = '';
    this.recommendationMutation.set({ saving: false, error: '', status: 0 });
    this.archiveTrigger = null;
    if (!this.selectedClientId()) return;
    this.loadPortfolio();
    this.loadGuidance();
    this.loadRecommendations();
    this.loadInstruments();
  }

  private clearResource<T>(resource: PageResource<T>): void {
    resource.load(EMPTY);
    resource.loading.set(false);
  }

  private denyAccess(): void {
    this.openClient(null);
    this.routeError.set('Client access is unavailable. Sign in again or return to assigned clients.');
  }

  loadInstruments(): void {
    if (this.selectedClientId()) this.instruments.load(this.api.instruments({ page: 0, pageSize: 100 }));
  }

  loadPortfolio(): void {
    const clientId = this.selectedClientId();
    if (clientId) this.portfolio.load(this.api.portfolio(clientId));
  }

  loadGuidance(): void {
    const clientId = this.selectedClientId();
    if (clientId) this.guidance.load(this.api.guidance(clientId));
  }

  loadRecommendations(): void {
    const clientId = this.selectedClientId();
    if (clientId) this.recommendations.load(this.api.recommendations(clientId));
  }

  guidanceFor(instrumentId: number) {
    return this.guidance.data()?.items.find((item) => item.instrumentId === instrumentId) ?? null;
  }

  guidanceDraft(instrumentId: number): GuidanceDraft {
    const existing = this.guidanceDrafts()[instrumentId];
    if (existing) return existing;
    const current = this.guidanceFor(instrumentId);
    return {
      status: current?.status ?? 'NONE',
      reasoning: current?.reasoning ?? '',
      expectedVersion: current?.version ?? null,
    };
  }

  updateGuidanceStatus(instrumentId: number, event: Event): void {
    const status = (event.target as HTMLSelectElement).value as GuidanceStatus;
    this.guidanceDrafts.update((drafts) => ({
      ...drafts,
      [instrumentId]: { ...this.guidanceDraft(instrumentId), status },
    }));
  }

  updateGuidanceReasoning(instrumentId: number, event: Event): void {
    const reasoning = (event.target as HTMLTextAreaElement).value;
    this.guidanceDrafts.update((drafts) => ({
      ...drafts,
      [instrumentId]: { ...this.guidanceDraft(instrumentId), reasoning },
    }));
  }

  guidanceSaveState(instrumentId: number): AdvisorSaveState {
    return this.guidanceSaves()[instrumentId] ?? { saving: false, error: '', status: 0 };
  }

  saveGuidance(position: AdvisorPosition): void {
    const clientId = this.selectedClientId();
    if (!clientId || !this.guidance.data() || this.guidance.error() || this.guidance.loading() || this.guidanceSaveState(position.instrumentId).saving || this.guidanceSaveState(position.instrumentId).status === 409) return;
    const draft = this.guidanceDraft(position.instrumentId);
    if (!['NONE', 'HOLD', 'SELL'].includes(draft.status) || draft.reasoning.trim().length > 2000) {
      this.setGuidanceSaveState(position.instrumentId, { saving: false, error: 'Use N/A, HOLD or SELL and at most 2000 trimmed reasoning characters.', status: 400 });
      return;
    }
    const generation = this.generation;
    this.setGuidanceSaveState(position.instrumentId, { saving: true, error: '', status: 0 });
    this.api
      .saveGuidance(clientId, position.instrumentId, {
        status: draft.status,
        reasoning: draft.reasoning.trim() || null,
        expectedVersion: draft.expectedVersion,
      })
      .pipe(takeUntil(this.clientChanged))
      .subscribe({
        next: (confirmed) => {
          if (generation !== this.generation) return;
          this.guidance.data.update((data) => data ? { ...data, items: [...data.items.filter((item) => item.instrumentId !== confirmed.instrumentId), confirmed] } : data);
          this.setGuidanceSaveState(position.instrumentId, { saving: false, error: '', status: 200 });
          this.guidanceDrafts.update((drafts) => {
            const next = { ...drafts };
            delete next[position.instrumentId];
            return next;
          });
        },
        error: (error: unknown) => {
          if (generation !== this.generation) return;
          const status = this.errorStatus(error);
          if ([401, 403, 404].includes(status)) { this.denyAccess(); return; }
          this.setGuidanceSaveState(position.instrumentId, {
            saving: false,
            error: status === 409 ? 'This guidance changed. Refreshing the current version; your draft is preserved.' : this.errorMessage(error),
            status,
          });
          if (status === 409) this.loadGuidance();
        },
      });
  }

  recommendationDraft(recommendation: Recommendation) {
    return this.recommendationDrafts()[recommendation.recommendationId] ?? {
      reasoning: recommendation.reasoning,
      expectedVersion: recommendation.version,
    };
  }

  updateRecommendationReasoning(recommendationId: number, event: Event): void {
    const reasoning = (event.target as HTMLTextAreaElement).value;
    const existing = this.recommendations.data()?.items.find((item) => item.recommendationId === recommendationId);
    if (!existing) return;
    this.recommendationDrafts.update((drafts) => ({
      ...drafts,
      [recommendationId]: { ...this.recommendationDraft(existing), reasoning },
    }));
  }

  recommendationSaveState(recommendationId: number): AdvisorSaveState {
    return this.recommendationSaves()[recommendationId] ?? { saving: false, error: '', status: 0 };
  }

  saveRecommendation(recommendation: Recommendation): void {
    const clientId = this.selectedClientId();
    if (!clientId || !this.canRecommend() || this.recommendationSaveState(recommendation.recommendationId).saving || this.recommendationSaveState(recommendation.recommendationId).status === 409 || this.recommendationMutation().saving) return;
    const draft = this.recommendationDraft(recommendation);
    if (!validReasoning(draft.reasoning)) {
      this.setRecommendationSaveState(recommendation.recommendationId, {
        saving: false,
        error: 'Reasoning must contain 1–2000 trimmed characters.',
        status: 400,
      });
      return;
    }
    const generation = this.generation;
    this.setRecommendationSaveState(recommendation.recommendationId, { saving: true, error: '', status: 0 });
    this.api
      .updateRecommendation(clientId, recommendation.recommendationId, {
        reasoning: normalizeReasoning(draft.reasoning),
        expectedVersion: draft.expectedVersion,
      })
      .pipe(takeUntil(this.clientChanged))
      .subscribe({
        next: (confirmed) => {
          if (generation !== this.generation) return;
          this.recommendations.data.update((data) => data ? { ...data, items: data.items.map((item) => item.recommendationId === confirmed.recommendationId ? confirmed : item) } : data);
          this.setRecommendationSaveState(recommendation.recommendationId, { saving: false, error: '', status: 200 });
          this.recommendationDrafts.update((drafts) => {
            const next = { ...drafts };
            delete next[recommendation.recommendationId];
            return next;
          });
        },
        error: (error: unknown) => {
          if (generation !== this.generation) return;
          const status = this.errorStatus(error);
          if ([401, 403, 404].includes(status)) { this.denyAccess(); return; }
          this.setRecommendationSaveState(recommendation.recommendationId, {
            saving: false,
            error: status === 409 ? 'This recommendation changed. Refreshing the current version; your draft is preserved.' : this.errorMessage(error),
            status,
          });
          if (status === 409) { this.loadRecommendations(); this.loadPortfolio(); }
        },
      });
  }

  eligibleInstruments(catalog: AdvisorPage<AdvisorInstrument>): AdvisorInstrument[] {
    if (!this.canRecommend()) return [];
    const positions = this.portfolio.data()?.currentPositions ?? [];
    const activeRecommendations = this.recommendations.data()?.items ?? [];
    return catalog.items.filter(
      (instrument) =>
        instrument.isActive &&
        !positions.some(
          (position) => position.instrumentId === instrument.instrumentId && hasPositiveDecimal(position.owned),
        ) &&
        !activeRecommendations.some((recommendation) => recommendation.instrumentId === instrument.instrumentId),
    );
  }

  setNewInstrument(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.newRecommendationInstrument.set(value ? Number(value) : null);
  }

  createRecommendation(): void {
    const clientId = this.selectedClientId();
    const instrumentId = this.newRecommendationInstrument();
    if (!clientId || this.recommendationMutation().saving) return;
    const catalog = this.instruments.data();
    if (!instrumentId || !catalog || !this.eligibleInstruments(catalog).some((item) => item.instrumentId === instrumentId) || !validReasoning(this.newRecommendationReasoning)) {
      this.recommendationMutation.set({
        saving: false,
        error: 'Select an eligible instrument and enter 1–2000 trimmed reasoning characters.',
        status: 400,
      });
      return;
    }
    const generation = this.generation;
    this.recommendationMutation.set({ saving: true, error: '', status: 0 });
    this.api
      .createRecommendation(clientId, {
        instrumentId,
        reasoning: normalizeReasoning(this.newRecommendationReasoning),
      })
      .pipe(takeUntil(this.clientChanged))
      .subscribe({
        next: () => {
          if (generation !== this.generation) return;
          this.recommendationMutation.set({ saving: false, error: '', status: 201 });
          this.newRecommendationInstrument.set(null);
          this.newRecommendationReasoning = '';
          this.loadRecommendations();
          this.loadPortfolio();
        },
        error: (error: unknown) => {
          if (generation !== this.generation) return;
          if ([401, 403, 404].includes(this.errorStatus(error))) { this.denyAccess(); return; }
          this.recommendationMutation.set({ saving: false, error: this.errorMessage(error), status: this.errorStatus(error) });
          if (this.errorStatus(error) === 409) { this.loadRecommendations(); this.loadPortfolio(); }
        },
      });
  }

  requestArchive(recommendation: Recommendation, event?: Event): void {
    if (this.recommendationMutation().saving || this.recommendationSaveState(recommendation.recommendationId).saving) return;
    this.archiveTrigger = event?.currentTarget as HTMLElement | null;
    this.pendingArchive.set(recommendation);
    this.recommendationMutation.set({ saving: false, error: '', status: 0 });
    this.changeDetector.detectChanges();
    this.archiveConfirmation()?.nativeElement.focus();
  }

  cancelArchive(): void {
    this.pendingArchive.set(null);
    this.archiveTrigger?.focus();
  }

  confirmArchive(): void {
    const clientId = this.selectedClientId();
    const recommendation = this.pendingArchive();
    if (!clientId || !recommendation || this.recommendationMutation().saving) return;
    const generation = this.generation;
    this.recommendationMutation.set({ saving: true, error: '', status: 0 });
    this.api
      .archiveRecommendation(clientId, recommendation.recommendationId, recommendation.version)
      .pipe(takeUntil(this.clientChanged))
      .subscribe({
        next: () => {
          if (generation !== this.generation) return;
          this.pendingArchive.set(null);
          this.recommendationMutation.set({ saving: false, error: '', status: 200 });
          this.loadRecommendations();
          this.loadPortfolio();
        },
        error: (error: unknown) => {
          if (generation !== this.generation) return;
          if ([401, 403, 404].includes(this.errorStatus(error))) { this.denyAccess(); return; }
          this.recommendationMutation.set({ saving: false, error: this.errorMessage(error), status: this.errorStatus(error) });
          if (this.errorStatus(error) === 409) { this.cancelArchive(); this.loadRecommendations(); this.loadPortfolio(); }
        },
      });
  }

  canRecommend(): boolean {
    return !!this.portfolio.data() && !this.portfolio.loading() && !this.portfolio.error() && !!this.recommendations.data() && !this.recommendations.loading() && !this.recommendations.error() && !!this.instruments.data() && !this.instruments.error() && !this.instruments.loading();
  }

  visibleRecommendations(): Recommendation[] {
    return (this.recommendations.data()?.items ?? []).filter((item) => !this.portfolio.data()?.currentPositions.some((position) => position.instrumentId === item.instrumentId && hasPositiveDecimal(position.owned)));
  }

  rebaseGuidance(instrumentId: number): void {
    if (!this.guidance.data() || this.guidance.loading() || this.guidance.error()) return;
    this.guidanceDrafts.update((drafts) => ({ ...drafts, [instrumentId]: { ...this.guidanceDraft(instrumentId), expectedVersion: this.guidanceFor(instrumentId)?.version ?? null } }));
    this.setGuidanceSaveState(instrumentId, { saving: false, error: '', status: 0 });
  }

  rebaseRecommendation(recommendationId: number): void {
    const current = this.recommendations.data()?.items.find((item) => item.recommendationId === recommendationId);
    if (!current || this.recommendations.loading() || this.recommendations.error()) return;
    this.recommendationDrafts.update((drafts) => ({ ...drafts, [recommendationId]: { ...this.recommendationDraft(current), expectedVersion: current.version } }));
    this.setRecommendationSaveState(recommendationId, { saving: false, error: '', status: 0 });
  }

  private setGuidanceSaveState(instrumentId: number, state: AdvisorSaveState): void {
    this.guidanceSaves.update((states) => ({ ...states, [instrumentId]: state }));
  }

  private setRecommendationSaveState(recommendationId: number, state: AdvisorSaveState): void {
    this.recommendationSaves.update((states) => ({ ...states, [recommendationId]: state }));
  }

  private errorStatus(error: unknown): number {
    return typeof error === 'object' && error !== null && 'status' in error
      ? Number((error as { status?: number }).status ?? 0)
      : 0;
  }

  private errorMessage(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const body = (error as { error?: { message?: string } }).error;
      if (body?.message) return body.message;
    }
    return 'The advisory change could not be saved. Your draft is preserved.';
  }
}
