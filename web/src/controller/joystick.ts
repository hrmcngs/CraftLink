/** Digital movement intent from a normalized stick, with a neutral dead zone. */
export function joystickDirections(x: number, y: number): string[] {
  if (!Number.isFinite(x) || !Number.isFinite(y) || Math.hypot(x, y) < 0.25) return [];
  const held: string[] = [];
  if (y < -0.35) held.push('forward');
  if (y > 0.35) held.push('back');
  if (x < -0.35) held.push('left');
  if (x > 0.35) held.push('right');
  return held;
}
