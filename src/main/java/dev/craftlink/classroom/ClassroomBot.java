package dev.craftlink.classroom;

import dev.craftlink.input.RemoteState;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Pose;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;

/** Remote-controlled humanoid; all intent is applied on the integrated server thread. */
public final class ClassroomBot extends PathfinderMob {
    private static final ChatFormatting[] COLORS = {ChatFormatting.RED,ChatFormatting.BLUE,ChatFormatting.GREEN,ChatFormatting.YELLOW,ChatFormatting.LIGHT_PURPLE,ChatFormatting.AQUA,ChatFormatting.GOLD,ChatFormatting.WHITE};
    private static final String[] NAMES = {"あか","あお","みどり","きいろ","むらさき","みずいろ","オレンジ","しろ"};
    private static final EntityDataAccessor<Integer> NUMBER = SynchedEntityData.defineId(ClassroomBot.class, EntityDataSerializers.INT);
    private Set<String> held = Set.of();
    private boolean jumpPulse;
    private final net.minecraft.world.SimpleContainer inventory = new net.minecraft.world.SimpleContainer(36);
    private int selectedSlot;
    private long inventoryVersion;
    public void returnInventory(){for(int i=0;i<36;i++){var item=inventory.removeItemNoUpdate(i);if(!item.isEmpty())spawnAtLocation(item);}setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);}
    public long inventoryVersion(){return inventoryVersion;}
    public void selectSlot(int slot){if(slot<0||slot>8)throw new IllegalArgumentException("Invalid slot");if(selectedSlot!=slot){selectedSlot=slot;inventoryVersion++;}setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,inventory.getItem(slot).copy());}
    public void swapToHand(int slot){if(slot<9||slot>35)throw new IllegalArgumentException("Invalid backpack slot");var previous=inventory.getItem(selectedSlot);inventory.setItem(selectedSlot,inventory.getItem(slot));inventory.setItem(slot,previous);inventoryVersion++;selectSlot(selectedSlot);}
    public com.google.gson.JsonObject inventoryMessage(){
        var result=new com.google.gson.JsonObject();result.addProperty("type","inventory");result.addProperty("selected",selectedSlot);
        var slots=new com.google.gson.JsonArray();
        for(int i=0;i<inventory.getContainerSize();i++){var item=inventory.getItem(i);var slot=new com.google.gson.JsonObject();slot.addProperty("slot",i);slot.addProperty("id",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());slot.addProperty("name",item.isEmpty()?"":item.getHoverName().getString());slot.addProperty("count",item.getCount());slots.add(slot);}
        result.add("slots",slots);return result;
    }
    @Override protected void pickUpItem(net.minecraft.world.entity.item.ItemEntity entity){
        if(!isAlive()||entity.hasPickUpDelay())return;
        var original=entity.getItem();int count=original.getCount();var remainder=inventory.addItem(original.copy());int taken=count-remainder.getCount();
        if(taken<=0)return;take(entity,taken);if(remainder.isEmpty())entity.discard();else entity.setItem(remainder);inventoryVersion++;selectSlot(selectedSlot);
    }
    public ClassroomBot(EntityType<? extends ClassroomBot> type, Level level) {
        super(type, level);
        moveControl = new MoveControl(this) {
            @Override public void tick() {
                float x = (held.contains("left") ? 1 : 0) - (held.contains("right") ? 1 : 0);
                float z = (held.contains("forward") ? 1 : 0) - (held.contains("back") ? 1 : 0);
                float length = Math.max(1, (float)Math.sqrt(x*x+z*z));
                setSpeed(held.contains("sneak") ? .055f : held.contains("sprint") ? .20f : .12f);
                setXxa(x/length); setZza(z/length);
            }
        };
        jumpControl = new JumpControl(this) {
            @Override public void tick() { setJumping(held.contains("jump") || jumpPulse); jumpPulse = false; }
        };
    }
    public ClassroomBot(ServerLevel level, int number) {
        this(ClassroomEntities.BOT.get(), level);
        if (number < 1 || number > NAMES.length) throw new IllegalArgumentException("Participant number");
        entityData.set(NUMBER, number);
        setInvulnerable(true); setCanPickUpLoot(true);
        setCustomName(Component.literal(number + "号 " + NAMES[number-1]).withStyle(COLORS[number-1])); setCustomNameVisible(true);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(NUMBER, 1); }
    public int participantNumber() { return entityData.get(NUMBER); }
    @Override public void aiStep() { updateSwingTime(); super.aiStep(); }
    public void accept(RemoteState.Snapshot input) {
        held = input.held(); jumpPulse = input.presses().contains("jump");
        setYRot(getYRot() + (float)input.yaw()); setYHeadRot(getYRot());
        setXRot(net.minecraft.util.Mth.clamp(getXRot() + (float)input.pitch(), -90, 90));
        setShiftKeyDown(held.contains("sneak")); setSprinting(held.contains("sprint") && !isShiftKeyDown());
        setPose(isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING);
        if (held.isEmpty()) setDeltaMovement(0, getDeltaMovement().y, 0);
        if (input.presses().contains("use")) {
            swing(InteractionHand.MAIN_HAND);
            ((ServerLevel)level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY()+2, getZ(), 8,.35,.25,.35,0);
        }
    }
    @Override protected void customServerAiStep() { /* No autonomous goals: remote intent controls movement. */ }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void checkFallDamage(double distance, boolean grounded, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        fallDistance = 0; // No farmland trampling or fall-triggered block changes from demo NPCs.
    }
}
