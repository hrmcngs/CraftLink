package dev.craftlink.classroom;

import com.google.gson.JsonObject;
import dev.craftlink.input.RemoteState;
import dev.craftlink.security.Session;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded room; networking exchanges immutable input, never Minecraft entities. */
public final class ClassroomRoom {
    public static final int LIMIT = 8;
    public static final Set<String> BUTTONS = Set.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "use", "attack", "tap");
    public enum Mode { SURVIVAL, CREATIVE }
    private Mode mode=Mode.SURVIVAL;
    public synchronized Mode mode(){return mode;}
    /** Host screen or local host command; never exposed through participant sockets. */
    public synchronized void mode(Mode value){
        mode=Objects.requireNonNull(value);
        for(Peer peer:peers.values()){peer.modeOverride=null;resetMode(peer);}
    }
    public synchronized boolean mode(int number,Mode value){
        Objects.requireNonNull(value);prune();if(expired())return false;
        for(Peer peer:peers.values())if(peer.number==number){peer.modeOverride=value;resetMode(peer);return true;}
        return false;
    }
    private Mode mode(Peer peer){return peer.modeOverride==null?mode:peer.modeOverride;}
    private void resetMode(Peer peer){
        uiActions.remove(peer.id);
        peer.input.clear();peer.creative=null;peer.aim=null;peer.drop=null;peer.flying=false;peer.swapSlot=-1;peer.inventoryRequested=true;sendState(peer);
    }
    public interface Socket { void send(String json); void close(); default boolean sendVideo(byte[] packet) { return false; } }
    public record View(UUID id, int number, boolean connected, boolean approved,String name,String skin) {}
    public record VideoTarget(UUID id, Socket socket, dev.craftlink.video.BotVideoQuality quality) {}
    public record BotInput(int number, RemoteState.Snapshot input, boolean inventoryRequested, int selectedSlot, int swapSlot, CreativeRequest creative, Aim aim,DropRequest drop,boolean flying,boolean autoJump,ParticipantProfile profile, Mode mode, Move movement) {}
    public record Move(double x,double z){
        public Move{if(!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>1||Math.abs(z)>1)throw new IllegalArgumentException("Invalid movement");double length=Math.max(1,Math.hypot(x,z));x/=length;z/=length;}
    }
    public record DropRequest(int slot,boolean all,int count) {}
    public record UiAction(String kind,int menu,int slot,int button,String text) {}
    private final Map<UUID,ArrayDeque<UiAction>> uiActions=new HashMap<>();
    public synchronized void ui(UUID id,Socket socket,UiAction action){
        var peer=peers.get(id);
        if(peer==null||peer.socket!=socket||!peer.approved||paused&&!action.kind().equals("close")||expired())return;
        if(!Set.of("click","quick","collect","close","craft","openBlock","mawSkills","mawSelect","recipe","chat","skillStart","skillRelease","skillCancel","sheathe","backpack").contains(action.kind())||action.slot()< -999||action.slot()>255||action.button()<0||action.button()>1||action.text().length()>256)throw new IllegalArgumentException("Invalid menu action");
        var pending=uiActions.computeIfAbsent(id,key->new ArrayDeque<>());if(pending.size()<32)pending.add(action);
    }
    public synchronized List<UiAction> takeUi(UUID id){
        var pending=uiActions.remove(id);var peer=peers.get(id);
        return pending==null||peer==null||peer.socket==null||!peer.approved||expired()?List.of():pending.stream().filter(action->!paused||action.kind().equals("close")).toList();
    }
    public synchronized void chat(String text){var message=new JsonObject();message.addProperty("type","chat");message.addProperty("text",text);for(var peer:peers.values())if(peer.approved&&peer.socket!=null)peer.socket.send(message.toString());}
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
    private final dev.craftlink.skin.SkinRepository skinRepository;
    private boolean paused, closed;
    private volatile String videoStatus = "";
    private String skinCatalog;
    public synchronized void skins(JsonObject catalog){skinCatalog=catalog.toString();for(Peer peer:peers.values())if(peer.socket!=null)peer.socket.send(skinCatalog);}
    public void videoStatus(String message) { videoStatus = message; }
    public String videoStatus() { return videoStatus; }
    private static final class Peer {
        final UUID id = UUID.randomUUID();
        final int number;
        final Session resume = new Session();
        final RemoteState input = new RemoteState();
        ParticipantProfile profile=ParticipantProfile.DEFAULT;
        long lastSkinUpload;
        Socket socket;
        boolean approved, video, inventoryRequested;
        int selectedSlot;
        int swapSlot=-1;
        CreativeRequest creative;
        Aim aim;
        DropRequest drop;
        boolean flying,autoJump;
        Mode modeOverride;
        Move movement;
        final LinkedHashMap<Integer,TimedView> frameViews=new LinkedHashMap<>();
        final Map<Integer, Long> awaitingFrames = new LinkedHashMap<>();
        dev.craftlink.video.BotVideoQuality quality = dev.craftlink.video.BotVideoQuality.BALANCED;
        long disconnectedAt;
        Peer(int number, Socket socket) { this.number = number; this.socket = socket; }
    }
    public ClassroomRoom() { this(System::nanoTime); }
    public ClassroomRoom(LongSupplier clock) { this(clock,null); }
    ClassroomRoom(LongSupplier clock,dev.craftlink.skin.SkinRepository skins) { this.clock=clock;this.skinRepository=skins; }
    public String token() { return invitation.token(); }
    public synchronized boolean expired() { return closed || invitation.expired(); }
    public synchronized boolean paused() { return paused; }
    public synchronized boolean canAct(UUID id){var peer=peers.get(id);return peer!=null&&peer.socket!=null&&peer.approved&&!paused&&!expired();}
    public synchronized UUID join(String token, String resume, Socket socket) {
        return join(token,resume,socket,null,null);
    }
    public synchronized UUID join(String token,String resume,Socket socket,String name,String skin) {
        if (expired() || !invitation.accepts(token)) throw new IllegalArgumentException("Invalid invitation");
        var incoming=name==null&&skin==null?null:new ParticipantProfile(name,skin);
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
        if(incoming!=null)peer.profile=incoming;
        peer.input.clear();peer.aim=null;peer.frameViews.clear();
        var welcome = new JsonObject(); welcome.addProperty("type", "welcome"); welcome.addProperty("number", peer.number);
        welcome.addProperty("name",peer.profile.displayName(peer.number));welcome.addProperty("skin",peer.profile.skin());
        welcome.addProperty("resume", peer.resume.token()); socket.send(welcome.toString()); sendState(peer);if(skinCatalog!=null)socket.send(skinCatalog);
        return peer.id;
    }
    public synchronized void uploadSkin(UUID id,Socket socket,String image,boolean slim){
        var peer=peers.get(id);if(peer==null||peer.socket!=socket||!peer.approved||expired())return;
        long now=clock.getAsLong();if(peer.lastSkinUpload!=0&&now-peer.lastSkinUpload<5_000_000_000L){var error=new JsonObject();error.addProperty("type","skinUploadError");error.addProperty("message","次のスキン送信まで5秒待ってください。");socket.send(error.toString());return;}peer.lastSkinUpload=now;
        try{
            var skin=dev.craftlink.skin.SkinPng.fromData(image,slim);(skinRepository==null?dev.craftlink.skin.SkinRepository.global():skinRepository).store(skin);
            peer.profile=new ParticipantProfile(peer.profile.name(),skin.id());var message=new JsonObject();message.addProperty("type","skinUploaded");message.addProperty("number",peer.number);message.addProperty("name",peer.profile.displayName(peer.number));message.addProperty("skin",skin.id());message.addProperty("image",skin.data());message.addProperty("slim",skin.slim());socket.send(message.toString());
        }catch(java.io.IOException failure){var error=new JsonObject();error.addProperty("type","skinUploadError");error.addProperty("message","保存できませんでした。64×64のPNG（64KB以内）と、PCの保存先の空きを確認してください。");socket.send(error.toString());}
    }
    public synchronized void profile(UUID id,Socket socket,String name,String skin){
        var incoming=new ParticipantProfile(name,skin);Peer peer=peers.get(id);
        if(peer==null||peer.socket!=socket||!peer.approved||expired())return;
        peer.profile=incoming;var message=new JsonObject();message.addProperty("type","profile");message.addProperty("number",peer.number);
        message.addProperty("name",incoming.displayName(peer.number));message.addProperty("skin",incoming.skin());socket.send(message.toString());
    }
    public synchronized void disconnect(UUID id, Socket socket) {
        Peer peer = peers.get(id);
        if (peer != null && peer.socket == socket) { peer.socket = null;uiActions.remove(id); peer.video = false; peer.awaitingFrames.clear();peer.frameViews.clear();peer.aim=null;peer.drop=null; peer.disconnectedAt = clock.getAsLong(); peer.swapSlot=-1;peer.creative=null; peer.input.clear(); }
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
        uiActions.remove(id);Peer peer = peers.remove(id);
        if (peer != null) { peer.input.clear(); if (peer.socket != null) { peer.socket.send("{\"type\":\"revoked\"}"); peer.socket.close(); } }
    }
    public synchronized void pause(boolean value) {
        paused = value;uiActions.clear();
        for (Peer peer : peers.values()) { peer.swapSlot=-1;peer.creative=null;peer.aim=null;peer.drop=null; peer.input.clear(); sendState(peer); }
    }
    public synchronized void drop(UUID id,Socket socket,int slot,boolean all){
        drop(id,socket,slot,all,-1);
    }
    public synchronized void drop(UUID id,Socket socket,int slot,boolean all,int count){
        if(count!= -1&&(count<1||count>64))throw new IllegalArgumentException("Invalid drop count");
        if(slot< -1||slot>40)throw new IllegalArgumentException("Invalid drop slot");
        Peer peer=peers.get(id);
        if(peer!=null&&peer.socket==socket&&peer.approved&&!paused&&!expired())peer.drop=new DropRequest(slot<0?peer.selectedSlot:slot,all,count);
    }
    public synchronized void flight(UUID id,Socket socket){
        Peer peer=peers.get(id);
        if(peer!=null&&peer.socket==socket&&peer.approved&&!paused&&!expired()&&mode(peer)==Mode.CREATIVE){peer.flying=!peer.flying;peer.input.clear();peer.aim=null;sendState(peer);}
    }
    public synchronized void preferences(UUID id,Socket socket,boolean autoJump){
        Peer peer=peers.get(id);if(peer!=null&&peer.socket==socket&&peer.approved&&!expired())peer.autoJump=autoJump;
    }
    public synchronized void input(UUID id, Socket socket, Set<String> held, double yaw) {
        input(id, socket, held, yaw, 0);
    }
    public synchronized void input(UUID id, Socket socket, Set<String> held, double yaw, double pitch) {
        input(id,socket,held,yaw,pitch,null);
    }
    public synchronized void input(UUID id, Socket socket, Set<String> held, double yaw, double pitch,Touch touch) {
        input(id,socket,held,yaw,pitch,touch,null);
    }
    public synchronized void input(UUID id,Socket socket,Set<String> held,double yaw,double pitch,Touch touch,Move movement){
        if (!Double.isFinite(pitch) || Math.abs(pitch) > 180) throw new IllegalArgumentException("Invalid pitch");
        if (!BUTTONS.containsAll(held) || !Double.isFinite(yaw) || Math.abs(yaw) > 180) throw new IllegalArgumentException("Invalid input");
        Peer peer = peers.get(id);
        if (peer != null && peer.socket == socket && peer.approved && !paused && !expired()) {
            if(touch!=null){
                var view=peer.frameViews.get(touch.frame());
                if(view==null || clock.getAsLong()-view.at()>2_000_000_000L){peer.input.clear();peer.aim=null;return;}
                peer.aim=new Aim(view.sample(),touch.x(),touch.y());
            }else if(held.contains("attack")||held.contains("use")||held.contains("tap"))peer.aim=null;
            peer.movement=movement;peer.input.update(held, yaw, pitch, -1);
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
        if (slot < -1 || slot > 40) throw new IllegalArgumentException("Invalid inventory slot");
        Peer peer=peers.get(id);
        if (peer==null || peer.socket!=socket || !peer.approved || expired()) return;
        peer.inventoryRequested=true;
        if(!paused){if(slot>=0 && slot<=8)peer.selectedSlot=slot;else if(slot>=9)peer.swapSlot=slot;}
    }
    public synchronized void moveItem(UUID id,Socket socket,int source,int target,boolean half){
        moveItem(id,socket,source,target,half,-1);
    }
    public synchronized void moveItem(UUID id,Socket socket,int source,int target,boolean half,int count){
        if(count!= -1&&(count<1||count>64))throw new IllegalArgumentException("Invalid move count");
        if(source<0||source>168||target<0||target>168)throw new IllegalArgumentException("Invalid inventory move");
        Peer peer=peers.get(id);
        if(peer!=null&&peer.socket==socket&&peer.approved&&!paused&&!expired()){
            peer.swapSlot=count>0?100000+(count-1)*169*169+source*169+target:10000+(half?169*169:0)+source*169+target;peer.inventoryRequested=true;
        }
    }
    public synchronized void equip(UUID id,Socket socket,int source,int target){
        if(source<0||source>35||target<36||target>168)throw new IllegalArgumentException("Invalid equipment slot");
        Peer peer=peers.get(id);
        if(peer!=null&&peer.socket==socket&&peer.approved&&!paused&&!expired()){peer.swapSlot=1000+source*169+target;peer.inventoryRequested=true;}
    }
    public synchronized void creative(UUID id,Socket socket,String tab,int page,int item){
        if(tab==null || tab.length()>160 || !tab.matches("[a-zA-Z0-9_:./-]*") || page<0 || page>600 || item< -1 || item>=25000)throw new IllegalArgumentException("Invalid creative request");
        Peer peer=peers.get(id);if(peer!=null && peer.socket==socket && peer.approved && !paused && !expired())peer.creative=new CreativeRequest(tab,page,item);
    }
    public synchronized void publishInventory(UUID id, JsonObject inventory) {
        Peer peer=peers.get(id);if(!expired() && peer!=null && peer.socket!=null)peer.socket.send(inventory.toString());
    }
    public synchronized List<View> views() {
        prune(); return peers.values().stream().map(p -> new View(p.id, p.number, p.socket != null, p.approved,p.profile.displayName(p.number),p.profile.skin())).toList();
    }
    public synchronized Map<UUID, BotInput> takeInputs() {
        if (expired()) { close(); return Map.of(); }
        prune();
        Map<UUID, BotInput> inputs = new LinkedHashMap<>();
        for (Peer peer : peers.values()) if (peer.approved) {
            if (paused || peer.socket == null) peer.input.clear();
            var snapshot=peer.input.take();
            inputs.put(peer.id, new BotInput(peer.number, snapshot, peer.inventoryRequested, peer.selectedSlot, paused || peer.socket==null ? -1 : peer.swapSlot, paused || peer.socket==null ? null : peer.creative,paused||peer.socket==null?null:peer.aim,paused||peer.socket==null?null:peer.drop,mode(peer)==Mode.CREATIVE&&peer.flying,peer.autoJump,peer.profile,mode(peer),snapshot.held().stream().anyMatch(key->Set.of("forward","back","left","right").contains(key))?peer.movement:null));
            if(!snapshot.held().contains("attack")&&!snapshot.held().contains("use")&&!snapshot.held().contains("tap"))peer.aim=null;
            peer.inventoryRequested=false; peer.swapSlot=-1;peer.creative=null;peer.drop=null;
        }
        return Map.copyOf(inputs);
    }
    public synchronized void close() { closed = true;uiActions.clear(); for (UUID id : List.copyOf(peers.keySet())) remove(id); }
    private void prune() {
        long now = clock.getAsLong();
        for (Peer peer : List.copyOf(peers.values())) if (peer.socket == null && now - peer.disconnectedAt >= RECONNECT_NS) remove(peer.id);
    }
    private void sendState(Peer peer) {
        if (peer.socket == null) return;
        var message = new JsonObject(); message.addProperty("type", "state"); message.addProperty("approved", peer.approved); message.addProperty("paused", paused);
        message.addProperty("mode",mode(peer).name().toLowerCase(java.util.Locale.ROOT));
        message.addProperty("flying",mode(peer)==Mode.CREATIVE&&peer.flying);
        peer.socket.send(message.toString());
    }
}
