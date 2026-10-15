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
    private final Map<UUID, ClassroomBot> bots = new HashMap<>();
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
        for (UUID id : List.copyOf(bots.keySet())) if (!inputs.containsKey(id)) { bots.remove(id).discard(); }
        for (var entry : inputs.entrySet()) {
            ClassroomBot bot = bots.get(entry.getKey());
            if (bot == null || !bot.isAlive()) {
                if (bot != null) bot.discard();
                bot = new ClassroomBot(level, entry.getValue().number());
                place(bot, entry.getValue().number());
                if (!level.addFreshEntity(bot)) continue;
                bots.put(entry.getKey(), bot);
            }
            if (moveBack || bot.distanceToSqr(anchor.getX()+.5, anchor.getY(), anchor.getZ()+.5) > 32*32 || bot.getY() < anchor.getY()-8 || bot.getY() > anchor.getY()+16) place(bot,entry.getValue().number());
            bot.accept(entry.getValue().input());
        }
        Map<UUID, UUID> ids = new HashMap<>();
        bots.forEach((peer, bot) -> ids.put(peer, bot.getUUID()));
        entityIds = Map.copyOf(ids);
    }
    private void place(ClassroomBot bot, int number) {
        double angle = Math.toRadians(yaw), lane = (number-1)%4*1.5-2.25, forward = 3+(number-1)/4*1.5;
        double x = anchor.getX()+.5+Math.cos(angle)*lane-Math.sin(angle)*forward;
        double z = anchor.getZ()+.5+Math.sin(angle)*lane+Math.cos(angle)*forward;
        // Prefer ground in front of the host and avoid spawning inside walls or other bots.
        for (int dy : new int[]{0,1,2,-1,-2,-3,-4}) for (int radius=0;radius<=3;radius++) {
            for (int[] offset : new int[][]{{radius,0},{-radius,0},{0,radius},{0,-radius}}) {
                bot.moveTo(x+offset[0],anchor.getY()+dy+.1,z+offset[1],yaw,0);
                var below = BlockPos.containing(bot.getX(),bot.getY()-.2,bot.getZ());
                if (level.getWorldBorder().isWithinBounds(bot.getBoundingBox()) && level.noCollision(bot,bot.getBoundingBox())
                    && !level.getBlockState(below).getCollisionShape(level,below).isEmpty()) {
                    bot.setDeltaMovement(0,0,0); return;
                }
            }
        }
        bot.moveTo(x,anchor.getY()+.1,z,yaw,0);bot.setDeltaMovement(0,0,0);
    }
    private void clear() { for (var bot : bots.values()) bot.discard(); bots.clear(); entityIds = Map.of(); room = null; level = null; owner = null; }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) { if (event.getServer() == owner) clear(); }
}
