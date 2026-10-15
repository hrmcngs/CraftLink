package dev.craftlink.classroom;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** All entity access is confined to the integrated server thread. */
public final class ClassroomBots {
    private volatile MinecraftServer owner;
    private ClassroomRoom room;
    private ServerLevel level;
    private BlockPos anchor;
    private float yaw;
    private volatile boolean recall;
    public void recall() { recall = true; }
    private volatile Map<UUID, UUID> entityIds = Map.of();
    public Map<UUID, UUID> entityIds() { return entityIds; }
    private final Map<UUID, Long> inventoryVersions = new HashMap<>();
    private final Map<UUID, ClassroomBot> bots = new HashMap<>();
    private final Map<UUID,BotBlockActions> actions=new HashMap<>();
    public void start(MinecraftServer server, ClassroomRoom room, ResourceKey<Level> dimension, BlockPos anchor, float yaw) {
        server.execute(() -> {
            clear(); this.owner = server; this.room = room; this.level = server.getLevel(dimension); this.anchor = anchor.immutable(); this.yaw = yaw;
        });
    }
    public void stop() {
        MinecraftServer server = owner;
        if (server != null && !server.isStopped()) server.execute(this::clear);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || owner == null || event.getServer() != owner) return;
        if (room == null || level == null || room.expired()) { clear(); return; }
        boolean moveBack = recall; recall = false;
        var inputs = room.takeInputs();
        for (UUID id : List.copyOf(bots.keySet())) if (!inputs.containsKey(id)) {var action=actions.remove(id);if(action!=null)action.cancel();removeBot(bots.remove(id)); inventoryVersions.remove(id); }
        for (var entry : inputs.entrySet()) {
            ClassroomBot bot = bots.get(entry.getKey());
            if (bot == null || !bot.isAlive()) {
                if (bot != null) removeBot(bot);
                bot = new ClassroomBot(level, entry.getValue().number());
                place(bot, entry.getValue().number());
                if (!level.addFreshEntity(bot)) continue;
                bots.put(entry.getKey(), bot);
                var previous=actions.put(entry.getKey(),new BotBlockActions(level,entry.getKey(),entry.getValue().number()));if(previous!=null)previous.cancel();
            }
            if (moveBack || outsideArea(bot.getX(), bot.getY(), bot.getZ(), anchor.getX()+.5, anchor.getZ()+.5, level.getMinBuildHeight())) place(bot,entry.getValue().number());
            bot.selectSlot(entry.getValue().selectedSlot());
            boolean creativeAllowed=room.mode()==ClassroomRoom.Mode.CREATIVE;
            bot.setInvulnerable(creativeAllowed);
            var request=entry.getValue().creative();
            if(request!=null){
                if(creativeAllowed && room.catalog()!=null){
                    if(request.item()>=0){try{var stack=room.catalog().stack(request.item());if(stack.isItemEnabled(level.enabledFeatures()))bot.creativeItem(stack);}catch(IllegalArgumentException ignored){}}
                    else dev.craftlink.video.ItemIcons.send(room,entry.getKey(),room.catalog().page(request.tab(),request.page()));
                }else {var denied=new com.google.gson.JsonObject();denied.addProperty("type","creativeDenied");room.publishInventory(entry.getKey(),denied);}
            }
            bot.accept(entry.getValue().input());
            if(entry.getValue().swapSlot()>=9)bot.swapToHand(entry.getValue().swapSlot());
            actions.get(entry.getKey()).tick(bot,entry.getValue().input(),room.mode(),entry.getValue().aim());
            if(entry.getValue().inventoryRequested() || !Objects.equals(inventoryVersions.get(entry.getKey()),bot.inventoryVersion())) {
                var inventory=bot.inventoryMessage();inventory.addProperty("creative",creativeAllowed);inventory.addProperty("mode",room.mode().name().toLowerCase(java.util.Locale.ROOT));dev.craftlink.video.ItemIcons.send(room,entry.getKey(),inventory);inventoryVersions.put(entry.getKey(),bot.inventoryVersion());
            }
        }
        Map<UUID, UUID> ids = new HashMap<>();
        bots.forEach((peer, bot) -> ids.put(peer, bot.getUUID()));
        entityIds = Map.copyOf(ids);
    }
    private void place(ClassroomBot bot, int number) {
        double angle = Math.toRadians(yaw), lane = (number-1)%4*1.5-2.25, forward = 3+(number-1)/4*1.5;
        double x = anchor.getX()+.5+Math.cos(angle)*lane-Math.sin(angle)*forward;
        double z = anchor.getZ()+.5+Math.sin(angle)*lane+Math.cos(angle)*forward;
        // Search all the way down: the host may have opened the room while flying.
        for (int radius=0;radius<=3;radius++) {
            for (int[] offset : new int[][]{{radius,0},{-radius,0},{0,radius},{0,-radius}}) {
                if (placeOnGround(bot, x+offset[0], z+offset[1])) return;
            }
        }
        // A nearby cliff/void must not become an endlessly falling spawn point.
        if (!placeOnGround(bot, anchor.getX()+.5, anchor.getZ()+.5)) {
            bot.moveTo(anchor.getX()+.5, anchor.getY(), anchor.getZ()+.5, yaw, 0);
            bot.setDeltaMovement(0,0,0);
        }
    }
    private boolean placeOnGround(ClassroomBot bot, double x, double z) {
        int top = Math.min(level.getMaxBuildHeight()-2, anchor.getY()+2);
        for (int y=top; y>=level.getMinBuildHeight(); y--) {
            var below = BlockPos.containing(x,y,z);
            var state = level.getBlockState(below);
            var shape = state.getCollisionShape(level,below);
            if (shape.isEmpty() || !state.getFluidState().isEmpty()) continue;
            double feet = y+shape.max(net.minecraft.core.Direction.Axis.Y);
            bot.moveTo(x,feet,z,yaw,0);
            if (level.getWorldBorder().isWithinBounds(bot.getBoundingBox()) && level.noCollision(bot,bot.getBoundingBox())) {
                bot.setDeltaMovement(0,0,0); bot.fallDistance=0; return true;
            }
        }
        return false;
    }
    static boolean outsideArea(double x, double y, double z, double anchorX, double anchorZ, int minimumY) {
        double dx=x-anchorX, dz=z-anchorZ;
        return dx*dx+dz*dz>32*32 || y<minimumY-4;
    }

    private void removeBot(ClassroomBot bot){bot.returnInventory();bot.discard();}
    private void clear() {actions.values().forEach(BotBlockActions::cancel);actions.clear(); for (var bot : bots.values()) removeBot(bot); bots.clear(); inventoryVersions.clear(); entityIds = Map.of(); room = null; level = null; owner = null; }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) { if (event.getServer() == owner) clear(); }
}
