package dev.craftlink.addon;
import java.util.*;
/** Independent, bounded per-tick input; never changes a physical key's click queue. */
public final class ActionState {
    private Set<String> held = Set.of();
    private final Set<String> clicks = new HashSet<>();
    public synchronized void update(Set<String> next, Set<String> edges, Set<String> allowed) {
        clicks.clear();
        for (String id : allowed) if (edges.contains(id) || next.contains(id) && !held.contains(id)) clicks.add(id);
        var active = new HashSet<>(next); active.retainAll(allowed); held = Set.copyOf(active);
    }
    public synchronized boolean held(String id) { return held.contains(id); }
    public synchronized boolean consume(String id) { return clicks.remove(id); }
    public synchronized void clear() { held = Set.of(); clicks.clear(); }
}
