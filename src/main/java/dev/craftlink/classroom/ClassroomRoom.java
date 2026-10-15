package dev.craftlink.classroom;

import com.google.gson.JsonObject;
import dev.craftlink.input.RemoteState;
import dev.craftlink.security.Session;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded room; networking exchanges immutable input, never Minecraft entities. */
public final class ClassroomRoom {
    public static final int LIMIT = 8;
    public static final Set<String> BUTTONS = Set.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "use", "attack");
    public enum Mode { SURVIVAL, CREATIVE }
    private Mode mode=Mode.SURVIVAL;
    public synchronized Mode mode(){return mode;}
    /** Called by the native host screen only; never exposed as a phone command. */
    public synchronized void mode(Mode value){
        mode=Objects.requireNonNull(value);
        for(Peer peer:peers.values()){peer.input.clear();peer.creative=null;peer.aim=null;peer.swapSlot=-1;peer.inventoryRequested=true;sendState(peer);}
    }
    public interface Socket { void send(String json); void close(); default boolean sendVideo(byte[] packet) { return false; } }
    public record View(UUID id, int number, boolean connected, boolean approved) {}
    public record VideoTarget(UUID id, Socket socket, dev.craftlink.video.BotVideoQuality quality) {}
    public record BotInput(int number, RemoteState.Snapshot input, boolean inventoryRequested, int selectedSlot, int swapSlot, CreativeRequest creative, Aim aim) {}
    public record ViewSample(double eyeX,double eyeY,double eyeZ,double yaw,double pitch,double aspect) {}
    public record Touch(int frame,double x,double y) {
        public Touch {if(frame<1||!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>1||y<0||y>1)throw new IllegalArgumentException("Invalid touch");}
    }
    public record Aim(ViewSample view,double x,double y) {}
    private record TimedView(ViewSample sample,long at) {}
    private static final long RECONNECT_NS = 30_000_000_000L;
    public record CreativeRequest(String tab,int page,int item) {}
    private volatile CreativeCatalog catalog;
    public void catalog(CreativeCatalog value){catalog=value;}
    public CreativeCatalog catalog(){return catalog;}
    private final Session invitation = new Session();
    private final Map<UUID, Peer> peers = new LinkedHashMap<>();
    private final LongSupplier clock;
    private boolean paused, closed;
    private volatile String videoStatus = "";
    public void videoStatus(String message) { videoStatus = message; }
    public String videoStatus() { return videoStatus; }
    private static final class Peer {
        final UUID id = UUID.randomUUID();
        final int number;
        final Session resume = new Session();
        final RemoteState input = new RemoteState();
        Socket socket;
        boolean approved, video, inventoryRequested;
        int selectedSlot;
        int swapSlot=-1;
        CreativeRequest creative;
        Aim aim;
        final LinkedHashMap<Integer,TimedView> frameViews=new LinkedHashMap<>();
        final Map<Integer, Long> awaitingFrames = new LinkedHashMap<>();
        dev.craftlink.video.BotVideoQuality quality = dev.craftlink.video.BotVideoQuality.BALANCED;
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
        peer.inventoryRequested = true; peer.approved = true; peer.video = false; peer.awaitingFrames.clear();
        peer.input.clear();peer.aim=null;peer.frameViews.clear();
        var welcome = new JsonObject(); welcome.addProperty("type", "welcome"); welcome.addProperty("number", peer.number);
        welcome.addProperty("resume", peer.resume.token()); socket.send(welcome.toString()); sendState(peer);
        return peer.id;
    }
    public synchronized void disconnect(UUID id, Socket socket) {
        Peer peer = peers.get(id);
        if (peer != null && peer.socket == socket) { peer.socket = null; peer.video = false; peer.awaitingFrames.clear();peer.frameViews.clear();peer.aim=null; peer.disconnectedAt = clock.getAsLong(); peer.swapSlot=-1;peer.creative=null; peer.input.clear(); }
    }
    public synchronized void approve(UUID id) {
        Peer peer = peers.get(id); if (peer == null || peer.socket == null || expired()) return;
        peer.approved = true; peer.input.clear(); sendState(peer);
    }
    /** Explicit host action: approve connected participants and start the room together. */
    public synchronized int approveConnectedAndStart() {
        if (expired()) return 0;
        int count = 0;
        for (Peer peer : peers.values()) if (peer.socket != null) {
            peer.approved = true; peer.input.clear(); count++;
        }
        if (count > 0) pause(false);
        return count;
    }
    public synchronized void remove(UUID id) {
        Peer peer = peers.remove(id);
        if (peer != null) { peer.input.clear(); if (peer.socket != null) { peer.socket.send("{\"type\":\"revoked\"}"); peer.socket.close(); } }
    }
    public synchronized void pause(boolean value) {
        paused = value;
        for (Peer peer : peers.values()) { peer.swapSlot=-1;peer.creative=null;peer.aim=null; peer.input.clear(); sendState(peer); }
    }
    public synchronized void input(UUID id, Socket socket, Set<String> held, double yaw) {
        input(id, socket, held, yaw, 0);
    }
    public synchronized void input(UUID id, Socket socket, Set<String> held, double yaw, double pitch) {
        input(id,socket,held,yaw,pitch,null);
    }
    public synchronized void input(UUID id, Socket socket, Set<String> held, double yaw, double pitch,Touch touch) {
        if (!Double.isFinite(pitch) || Math.abs(pitch) > 180) throw new IllegalArgumentException("Invalid pitch");
        if (!BUTTONS.containsAll(held) || !Double.isFinite(yaw) || Math.abs(yaw) > 180) throw new IllegalArgumentException("Invalid input");
        Peer peer = peers.get(id);
        if (peer != null && peer.socket == socket && peer.approved && !paused && !expired()) {
            if(touch!=null){
                var view=peer.frameViews.get(touch.frame());
                if(view==null || clock.getAsLong()-view.at()>2_000_000_000L){peer.input.clear();peer.aim=null;return;}
                peer.aim=new Aim(view.sample(),touch.x(),touch.y());
            }else if(held.contains("attack")||held.contains("use"))peer.aim=null;
            peer.input.update(held, yaw, pitch, -1);
        }
    }
    public synchronized void video(UUID id, Socket socket, boolean enabled) { video(id,socket,enabled,dev.craftlink.video.BotVideoQuality.BALANCED); }
    public synchronized void video(UUID id, Socket socket, boolean enabled, dev.craftlink.video.BotVideoQuality quality) {
        Peer peer = peers.get(id);
        if (peer != null && peer.socket == socket && peer.approved) { if (peer.video != enabled || peer.quality != quality) peer.awaitingFrames.clear(); peer.video = enabled; peer.quality = quality; }
    }
    public synchronized void frameAck(UUID id, Socket socket, int frame) {
        Peer peer = peers.get(id);
        if (peer != null && peer.socket == socket && peer.awaitingFrames.containsKey(frame)) peer.awaitingFrames.remove(frame);
    }
    public synchronized List<VideoTarget> videoTargets() {
        if (expired()) return List.of();
        long now = clock.getAsLong();
        for (Peer peer : peers.values()) peer.awaitingFrames.entrySet().removeIf(e -> now-e.getValue()>2_000_000_000L);
        return peers.values().stream().filter(p -> p.approved && p.video && p.socket != null && p.awaitingFrames.size()<2)
            .map(p -> new VideoTarget(p.id, p.socket, p.quality)).toList();
    }
    public synchronized void publishVideo(VideoTarget target, int frame, byte[] packet) {
        publishVideo(target,frame,packet,null);
    }
    public synchronized void publishVideo(VideoTarget target, int frame, byte[] packet,ViewSample view) {
        Peer peer = peers.get(target.id());
        if (!expired() && peer != null && peer.socket == target.socket() && peer.approved && peer.video && peer.quality == target.quality() && peer.awaitingFrames.size()<2 && peer.socket.sendVideo(packet)) {
            peer.awaitingFrames.put(frame,clock.getAsLong());
            if(view!=null){peer.frameViews.put(frame,new TimedView(view,clock.getAsLong()));while(peer.frameViews.size()>16)peer.frameViews.remove(peer.frameViews.keySet().iterator().next());}
        }
    }
    public synchronized void inventory(UUID id, Socket socket, int slot) {
        if (slot < -1 || slot > 35) throw new IllegalArgumentException("Invalid inventory slot");
        Peer peer=peers.get(id);
        if (peer==null || peer.socket!=socket || !peer.approved || expired()) return;
        peer.inventoryRequested=true;
        if(!paused){if(slot>=0 && slot<=8)peer.selectedSlot=slot;else if(slot>=9)peer.swapSlot=slot;}
    }
    public synchronized void creative(UUID id,Socket socket,String tab,int page,int item){
        if(tab==null || tab.length()>160 || !tab.matches("[a-zA-Z0-9_:./-]*") || page<0 || page>600 || item< -1 || item>=25000)throw new IllegalArgumentException("Invalid creative request");
        Peer peer=peers.get(id);if(peer!=null && peer.socket==socket && peer.approved && !paused && !expired())peer.creative=new CreativeRequest(tab,page,item);
    }
    public synchronized void publishInventory(UUID id, JsonObject inventory) {
        Peer peer=peers.get(id);if(!expired() && peer!=null && peer.socket!=null)peer.socket.send(inventory.toString());
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
            var snapshot=peer.input.take();
            inputs.put(peer.id, new BotInput(peer.number, snapshot, peer.inventoryRequested, peer.selectedSlot, paused || peer.socket==null ? -1 : peer.swapSlot, paused || peer.socket==null ? null : peer.creative,paused||peer.socket==null?null:peer.aim));
            if(!snapshot.held().contains("attack")&&!snapshot.held().contains("use"))peer.aim=null;
            peer.inventoryRequested=false; peer.swapSlot=-1;peer.creative=null;
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
        message.addProperty("mode",mode.name().toLowerCase(java.util.Locale.ROOT));
        peer.socket.send(message.toString());
    }
}
