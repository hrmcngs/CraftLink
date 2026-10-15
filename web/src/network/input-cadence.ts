/** Deduplicate state refreshes, never accumulated look deltas or one-shot slots. */
export class InputCadence {
  private previous = '';
  private sentAt = -Infinity;
  allow(serialized: string, now: number, transient: boolean) {
    if (!transient && serialized === this.previous && now - this.sentAt < 250) return false;
    this.previous = serialized;
    this.sentAt = now;
    return true;
  }
}
