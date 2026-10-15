package dev.craftlink.addon;

import dev.craftlink.config.LinkConfig;
import dev.craftlink.input.RemoteState;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

/** Client-only extension API. Callbacks run on the Minecraft thread after explicit PC opt-in. */
public final class AddonActions {
    public record Action(String id, String label) {}
    private record Handler(String label, Consumer<Boolean> callback) {}
    private static final Map<String, Handler> handlers = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> failed = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final ActionState state = new ActionState();
    private static Set<String> callbackHeld = Set.of();
    private static volatile long refreshAt;
    private static List<Action> availableCache = List.of(), enabledCache = List.of();
    private static List<? extends String> selectedCache = List.of();
    private static List<Action> enabledSource;
    private static volatile Map<KeyMapping, String> enabledKeys = Map.of();
    private static final Set<String> VANILLA = Set.of("key.attack", "key.use", "key.forward", "key.left", "key.back", "key.right", "key.jump", "key.sneak", "key.sprint", "key.drop", "key.inventory", "key.chat", "key.playerlist", "key.pickItem", "key.command", "key.socialInteractions", "key.screenshot", "key.togglePerspective", "key.smoothCamera", "key.fullscreen", "key.spectatorOutlines", "key.swapOffhand", "key.saveToolbarActivator", "key.loadToolbarActivator", "key.advancements");
    private AddonActions() {}
    /** Register during client setup, e.g. register("example:ability", "Ability", down -> ...). */
    public static void register(String id, String label, Consumer<Boolean> callback) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 120 || label.isBlank() || label.length() > 80) throw new IllegalArgumentException("Invalid addon action");
        Objects.requireNonNull(callback);
        synchronized (handlers) {
            if (handlers.size() >= 128 || handlers.putIfAbsent("addon:" + id, new Handler(label, callback)) != null) throw new IllegalArgumentException("Duplicate or too many actions");
            refreshAt = 0;
        }
    }
    public static boolean validId(Object value) { return value instanceof String s && s.length() <= 180 && s.matches("addon:[A-Za-z0-9_.:/-]+"); }
    public static boolean eligible(KeyMapping key) {
        return !VANILLA.contains(key.getName()) && !key.getName().matches("key.hotbar.[1-9]") && !key.getName().startsWith("key.craftlink.") && validId(keyId(key));
    }
    public static String keyId(KeyMapping key) { return "addon:key:" + key.getName(); }
    public static List<Action> available(Minecraft mc) {
        if (System.nanoTime() < refreshAt) return availableCache;
        var actions = new TreeMap<String, Action>();
        for (KeyMapping key : mc.options.keyMappings) if (eligible(key)) {
            String label = Component.translatable(key.getName()).getString();
            actions.put(keyId(key), new Action(keyId(key), label.substring(0, Math.min(80, label.length()))));
        }
        handlers.forEach((id, handler) -> { if (!failed.contains(id)) actions.put(id, new Action(id, handler.label)); });
        availableCache = List.copyOf(actions.values()); refreshAt = System.nanoTime() + 1_000_000_000L;
        return availableCache;
    }
    public static List<Action> enabled(Minecraft mc) {
        var selected = LinkConfig.ADDON_ACTIONS.get();
        var source = available(mc);
        if (source != enabledSource || !selected.equals(selectedCache)) {
            enabledSource = source; selectedCache = List.copyOf(selected);
            enabledCache = source.stream().filter(action -> selected.contains(action.id)).limit(16).toList();
            var keys = new IdentityHashMap<KeyMapping, String>();
            Set<String> ids = new HashSet<>(); for (Action action : enabledCache) ids.add(action.id);
            for (KeyMapping key : mc.options.keyMappings) { String id = keyId(key); if (ids.contains(id)) keys.put(key, id); }
            enabledKeys = Collections.unmodifiableMap(keys);
        }
        return enabledCache;
    }
    public static void tick(Minecraft mc, RemoteState.Snapshot snapshot, boolean active) {
        Set<String> allowed = new HashSet<>();
        for (Action action : enabled(mc)) allowed.add(action.id);
        state.update(active ? snapshot.held() : Set.of(), active ? snapshot.presses() : Set.of(), allowed);
        Set<String> next = new HashSet<>();
        handlers.forEach((id, handler) -> {
            if (failed.contains(id)) return;
            boolean down = active && allowed.contains(id) && snapshot.held().contains(id);
            boolean pressed = active && allowed.contains(id) && snapshot.presses().contains(id);
            try {
                if (down && !callbackHeld.contains(id) || pressed && !callbackHeld.contains(id)) handler.callback.accept(true);
                if (!down && (callbackHeld.contains(id) || pressed)) handler.callback.accept(false);
                if (down && !failed.contains(id)) next.add(id);
            } catch (RuntimeException failure) {
                if (failed.add(id)) LoggerFactory.getLogger("CraftLink").warn("Addon action disabled after callback failure: {}", id, failure);
                try { handler.callback.accept(false); } catch (RuntimeException ignored) { /* already reported */ }
                refreshAt = 0;
            }
        });
        callbackHeld = Set.copyOf(next);
    }
    public static boolean held(KeyMapping key) { String id = enabledKeys.get(key); return id != null && state.held(id); }
    public static boolean consume(KeyMapping key) { String id = enabledKeys.get(key); return id != null && state.consume(id); }
    public static void releaseAll() {
        state.clear();
        for (String id : callbackHeld) {
            var handler = handlers.get(id);
            if (handler != null) try { handler.callback.accept(false); }
            catch (RuntimeException failure) { LoggerFactory.getLogger("CraftLink").warn("Addon release failed: {}", id, failure); }
        }
        callbackHeld = Set.of();
    }
}
