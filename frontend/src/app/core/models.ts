/** Shared prescription item (CLAUDE.md section 4) - same snake_case fields as the backend. */
export type Confidence = 'high' | 'medium' | 'low';

export interface Item {
  drug_name: string | null;
  strength: string | null;
  dose: string | null;
  frequency: string | null;
  duration: string | null;
  confidence: Confidence;
  note: string | null;
}

export const ITEM_FIELDS = ['drug_name', 'strength', 'dose', 'frequency', 'duration'] as const;
export type ItemField = (typeof ITEM_FIELDS)[number];

export interface ScanResult {
  scan_id: string;
  model_id: string;
  pages: number;
  items: Item[];
  low_confidence_rows: number[];
  input_tokens: number;
  output_tokens: number;
  latency_ms: number;
  estimated_cost_usd: number;
}

export type ErrorType =
  | 'config_error'
  | 'invalid_image'
  | 'ai_unavailable'
  | 'unreadable_image'
  | 'invalid_response'
  | 'validation'
  | 'network';

export interface ApiError {
  error_type: ErrorType;
  message: string;
  detail?: string | null;
  fallback_to_manual?: boolean;
  rows?: number[];
}

export type Source = 'ai' | 'manual';

export interface ConfirmedPrescription {
  id: string;
  confirmed_at: string;
  source: Source;
  scan_id: string | null;
  items: Item[];
  corrections: number;
}

export interface ConfigStatus {
  credentials_found: boolean;
  profile: string | null;
  region: string;
  region_set: boolean;
  model_id: string;
  model_id_set: boolean;
  stock_items: number;
  interaction_rules: number;
}

export interface BedrockCheck {
  ok: boolean;
  model_id: string;
  reply?: string;
  error_type?: string;
  message?: string;
  latency_ms: number;
}

export interface ScanRecord {
  timestamp: string;
  scan_id: string;
  model_id: string;
  outcome: string;
  item_count: number;
  low_confidence_count: number;
  input_tokens: number;
  output_tokens: number;
  latency_ms: number;
}

export interface Dashboard {
  scans_total: number;
  scans_success: number;
  success_rate: number | null;
  avg_latency_ms: number | null;
  p95_latency_ms: number | null;
  within_target_rate: number | null;
  target_ms: number;
  input_tokens: number;
  output_tokens: number;
  cost_total_usd: number;
  cost_per_scan_usd: number | null;
  cost_per_1000_scans_usd: number | null;
  price_input_per_1m: number;
  price_output_per_1m: number;
  outcomes: Record<string, number>;
  scans_per_day: { date: string; success: number; failed: number }[];
  confirmed_total: number;
  confirmed_ai: number;
  confirmed_manual: number;
  items_confirmed: number;
  corrections_total: number;
  corrections_per_ai_list: number | null;
  ai_lists_unchanged_rate: number | null;
  items_extracted: number;
  items_low_confidence: number;
  confidence_confirmed: Partial<Record<Confidence, number>>;
  top_drugs: { name: string; count: number }[];
  recent_scans: ScanRecord[];
}

export type StockRow = Record<
  'item_id' | 'brand_name' | 'generic_name' | 'strength' | 'dosage_form' | 'therapeutic_class' | 'in_stock' | 'quantity_on_hand',
  string
>;
export type RuleRow = Record<'rule_id' | 'rule_type' | 'item_a' | 'item_b' | 'severity' | 'warning_message', string>;

/** Human labels for scan outcomes (error_type codes from the backend). */
export const OUTCOME_LABELS: Record<string, string> = {
  success: 'Read successfully',
  unreadable_image: 'Photo unreadable',
  invalid_image: 'Invalid file',
  ai_unavailable: 'AI unavailable',
  config_error: 'AI not configured',
  invalid_response: 'Unexpected AI result',
  error: 'Other error'
};
