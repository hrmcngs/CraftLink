package dev.craftlink.input;

import java.util.Set;

/** Per-tick remote button state, with rising edges independent of PC input. */
public final class RemoteButtons {
    private Set<String> held = Set.of(), previous = Set.of();
    private Set<String> queued = Set.of();
    public void update(Set<String> next) { update(next, Set.of()); }
    public void update(Set<String> next, Set<String> edges) { previous = held; held = Set.copyOf(next); queued = Set.copyOf(edges); }
    public boolean held(String key) { return held.contains(key); }
    public boolean pressed(String key) { return queued.contains(key) || held.contains(key) && !previous.contains(key); }
    public Set<String> held() { return held; }
    public void clear() { held = previous = queued = Set.of(); }
}
