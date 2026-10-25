import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { buildApiUrl } from '../../core/api/api-url';
import {
  AdvisorClient,
  AdvisorInstrument,
  AdvisorPage,
  AdvisorPortfolio,
  GuidanceRequest,
  GuidanceResponse,
  HoldingGuidance,
  Recommendation,
  RecommendationRequest,
  RecommendationUpdateRequest,
} from './advisor-models';

export interface AdvisorClientQuery {
  page?: number;
  pageSize?: number;
  search?: string;
}

@Injectable({ providedIn: 'root' })
export class AdvisorApi {
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);

  clients(query: AdvisorClientQuery = {}): Observable<AdvisorPage<AdvisorClient>> {
    return this.get<AdvisorPage<AdvisorClient>>(
      '/api/workspace/advisor/clients',
      this.params({ page: query.page, pageSize: query.pageSize, search: query.search }),
    );
  }

  portfolio(clientId: number): Observable<AdvisorPortfolio> {
    return this.get<AdvisorPortfolio>(
      `/api/workspace/advisor/clients/${this.id(clientId)}/portfolio`,
    );
  }

  guidance(clientId: number): Observable<GuidanceResponse> {
    return this.get<GuidanceResponse>(
      `/api/workspace/advisor/clients/${this.id(clientId)}/guidance`,
    );
  }

  saveGuidance(
    clientId: number,
    instrumentId: number,
    request: GuidanceRequest,
  ): Observable<HoldingGuidance> {
    return this.put<HoldingGuidance>(
      `/api/workspace/advisor/clients/${this.id(clientId)}/guidance/${this.id(instrumentId)}`,
      request,
    );
  }

  recommendations(clientId: number): Observable<{ items: Recommendation[]; asOf: string | null }> {
    return this.get(`/api/workspace/advisor/clients/${this.id(clientId)}/recommendations`);
  }

  createRecommendation(clientId: number, request: RecommendationRequest): Observable<Recommendation> {
    return this.post<Recommendation>(
      `/api/workspace/advisor/clients/${this.id(clientId)}/recommendations`,
      request,
    );
  }

  updateRecommendation(
    clientId: number,
    recommendationId: number,
    request: RecommendationUpdateRequest,
  ): Observable<Recommendation> {
    return this.patch<Recommendation>(
      `/api/workspace/advisor/clients/${this.id(clientId)}/recommendations/${this.id(recommendationId)}`,
      request,
    );
  }

  archiveRecommendation(clientId: number, recommendationId: number, expectedVersion: number) {
    return this.delete<{ recommendationId: number; version: number }>(
      `/api/workspace/advisor/clients/${this.id(clientId)}/recommendations/${this.id(recommendationId)}`,
      { expectedVersion },
    );
  }

  instruments(query: { page?: number; pageSize?: number; search?: string } = {}) {
    return this.get<AdvisorPage<AdvisorInstrument>>(
      '/api/workspace/advisor/instruments',
      this.params(query),
    );
  }

  private get<T>(path: string, params?: Record<string, string | number>) {
    return this.http
      .get<T>(this.url(path), params ? { params } : {})
      .pipe(catchError((error) => this.normalizeError(error)));
  }

  private post<T>(path: string, body: unknown) {
    return this.http
      .post<T>(this.url(path), body)
      .pipe(catchError((error) => this.normalizeError(error)));
  }

  private put<T>(path: string, body: unknown) {
    return this.http
      .put<T>(this.url(path), body)
      .pipe(catchError((error) => this.normalizeError(error)));
  }

  private patch<T>(path: string, body: unknown) {
    return this.http
      .patch<T>(this.url(path), body)
      .pipe(catchError((error) => this.normalizeError(error)));
  }

  private delete<T>(path: string, body: unknown) {
    return this.http
      .delete<T>(this.url(path), { body })
      .pipe(catchError((error) => this.normalizeError(error)));
  }

  private params(values: Record<string, string | number | undefined>) {
    return Object.fromEntries(
      Object.entries(values).filter(([, value]) => value !== undefined && value !== ''),
    ) as Record<string, string | number>;
  }

  private id(value: number): string {
    if (!Number.isSafeInteger(value) || value < 1) throw new Error('IDs must be positive integers.');
    return encodeURIComponent(String(value));
  }

  private url(path: string) {
    return buildApiUrl(path, this.platform);
  }

  private normalizeError(error: unknown): Observable<never> {
    if (!(error instanceof HttpErrorResponse)) return throwError(() => error);
    const body = error.error && typeof error.error === 'object' ? error.error : {};
    const detail = Array.isArray(body.detail)
      ? body.detail.map((item: unknown) => String(item)).join(', ')
      : body.detail;
    const defaults: Record<number, string> = {
      0: 'The advisory service is offline.',
      400: 'The advisory request is invalid. Check the selected instrument and reasoning.',
      401: 'Your session has expired. Please sign in again.',
      403: 'You do not have access to this advisory information.',
      404: 'This advisory record is unavailable.',
      409: 'The advisory record or eligibility changed. Refresh before retrying.',
      429: 'Too many advisory requests. Wait before retrying.',
    };
    const message =
      typeof body.message === 'string'
        ? body.message
        : typeof detail === 'string'
          ? detail
          : defaults[error.status] ?? 'The advisory service is temporarily unavailable.';
    return throwError(
      () =>
        new HttpErrorResponse({
          error: { ...body, message },
          headers: error.headers,
          status: error.status,
          statusText: error.statusText,
          url: error.url ?? undefined,
        }),
    );
  }
}
