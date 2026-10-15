package dev.craftlink.classroom;

import com.google.gson.JsonObject;
import dev.craftlink.input.RemoteState;
import dev.craftlink.security.Session;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded room; networking exchanges immutable input, never Minecraft entities. */
public final class ClassroomRoom {
    public static final int LIMIT = 8;
    public static final Set<String> BUTTONS = Set.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "use");
    public interface Socket { void send(String json); void close(); }
    public record View(UUID id, int number, boolean connected, boolean approved) {}
    public record BotInput(int number, RemoteState.Snapshot input) {}
    private static final long RECONNECT_NS = 30_000_000_000L;
    private final Session invitation = new Session();
    private final Map<UUID, Peer> peers = new LinkedHashMap<>();
    private final LongSupplier clock;
    private boolean paused = true, closed;
    private static final class Peer {
        final UUID id = UUID.randomUUID();
        final int number;
        final Session resume = new Session();
        final RemoteState input = new RemoteState();
        Socket socket;
        boolean approved;
        long disconnectedAt;
        Peer(int number, Socket socket) { this.number = number; this.socket = socket; }
    }
    public ClassroomRoom() { this(System::nanoTime); }
    public ClassroomRoom(LongSupplier clock) { this.clock = clock; }
    public String token() { return invitation.token(); }
    public synchronized boolean expired() { return closed || invitation.expired(); }
    public synchronized boolean paused() { return paused; }
    public synchronized UUID join(String token, String resume, Socket socket) {
        if (expired() || !invitation.accepts(token)) throw new IllegalArgumentException("Invalid invitation");
        prune();
        Peer peer = null;
        if (resume != null && !resume.isEmpty()) {
            for (Peer candidate : peers.values()) if (candidate.resume.accepts(resume)) { peer = candidate; break; }
            if (peer != null && peer.socket != null) throw new IllegalArgumentException("Active resume token");
            if (peer != null) peer.socket = socket;
        }
        if (peer == null) {
            if (peers.size() >= LIMIT) throw new IllegalArgumentException("Room full");
            int number = 1;
            for (; number <= LIMIT; number++) { final int slot = number; if (peers.values().stream().noneMatch(p -> p.number == slot)) break; }
            peer = new Peer(number, socket); peers.put(peer.id, peer);
        }
        peer.input.clear();
        var welcome = new JsonObject(); welcome.addProperty("type", "welcome"); welcome.addProperty("number", peer.number);
        welcome.addProperty("resume", peer.resume.token()); socket.send(welcome.toString()); sendState(peer);
        return peer.id;
    }
    public synchronized void disconnect(UUID id, Socket socket) {
        Peer peer = peers.get(id);
        if (peer != null && peer.socket == socket) { peer.socket = null; peer.disconnectedAt = clock.getAsLong(); peer.input.clear(); }
    }
    public synchronized void approve(UUID id) {
        Peer peer = peers.get(id); if (peer == null || peer.socket == null || expired()) return;
        peer.approved = true; peer.input.clear(); sendState(peer);
    }
    public synchronized void remove(UUID id) {
        Peer peer = peers.remove(id);
        if (peer != null) { peer.input.clear(); if (peer.socket != null) { peer.socket.send("{\"type\":\"revoked\"}"); peer.socket.close(); } }
    }
    public synchronized void pause(boolean value) {
        paused = value;
        for (Peer peer : peers.values()) { peer.input.clear(); sendState(peer); }
    }
    public synchronized void input(UUID id, Socket socket, Set<String> held, double yaw) {
        if (!BUTTONS.containsAll(held) || !Double.isFinite(yaw) || Math.abs(yaw) > 180) throw new IllegalArgumentException("Invalid input");
        Peer peer = peers.get(id);
        if (peer != null && peer.socket == socket && peer.approved && !paused && !expired()) peer.input.update(held, yaw, 0, -1);
    }
    public synchronized List<View> views() {
        prune(); return peers.values().stream().map(p -> new View(p.id, p.number, p.socket != null, p.approved)).toList();
    }
    public synchronized Map<UUID, BotInput> takeInputs() {
        if (expired()) { close(); return Map.of(); }
        prune();
        Map<UUID, BotInput> inputs = new LinkedHashMap<>();
        for (Peer peer : peers.values()) if (peer.approved) {
            if (paused || peer.socket == null) peer.input.clear();
            inputs.put(peer.id, new BotInput(peer.number, peer.input.take()));
        }
        return Map.copyOf(inputs);
    }
    public synchronized void close() { closed = true; for (UUID id : List.copyOf(peers.keySet())) remove(id); }
    private void prune() {
        long now = clock.getAsLong();
        for (Peer peer : List.copyOf(peers.values())) if (peer.socket == null && now - peer.disconnectedAt >= RECONNECT_NS) remove(peer.id);
    }
    private void sendState(Peer peer) {
        if (peer.socket == null) return;
        var message = new JsonObject(); message.addProperty("type", "state"); message.addProperty("approved", peer.approved); message.addProperty("paused", paused);
        peer.socket.send(message.toString());
    }
}
