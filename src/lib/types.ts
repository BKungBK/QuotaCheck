// src/lib/types.ts
// Shared TypeScript interfaces used by DesktopWidget, MobileApp, and settings

export interface QuotaPool {
  label: string;
  remaining_fraction: number;
  reset_time: string | null;
}

export interface Cache {
  pools: QuotaPool[];
  last_updated: string;
  is_offline: boolean;
  error_reason?: string;
  source: string;
  account_email?: string;
}

/** Superset Config — covers both Desktop and Mobile/Settings fields */
export interface Config {
  refresh_token_override?: string;
  antigravity_config_path?: string;
  monitor_index?: number;
  offset_x?: number;
  offset_y?: number;
  position_corner?: string;
  reset_time_utc?: string;
  autostart?: boolean;
  preferred_account?: string;
  mask_account_email?: boolean;
  quota_source_mode?: string;
  display_mode?: string;
}
