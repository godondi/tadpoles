import { GuidanceStatus } from '../../advisor/advisor-models';

/** Proposed session-owned client contract; not a legacy trade-suggestion response. */
export interface ClientAssignment {
  assignmentStatus: 'ASSIGNED' | 'PENDING' | string;
  advisor: { advisorId: number; name: string } | null;
  assignedAt: string | null;
}

export interface ClientGuidance {
  instrumentId: number;
  status: GuidanceStatus;
  reasoning: string | null;
  authorName: string | null;
  updatedAt: string;
}

export interface ClientRecommendation {
  recommendationId: number;
  instrumentId: number;
  ticker: string;
  instrumentName: string;
  reasoning: string;
  updatedAt: string;
}

export interface ClientAdvisory {
  guidance: ClientGuidance[];
  recommendations: ClientRecommendation[];
  asOf: string | null;
  source: string | null;
}
