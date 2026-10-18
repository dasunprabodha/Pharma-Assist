/** Small display formatters shared by pages. */
export function seconds(ms: number | null | undefined): string {
  return ms == null ? '–' : `${(ms / 1000).toFixed(1)} s`;
}

export function percent(rate: number | null | undefined): string {
  return rate == null ? '–' : `${Math.round(rate * 100)}%`;
}

export function usd(value: number | null | undefined, digits = 4): string {
  if (value == null) return '–';
  if (value === 0) return '$0';
  return value < 0.01 ? `$${value.toFixed(digits)}` : `$${value.toFixed(2)}`;
}

export function compact(n: number | null | undefined): string {
  if (n == null) return '–';
  return new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(n);
}

export function dateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}
