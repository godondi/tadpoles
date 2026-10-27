export type ComplianceEvidenceStatus = 'PASS' | 'FAIL' | 'NOT_EVALUATED' | 'UNKNOWN';

export interface ComplianceAccessPolicy {
  configured: boolean;
  purposes: string[];
  scopeLabel: string;
  policyVersion: string;
}

export interface ComplianceContext {
  contextId: string;
  purposeCode: string;
  caseReference: string;
  scopeLabel: string;
  permissions: string[];
  expiresAt: string;
  policyVersion: string;
}

export interface ComplianceAccessContextRequest {
  purposeCode: string;
  caseReference: string;
  referenceType?: 'EXTERNAL' | 'MANAGED';
}

export interface ComplianceSearchFilters {
  orderId?: number;
  from?: string;
  to?: string;
  clientReference?: string;
  instrument?: string;
  status?: string;
  page: number;
  pageSize: number;
}

export interface ComplianceOrderSummary {
  orderId: number;
  clientReference: string;
  instrument: string;
  side: string;
  quantity: string;
  unit: string;
  status: string;
  submittedAt: string | null;
  acceptedAt: string | null;
  outcomeAt: string | null;
  executionMode: string;
  recordKind: string;
  evidenceCompleteness: ComplianceEvidenceStatus;
}

export interface ComplianceSearchPage {
  items: ComplianceOrderSummary[];
  page: number;
  pageSize: number;
  total: number;
  scope: string;
  asOf: string;
}

export interface ComplianceEvidenceRecord {
  reference: string;
  sequence: number | null;
  actor: string | null;
  occurredAt: string | null;
  values: Record<string, string | number | boolean | null>;
}

export interface ComplianceEvidenceSection {
  name: string;
  items: ComplianceEvidenceRecord[];
  page: number;
  pageSize: number;
  total: number;
  hasMore: boolean;
  coverage: string;
  source: string;
  asOf: string;
  completeness: ComplianceEvidenceStatus;
}

export interface ComplianceInvestigationDetail {
  summary: ComplianceOrderSummary;
  authenticatedActor: string;
  accountReference: string;
  acceptedPolicy: Record<string, unknown> | null;
  currentPolicy: Record<string, unknown> | null;
  snapshotAt: string;
  sections: ComplianceEvidenceSection[];
  findings: { code: string; explanation: string; status: ComplianceEvidenceStatus }[];
  exportAllowed: boolean;
  limitations: string[];
}

export type ComplianceExportState = 'REQUESTED' | 'READY' | 'FAILED' | 'EXPIRED' | 'DENIED';

export interface ComplianceManifest {
  schemaVersion: string;
  sourceSnapshot: string;
  sectionCounts: Record<string, number>;
  contentDigest: string;
  signatureStatus: 'NOT_SUPPLIED' | 'SUPPLIED' | 'FAILED';
  anchorStatus: 'NOT_SUPPLIED' | 'SUPPLIED' | 'FAILED';
  verification: ComplianceEvidenceStatus;
}

export interface ComplianceExportRecord {
  requestReference: string;
  scope: string;
  actor: string | null;
  createdAt: string;
  asOf: string;
  purposeReference: string;
  state: ComplianceExportState;
  expiresAt: string | null;
  manifest: ComplianceManifest | null;
  authorizedActions: ('DOWNLOAD' | 'RECOVER_RECEIPT')[];
}

export interface ComplianceExportPage {
  items: ComplianceExportRecord[];
  page: number;
  pageSize: number;
  total: number;
  scope: string;
  asOf: string;
}
