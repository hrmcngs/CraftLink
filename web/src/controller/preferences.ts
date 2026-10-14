export interface Preferences { layout: 'tap' | 'mobile' | 'classic_tap' | 'classic'; sensitivity: number; buttonSize: number }
export const defaults: Preferences = {layout:'tap', sensitivity:0.15, buttonSize:100};
export function normalizePreferences(value: unknown): Preferences {
  const data = typeof value === 'object' && value !== null ? value as Record<string, unknown> : {};
  return {
    layout: data.layout === 'mobile' || data.layout === 'classic' || data.layout === 'classic_tap' ? data.layout : 'tap',
    sensitivity: typeof data.sensitivity === 'number' && Number.isFinite(data.sensitivity) && data.sensitivity >= 0.03 && data.sensitivity <= 0.5 ? data.sensitivity : defaults.sensitivity,
    buttonSize: typeof data.buttonSize === 'number' && Number.isInteger(data.buttonSize) && data.buttonSize >= 80 && data.buttonSize <= 140 ? data.buttonSize : defaults.buttonSize
  };
}
