package dev.craftlink.classroom;

import dev.craftlink.input.RemoteState;
import java.util.Set;
import net.minecraft.ChatFormatting;
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
    private static final EntityDataAccessor<Boolean> FLYING=SynchedEntityData.defineId(ClassroomBot.class,EntityDataSerializers.BOOLEAN);
    private Set<String> held = Set.of();
    private boolean jumpPulse;
    private boolean autoJump;
    public void autoJump(boolean value){autoJump=value;}
    public boolean flying(){return entityData.get(FLYING);}
    public void flying(boolean value){if(flying()!=value){entityData.set(FLYING,value);setNoGravity(value);setDeltaMovement(0,0,0);fallDistance=0;}}
    private final net.minecraft.world.SimpleContainer inventory = new net.minecraft.world.SimpleContainer(36);
    private int selectedSlot;
    private long inventoryVersion;
    public void returnInventory(){for(int i=0;i<36;i++){var item=inventory.removeItemNoUpdate(i);if(!item.isEmpty())spawnAtLocation(item);}setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);}
    public long inventoryVersion(){return inventoryVersion;}
    public void selectSlot(int slot){if(slot<0||slot>8)throw new IllegalArgumentException("Invalid slot");if(selectedSlot!=slot){selectedSlot=slot;inventoryVersion++;}setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,inventory.getItem(slot).copy());}
    public void creativeItem(net.minecraft.world.item.ItemStack stack){inventory.setItem(selectedSlot,stack.copy());inventoryVersion++;selectSlot(selectedSlot);}
    public void swapToHand(int slot){if(slot<9||slot>35)throw new IllegalArgumentException("Invalid backpack slot");var previous=inventory.getItem(selectedSlot);inventory.setItem(selectedSlot,inventory.getItem(slot));inventory.setItem(slot,previous);inventoryVersion++;selectSlot(selectedSlot);}
    public com.google.gson.JsonObject inventoryMessage(){
        var result=new com.google.gson.JsonObject();result.addProperty("type","inventory");result.addProperty("selected",selectedSlot);
        var slots=new com.google.gson.JsonArray();
        for(int i=0;i<inventory.getContainerSize();i++){var item=inventory.getItem(i);var slot=new com.google.gson.JsonObject();slot.addProperty("slot",i);slot.addProperty("id",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());slot.addProperty("name",item.isEmpty()?"":item.getHoverName().getString());slot.addProperty("count",item.getCount());slots.add(slot);}
        for(int i=0;i<slots.size();i++)slots.get(i).getAsJsonObject().addProperty("iconKey",dev.craftlink.video.ItemIcons.request(inventory.getItem(i)));
        result.add("slots",slots);return result;
    }
    @Override protected void pickUpItem(net.minecraft.world.entity.item.ItemEntity entity){
        if(!isAlive()||entity.hasPickUpDelay())return;
        var original=entity.getItem();int count=original.getCount();var remainder=inventory.addItem(original.copy());int taken=count-remainder.getCount();
        if(taken<=0)return;take(entity,taken);if(remainder.isEmpty())entity.discard();else entity.setItem(remainder);inventoryVersion++;selectSlot(selectedSlot);
    }
    public ClassroomBot(EntityType<? extends ClassroomBot> type, Level level) {
        super(type, level);
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND,0);
        moveControl = new MoveControl(this) {
            @Override public void tick() {
                float x = (held.contains("left") ? 1 : 0) - (held.contains("right") ? 1 : 0);
                float z = (held.contains("forward") ? 1 : 0) - (held.contains("back") ? 1 : 0);
                float length = Math.max(1, (float)Math.sqrt(x*x+z*z));
                setSpeed(held.contains("sneak") ? .055f : held.contains("sprint") ? .20f : .12f);
                setXxa(x/length); setZza(z/length);
            }
        };
        // Vanilla LookControl normally resets a mob's vertical rotation each tick.
        lookControl = new net.minecraft.world.entity.ai.control.LookControl(this) {
            @Override public void tick() { setYHeadRot(getYRot()); }
        };
        jumpControl = new JumpControl(this) {
            @Override public void tick() { setJumping(!flying() && (held.contains("jump") || jumpPulse || shouldAutoJump())); jumpPulse = false; }
        };
    }
    public ClassroomBot(ServerLevel level, int number) {
        this(ClassroomEntities.BOT.get(), level);
        if (number < 1 || number > NAMES.length) throw new IllegalArgumentException("Participant number");
        entityData.set(NUMBER, number);
        setInvulnerable(true); setCanPickUpLoot(true);
        setCustomName(Component.literal(number + "号 " + NAMES[number-1]).withStyle(COLORS[number-1])); setCustomNameVisible(true);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(NUMBER, 1);entityData.define(FLYING,false); }
    @Override public float getStandingEyeHeight(Pose pose,net.minecraft.world.entity.EntityDimensions dimensions){return pose==Pose.SWIMMING?.4f:pose==Pose.CROUCHING?1.27f:1.62f;}
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(Pose pose){return pose==Pose.SWIMMING?net.minecraft.world.entity.EntityDimensions.scalable(.6f,.6f):pose==Pose.CROUCHING?net.minecraft.world.entity.EntityDimensions.scalable(.6f,1.5f):super.getDimensions(pose);}
    public int participantNumber() { return entityData.get(NUMBER); }
    @Override public void aiStep() { updateSwingTime(); super.aiStep(); }
    public void accept(RemoteState.Snapshot input) {
        held = input.held(); jumpPulse = input.presses().contains("jump");
        setYRot(getYRot() + (float)input.yaw()); setYHeadRot(getYRot());
        setXRot(net.minecraft.util.Mth.clamp(getXRot() + (float)input.pitch(), -90, 90));
        setShiftKeyDown(held.contains("sneak")); setSprinting(held.contains("sprint") && !isShiftKeyDown());
        setSwimming(!flying()&&(isUnderWater()||isSwimming()&&isInWater())&&isSprinting()&&held.contains("forward"));
        setPose(isSwimming()?Pose.SWIMMING:isShiftKeyDown()&&!flying()?Pose.CROUCHING:Pose.STANDING);
        if (held.isEmpty()) setDeltaMovement(0, getDeltaMovement().y, 0);
        if (input.presses().contains("use") || input.presses().contains("attack")) {
            swing(InteractionHand.MAIN_HAND);
        }
    }
    private boolean shouldAutoJump(){
        if(!autoJump||!onGround()||!horizontalCollision||isShiftKeyDown()||xxa==0&&zza==0)return false;
        double radians=Math.toRadians(getYRot());var delta=new net.minecraft.world.phys.Vec3(Math.cos(radians)*xxa-Math.sin(radians)*zza,0,Math.sin(radians)*xxa+Math.cos(radians)*zza).normalize().scale(.7);
        return level().noCollision(this,getBoundingBox().move(delta.x,1,delta.z));
    }
    @Override public void travel(net.minecraft.world.phys.Vec3 intent){
        if(flying()){
            if(isControlledByLocalInstance()){
                moveRelative(held.contains("sprint")?.16f:.09f,new net.minecraft.world.phys.Vec3(intent.x,0,intent.z));
                var velocity=getDeltaMovement();double vertical=(held.contains("jump")?.25:0)-(held.contains("sneak")?.25:0);
                move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(velocity.x,vertical,velocity.z));
                setDeltaMovement(velocity.x*.6,0,velocity.z*.6);
            }
            calculateEntityAnimation(false);return;
        }
        if(isSwimming()&&held.contains("forward"))setDeltaMovement(getDeltaMovement().add(0,getLookAngle().y*.04,0));
        if(isInWater()&&held.contains("sneak"))setDeltaMovement(getDeltaMovement().add(0,-.04,0));
        super.travel(intent);
    }
    public void dropItem(int slot,boolean all){
        if(slot<0||slot>35)throw new IllegalArgumentException("Invalid drop slot");
        var current=inventory.getItem(slot);if(current.isEmpty())return;
        var stack=inventory.removeItem(slot,all?current.getCount():1);var eyes=getEyePosition();
        var entity=new net.minecraft.world.entity.item.ItemEntity(level(),eyes.x,eyes.y-.3,eyes.z,stack);
        entity.setDeltaMovement(getLookAngle().scale(.3).add(0,.15,0));entity.setDefaultPickUpDelay();entity.setThrower(getUUID());
        if(!level().addFreshEntity(entity))inventory.addItem(stack);
        inventoryVersion++;selectSlot(selectedSlot);
    }
    public void copyInventoryTo(Player player){
        for(int i=0;i<36;i++)player.getInventory().setItem(i,inventory.getItem(i).copy());
        player.getInventory().selected=selectedSlot;
    }
    public void copyInventoryFrom(Player player){
        boolean changed=false;
        for(int i=0;i<36;i++){var next=player.getInventory().getItem(i);if(!net.minecraft.world.item.ItemStack.matches(inventory.getItem(i),next)){inventory.setItem(i,next.copy());changed=true;}}
        if(changed){inventoryVersion++;selectSlot(selectedSlot);}
    }
    @Override protected void customServerAiStep() { /* No autonomous goals: remote intent controls movement. */ }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void checkFallDamage(double distance, boolean grounded, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        fallDistance = 0; // No farmland trampling or fall-triggered block changes from demo NPCs.
    }
}
