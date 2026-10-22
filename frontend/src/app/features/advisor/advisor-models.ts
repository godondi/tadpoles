/** Proposed workspace wire contract; backend integration is pending. */
export type GuidanceStatus = 'NONE' | 'HOLD' | 'SELL';

export interface AdvisorClient {
  clientId: number;
  displayName: string;
  clientReference: string;
  assignmentStatus?: string | null;
  updatedAt: string;
}

export interface AdvisorPage<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface AdvisorPosition {
  instrumentId: number;
  ticker: string;
  name: string;
  currency: string;
  owned: string;
  reserved: string;
  available: string;
  updatedAt: string;
}

export interface AdvisorPortfolio {
  clientId: number;
  clientName: string;
  clientReference: string;
  currentPositions: AdvisorPosition[];
  retrievedAt: string;
}

export interface HoldingGuidance {
  instrumentId: number;
  status: GuidanceStatus;
  reasoning: string | null;
  authorName: string | null;
  version: number;
  updatedAt: string;
}

export interface GuidanceResponse {
  items: HoldingGuidance[];
  asOf: string | null;
}

export interface GuidanceRequest {
  status: GuidanceStatus;
  reasoning: string | null;
  expectedVersion: number | null;
}

export interface AdvisorInstrument {
  instrumentId: number;
  ticker: string;
  instrumentName: string;
  currency: string;
  isActive: boolean;
}

export interface Recommendation {
  recommendationId: number;
  instrumentId: number;
  ticker: string;
  instrumentName: string;
  reasoning: string;
  version: number;
  updatedAt: string;
}

export interface RecommendationRequest {
  instrumentId: number;
  reasoning: string;
}

export interface RecommendationUpdateRequest {
  reasoning: string;
  expectedVersion: number;
}

export interface AdvisorSaveState {
  saving: boolean;
  error: string;
  status: number;
}
