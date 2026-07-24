// src/lib/quota-utils.ts
// Pure utility functions shared between DesktopWidget and MobileApp

/**
 * Format an email address with optional masking.
 * Masking shows only the first 2 characters of the local part.
 */
export function formatEmail(email: string | undefined, mask: boolean): string {
  if (!email) return '';
  if (!mask) return email;
  const parts = email.split('@');
  if (parts.length !== 2) return email;
  const name = parts[0];
  const domain = parts[1];
  if (name.length <= 2) return `${name}***@${domain}`;
  return `${name.slice(0, 2)}***@${domain}`;
}

/**
 * Format a reset time string into a human-readable countdown.
 * Returns empty string if the reset time has already passed.
 */
export function formatResetTime(raw: string | null): string {
  if (!raw) return '';
  const d = new Date(raw);
  if (!isNaN(d.getTime())) {
    const diffMs = d.getTime() - Date.now();
    if (diffMs <= 0) return '';
    const totalMins = Math.floor(diffMs / 60_000);
    const h = Math.floor(totalMins / 60);
    const m = totalMins % 60;
    if (h > 0 && m > 0) return `${h}h ${m}m`;
    if (h > 0) return `${h}h`;
    return `${m}m`;
  }
  return raw;
}

/**
 * Determine the CSS color for the quota progress bar.
 * @param fraction - The remaining quota fraction (0–1)
 * @param isOffline - Whether the widget is in offline mode
 * @param poolsEmpty - Whether the pools array is empty
 */
export function barColor(
  fraction: number,
  isOffline: boolean,
  poolsEmpty: boolean
): string {
  if (isOffline && poolsEmpty) return 'var(--color-bar-offline)';
  if (fraction <= 0.2) return 'var(--color-bar-low)';
  return 'var(--color-accent)';
}
