/**
 * Badge Utilities for Consistent UI Styling
 *
 * This module provides standardized badge variant selection
 * to ensure consistency across all components.
 */

import type { BadgeProps } from '@/components/ui/badge';

/**
 * Get badge variant for loader enabled status
 * @param enabled - Whether the loader is enabled
 * @returns Badge variant ('success' for enabled, 'destructive' for disabled)
 */
export function getLoaderStatusVariant(enabled: boolean): BadgeProps['variant'] {
  return enabled ? 'success' : 'destructive';
}

/**
 * Get badge text for loader enabled status
 * @param enabled - Whether the loader is enabled
 * @returns Display text ('ENABLED' or 'DISABLED')
 */
export function getLoaderStatusText(enabled: boolean): string {
  return enabled ? 'ENABLED' : 'DISABLED';
}

/**
 * Get badge variant for consecutive zero record runs
 * Color-coded by severity:
 * - 0-2 runs: secondary (gray) - normal
 * - 3-4 runs: default (orange) - warning
 * - 5+ runs: destructive (red) - critical
 *
 * @param count - Number of consecutive zero record runs
 * @returns Badge variant based on severity
 */
export function getZeroRecordRunsVariant(count: number): BadgeProps['variant'] {
  if (count >= 5) return 'destructive';
  if (count >= 3) return 'default';
  return 'secondary';
}

/**
 * Badge Color Standards
 *
 * - success (green): Positive status, enabled, healthy
 * - destructive (red): Negative status, disabled, critical
 * - default (orange/amber): Warning, moderate severity
 * - secondary (gray): Neutral, informational
 * - outline: Secondary actions, less prominent
 */
export const BadgeColorStandards = {
  ENABLED: 'success',
  DISABLED: 'destructive',
  CRITICAL: 'destructive',
  WARNING: 'default',
  NORMAL: 'secondary',
  INFO: 'outline',
} as const;
