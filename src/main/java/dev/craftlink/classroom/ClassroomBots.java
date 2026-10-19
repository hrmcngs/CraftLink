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
    private UUID hostId;
    private boolean hostFlying;
    private volatile boolean recall;
    public void recall() { recall = true; }
    private volatile Map<UUID, UUID> entityIds = Map.of();
    public Map<UUID, UUID> entityIds() { return entityIds; }
    private final Map<UUID,String> statuses=new HashMap<>();
    private final Map<UUID, Long> inventoryVersions = new HashMap<>();
    private final Map<UUID, ClassroomBot> bots = new HashMap<>();
    private final Map<UUID,BotBlockActions> actions=new HashMap<>();
    public void start(MinecraftServer server, ClassroomRoom room, ResourceKey<Level> dimension, BlockPos anchor, float yaw, UUID hostId) {
        server.execute(() -> {
            clear(); this.owner = server; this.room = room; this.level = server.getLevel(dimension); this.anchor = anchor.immutable(); this.yaw = yaw; this.hostId=hostId;
        });
    }
    public void stop() {
        MinecraftServer server = owner;
        if (server != null && !server.isStopped()) server.execute(this::clear);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || owner == null || event.getServer() != owner) return;
        if (room == null || level == null || room.expired()) { clear(); return; }
        var host=owner.getPlayerList().getPlayer(hostId);
        if(host!=null&&host.serverLevel()==level){anchor=host.blockPosition();yaw=host.getYRot();hostFlying=host.getAbilities().flying;}
        boolean moveBack = recall; recall = false;
        var inputs = room.takeInputs();
        for (UUID id : List.copyOf(bots.keySet())) if (!inputs.containsKey(id)) {var action=actions.remove(id);if(action!=null)action.close(bots.get(id));removeBot(bots.remove(id)); inventoryVersions.remove(id);statuses.remove(id); }
        for (var entry : inputs.entrySet()) {
            ClassroomBot bot = bots.get(entry.getKey());
            if (bot == null || !bot.isAlive()) {
                if (bot != null) {var old=actions.remove(entry.getKey());if(old!=null)old.close(bot);removeBot(bot);}
                bot = new ClassroomBot(level, entry.getValue().number());
                if(!place(bot, entry.getValue().number()))continue;
                if (!level.addFreshEntity(bot)) continue;
                bots.put(entry.getKey(), bot);
                var previous=actions.put(entry.getKey(),new BotBlockActions(level,entry.getKey(),entry.getValue().number()));if(previous!=null)previous.cancel();
            }
            if (moveBack || outsideArea(bot.getX(), bot.getY(), bot.getZ(), anchor.getX()+.5, anchor.getZ()+.5, level.getMinBuildHeight())) place(bot,entry.getValue().number());
            bot.selectSlot(entry.getValue().selectedSlot());
            bot.profile(entry.getValue().profile());
            boolean creativeAllowed=entry.getValue().mode()==ClassroomRoom.Mode.CREATIVE;
            bot.setInvulnerable(creativeAllowed);
            var request=entry.getValue().creative();
            if(request!=null){
                if(creativeAllowed && room.catalog()!=null){
                    if(request.item()>=0){try{var stack=room.catalog().stack(request.item());if(stack.isItemEnabled(level.enabledFeatures()))bot.creativeItem(stack);}catch(IllegalArgumentException ignored){}}
                    else dev.craftlink.video.ItemIcons.send(room,entry.getKey(),room.catalog().page(request.tab(),request.page()));
                }else {var denied=new com.google.gson.JsonObject();denied.addProperty("type","creativeDenied");room.publishInventory(entry.getKey(),denied);}
            }
            bot.flying(entry.getValue().flying());bot.autoJump(entry.getValue().autoJump());
            bot.accept(entry.getValue().input(),entry.getValue().movement());
            if(entry.getValue().swapSlot()>=100000){int encoded=entry.getValue().swapSlot()-100000;int count=encoded/(169*169)+1;encoded%=169*169;bot.moveItem(encoded/169,encoded%169,false,count);}
            else if(entry.getValue().swapSlot()>=10000){int encoded=entry.getValue().swapSlot()-10000;boolean half=encoded>=169*169;encoded%=169*169;bot.moveItem(encoded/169,encoded%169,half);}
            else if(entry.getValue().swapSlot()>=1000){int encoded=entry.getValue().swapSlot()-1000;bot.equip(encoded/169,encoded%169);}
            else if(entry.getValue().swapSlot()>=9)bot.swapToHand(entry.getValue().swapSlot());
            if(entry.getValue().drop()!=null)bot.dropItem(entry.getValue().drop().slot(),entry.getValue().drop().all(),entry.getValue().drop().count());
            if(!room.canAct(entry.getKey()))actions.get(entry.getKey()).cancelActions();
            actions.get(entry.getKey()).tickStatus(bot,entry.getValue().mode());
            for(var action:room.takeUi(entry.getKey()))actions.get(entry.getKey()).ui(bot,action,room,entry.getKey());
            if(room.canAct(entry.getKey()))actions.get(entry.getKey()).tick(bot,entry.getValue().input(),entry.getValue().mode(),entry.getValue().aim());
            else actions.get(entry.getKey()).cancelActions();
            actions.get(entry.getKey()).publishMenu(room,entry.getKey());
            if(entry.getValue().inventoryRequested() || !Objects.equals(inventoryVersions.get(entry.getKey()),bot.inventoryVersion())) {
                var inventory=bot.inventoryMessage();inventory.addProperty("weaponSkills",WeaponSkills.available());inventory.addProperty("creative",creativeAllowed);inventory.addProperty("mode",entry.getValue().mode().name().toLowerCase(java.util.Locale.ROOT));dev.craftlink.video.ItemIcons.send(room,entry.getKey(),inventory);inventoryVersions.put(entry.getKey(),bot.inventoryVersion());
                dev.craftlink.video.ItemIcons.preview(room,entry.getKey(),bot);
            }
            if(level.getGameTime()%5==0||entry.getValue().inventoryRequested()){
                var status=actions.get(entry.getKey()).status(bot,entry.getValue().mode());String serialized=status.toString();
                if(!serialized.equals(statuses.get(entry.getKey()))){room.publishInventory(entry.getKey(),status);statuses.put(entry.getKey(),serialized);}
            }
        }
        Map<UUID, UUID> ids = new HashMap<>();
        bots.forEach((peer, bot) -> ids.put(peer, bot.getUUID()));
        entityIds = Map.copyOf(ids);
    }
    public void teleport(int number,java.util.function.Consumer<Boolean> result){
        var server=owner;if(server==null){result.accept(false);return;}
        server.execute(()->{
            if(owner!=server||room==null||room.expired()){result.accept(false);return;}
            var host=server.getPlayerList().getPlayer(hostId);
            if(host==null||host.serverLevel()!=level){result.accept(false);return;}
            anchor=host.blockPosition();yaw=host.getYRot();hostFlying=host.getAbilities().flying;
            for(var entry:bots.entrySet())if(entry.getValue().participantNumber()==number){actions.get(entry.getKey()).cancel();result.accept(place(entry.getValue(),number));return;}
            result.accept(false);
        });
    }
    private boolean place(ClassroomBot bot, int number) {
        double angle=Math.toRadians(yaw), lane=(number-1)%4*1.5-2.25, forward=3+(number-1)/4*1.5;
        double x=anchor.getX()+.5+Math.cos(angle)*lane-Math.sin(angle)*forward;
        double z=anchor.getZ()+.5+Math.sin(angle)*lane+Math.cos(angle)*forward;
        var previous=bot.position();float previousYaw=bot.getYRot(),previousPitch=bot.getXRot();
        for(int radius=0;radius<=4;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            if(placeOnGround(bot,Math.floor(x+dx)+.5,Math.floor(z+dz)+.5))return true;
        }
        if(placeOnGround(bot,anchor.getX()+.5,anchor.getZ()+.5))return true;
        // Keep existing BOTs where they were; never fall back to an unchecked solid block.
        bot.moveTo(previous.x,previous.y,previous.z,previousYaw,previousPitch);
        return false;
    }
    private boolean placeOnGround(ClassroomBot bot,double x,double z){
        var column=BlockPos.containing(x,anchor.getY(),z);
        if(!level.hasChunkAt(column))return false;
        int top=Math.min(level.getMaxBuildHeight()-2,anchor.getY()+4);
        for(int y=top;y>=landingMinimumY(anchor.getY(),level.getMinBuildHeight(),hostFlying);y--){
            var below=BlockPos.containing(x,y,z);var state=level.getBlockState(below);
            if(!state.getFluidState().isEmpty())continue;
            var shape=state.getCollisionShape(level,below);
            double support=Double.NEGATIVE_INFINITY;
            for(var box:shape.toAabbs())if(box.minX<=x-below.getX()&&box.maxX>=x-below.getX()&&box.minZ<=z-below.getZ()&&box.maxZ>=z-below.getZ())support=Math.max(support,box.maxY);
            if(!Double.isFinite(support))continue;
            double feet=y+support;
            bot.moveTo(x,feet,z,yaw,0);
            if(level.getWorldBorder().isWithinBounds(bot.getBoundingBox())&&level.noCollision(bot,bot.getBoundingBox())&&!level.containsAnyLiquid(bot.getBoundingBox())){
                bot.setDeltaMovement(0,0,0);bot.fallDistance=0;return true;
            }
        }
        return false;
    }
    static int landingMinimumY(int hostY,int minimumY,boolean flying){
        // Grounded hosts must not spawn companions in a cave far below a nearby cliff.
        return flying?minimumY:Math.max(minimumY,hostY-4);
    }
    static boolean outsideArea(double x, double y, double z, double anchorX, double anchorZ, int minimumY) {
        double dx=x-anchorX, dz=z-anchorZ;
        return dx*dx+dz*dz>32*32 || y<minimumY-4;
    }

    private void removeBot(ClassroomBot bot){bot.returnInventory();bot.discard();}
    private void clear() {actions.forEach((id,action)->{var bot=bots.get(id);if(bot!=null)action.close(bot);});actions.clear(); for (var bot : bots.values()) removeBot(bot); bots.clear(); inventoryVersions.clear();statuses.clear(); entityIds = Map.of(); room = null; level = null; owner = null; hostId=null; }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) { if (event.getServer() == owner) clear(); }
}
