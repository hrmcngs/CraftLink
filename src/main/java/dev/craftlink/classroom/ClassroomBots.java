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
            if (bot.distanceToSqr(anchor.getX()+.5, anchor.getY(), anchor.getZ()+.5) > 32*32 || bot.getY() < anchor.getY()-8 || bot.getY() > anchor.getY()+16) place(bot,entry.getValue().number());
            bot.accept(entry.getValue().input());
        }
    }
    private void place(ClassroomBot bot, int number) {
        double x = anchor.getX()+.5+(number-1)%4*1.5-2.25, z = anchor.getZ()+.5+3+(number-1)/4*1.5;
        bot.moveTo(x,anchor.getY()+.1,z,yaw,0); bot.setDeltaMovement(0,0,0);
    }
    private void clear() { for (var bot : bots.values()) bot.discard(); bots.clear(); room = null; level = null; owner = null; }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) { if (event.getServer() == owner) clear(); }
}
