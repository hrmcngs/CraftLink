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
    private long damageSequence;
    public long damageSequence(){return damageSequence;}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
        if(BotBlockActions.isOwnActor(this,source.getEntity()))return false;
        boolean damaged=super.hurt(source,amount);
        if(damaged && amount>0)damageSequence++;
        return damaged;
    }
    private static final ChatFormatting[] COLORS = {ChatFormatting.RED,ChatFormatting.BLUE,ChatFormatting.GREEN,ChatFormatting.YELLOW,ChatFormatting.LIGHT_PURPLE,ChatFormatting.AQUA,ChatFormatting.GOLD,ChatFormatting.WHITE};
    private static final EntityDataAccessor<Integer> NUMBER = SynchedEntityData.defineId(ClassroomBot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLYING=SynchedEntityData.defineId(ClassroomBot.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SKIN=SynchedEntityData.defineId(ClassroomBot.class,EntityDataSerializers.INT);
    public int skinIndex(){return entityData.get(SKIN);}
    public void previewSkin(int index){entityData.set(SKIN,index);}
    public void profile(ParticipantProfile profile){
        String text=profile.displayName(participantNumber());
        if(getCustomName()==null||!text.equals(getCustomName().getString())){setCustomName(Component.literal(text).withStyle(COLORS[participantNumber()-1]));inventoryVersion++;}
        int skin=profile.skinIndex(participantNumber());if(skinIndex()!=skin){entityData.set(SKIN,skin);inventoryVersion++;}
    }
    private Set<String> held = Set.of();
    private boolean jumpPulse;
    private ClassroomRoom.Move movement;
    private boolean autoJump;
    public void autoJump(boolean value){autoJump=value;}
    public boolean flying(){return entityData.get(FLYING);}
    public void flying(boolean value){if(flying()!=value){entityData.set(FLYING,value);setNoGravity(value);setDeltaMovement(0,0,0);fallDistance=0;}}
    private final net.minecraft.world.SimpleContainer inventory = new net.minecraft.world.SimpleContainer(41);
    private int selectedSlot;
    private long inventoryVersion;
    public void returnInventory(){for(var slot:AccessoryInventory.slots(this)){var stack=slot.inventory().getStackInSlot(slot.index());if(!stack.isEmpty())spawnAtLocation(stack.copy());slot.inventory().setStackInSlot(slot.index(),net.minecraft.world.item.ItemStack.EMPTY);}for(int i=0;i<41;i++){var item=inventory.removeItemNoUpdate(i);if(!item.isEmpty())spawnAtLocation(item);}setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);}
    public long inventoryVersion(){return inventoryVersion;}
    public void selectSlot(int slot){if(slot<0||slot>8)throw new IllegalArgumentException("Invalid slot");if(selectedSlot!=slot){selectedSlot=slot;inventoryVersion++;}setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,inventory.getItem(slot).copy());
        for(var equipment:net.minecraft.world.entity.EquipmentSlot.values())if(equipment!=net.minecraft.world.entity.EquipmentSlot.MAINHAND)setItemSlot(equipment,inventory.getItem(equipment==net.minecraft.world.entity.EquipmentSlot.OFFHAND?40:36+equipment.getIndex()).copy());}
    public void creativeItem(net.minecraft.world.item.ItemStack stack){
        if(inventory.getItem(selectedSlot).isEmpty())inventory.setItem(selectedSlot,stack.copy());
        else if(addToBackpack(stack.copy()).getCount()==stack.getCount())return;
        inventoryVersion++;selectSlot(selectedSlot);
    }
    public void swapToHand(int slot){if(slot<9||slot>40)throw new IllegalArgumentException("Invalid backpack slot");if(slot>=36&&!inventory.getItem(slot).isEmpty()&&net.minecraft.world.item.enchantment.EnchantmentHelper.hasBindingCurse(inventory.getItem(slot)))return;var previous=inventory.getItem(selectedSlot);if(slot>=36&&!previous.isEmpty())return;inventory.setItem(selectedSlot,inventory.getItem(slot));inventory.setItem(slot,previous);inventoryVersion++;selectSlot(selectedSlot);}
    private net.minecraft.world.item.ItemStack slotStack(int slot,java.util.List<AccessoryInventory.Slot> accessories){
        return slot<41?inventory.getItem(slot):accessories.get(slot-41).inventory().getStackInSlot(accessories.get(slot-41).index());
    }
    private int slotLimit(int slot,net.minecraft.world.item.ItemStack stack,java.util.List<AccessoryInventory.Slot> accessories){
        if(stack.isEmpty())return 64;
        if(slot<36||slot==40)return stack.getMaxStackSize();
        if(slot<40){
            var equipment=net.minecraft.world.entity.EquipmentSlot.byTypeAndIndex(net.minecraft.world.entity.EquipmentSlot.Type.ARMOR,slot-36);
            return stack.canEquip(equipment,this)?1:0;
        }
        var accessory=accessories.get(slot-41);
        return accessory.inventory().isItemValid(accessory.index(),stack)?Math.min(1,accessory.inventory().getSlotLimit(accessory.index())):0;
    }
    private boolean canRemove(int slot,net.minecraft.world.item.ItemStack stack,java.util.List<AccessoryInventory.Slot> accessories){
        if(stack.isEmpty())return true;
        if(slot>=36&&slot<40&&net.minecraft.world.item.enchantment.EnchantmentHelper.hasBindingCurse(stack))return false;
        if(slot<41)return true;
        var accessory=accessories.get(slot-41);
        return accessory.inventory().extractItem(accessory.index(),stack.getCount(),true).getCount()==stack.getCount();
    }
    private void writeSlot(int slot,net.minecraft.world.item.ItemStack stack,java.util.List<AccessoryInventory.Slot> accessories){
        if(slot<41)inventory.setItem(slot,stack);
        else{var accessory=accessories.get(slot-41);accessory.inventory().setStackInSlot(accessory.index(),stack);}
    }
    public void moveItem(int source,int target,boolean half){
        moveItem(source,target,half,-1);
    }
    public void moveItem(int source,int target,boolean half,int requested){
        var accessories=AccessoryInventory.slots(this);int size=41+accessories.size();
        if(source<0||target<0||source>=size||target>=size||source==target)return;
        var from=slotStack(source,accessories);var to=slotStack(target,accessories);
        if(from.isEmpty()||!canRemove(source,from,accessories))return;
        int limit=slotLimit(target,from,accessories),amount=requested>0?Math.min(requested,from.getCount()):half?(from.getCount()+1)/2:from.getCount();
        if(limit<=0)return;
        if(to.isEmpty()||net.minecraft.world.item.ItemStack.isSameItemSameTags(from,to)){
            int moved=Math.min(amount,limit-to.getCount());if(moved<=0)return;
            var remaining=from.copy();remaining.shrink(moved);var destination=from.copy();destination.setCount(to.getCount()+moved);
            writeSlot(source,remaining,accessories);writeSlot(target,destination,accessories);
        }else{
            if(amount<from.getCount()||from.getCount()>limit||to.getCount()>slotLimit(source,to,accessories)||!canRemove(target,to,accessories))return;
            writeSlot(source,to.copy(),accessories);writeSlot(target,from.copy(),accessories);
        }
        inventoryVersion++;selectSlot(selectedSlot);
    }
    public void equip(int source,int target){
        if(source<0||source>=36||target<36||source==target)return;
        if(target>=41){
            var slots=AccessoryInventory.slots(this);if(target-41>=slots.size())return;
            var slot=slots.get(target-41);var incoming=inventory.getItem(source);var old=slot.inventory().getStackInSlot(slot.index());
            if(!incoming.isEmpty()&&(!slot.inventory().isItemValid(slot.index(),incoming)||incoming.getCount()>1&&!old.isEmpty()))return;
            if(!old.isEmpty()&&slot.inventory().extractItem(slot.index(),old.getCount(),true).getCount()!=old.getCount())return;
            if(!old.isEmpty())old=slot.inventory().extractItem(slot.index(),old.getCount(),false);
            slot.inventory().setStackInSlot(slot.index(),incoming.isEmpty()?net.minecraft.world.item.ItemStack.EMPTY:incoming.split(1));
            if(incoming.isEmpty())inventory.setItem(source,old);inventoryVersion++;selectSlot(selectedSlot);return;
        }
        var stack=inventory.getItem(source);var previous=inventory.getItem(target);
        var equipment=target==40?net.minecraft.world.entity.EquipmentSlot.OFFHAND:net.minecraft.world.entity.EquipmentSlot.byTypeAndIndex(net.minecraft.world.entity.EquipmentSlot.Type.ARMOR,target-36);
        if(!stack.isEmpty()&&target!=40&&!stack.canEquip(equipment,this))return;
        if(!previous.isEmpty()&&net.minecraft.world.item.enchantment.EnchantmentHelper.hasBindingCurse(previous))return;
        if(!previous.isEmpty()&&stack.getCount()>1)return;
        inventory.setItem(target,stack.isEmpty()?net.minecraft.world.item.ItemStack.EMPTY:stack.split(target==40?stack.getCount():1));
        if(stack.isEmpty())inventory.setItem(source,previous);
        inventoryVersion++;selectSlot(selectedSlot);
    }
    public com.google.gson.JsonObject inventoryMessage(){
        var result=new com.google.gson.JsonObject();result.addProperty("type","inventory");result.addProperty("backpacks",SophisticatedBackpacks.available());result.addProperty("selected",selectedSlot);
        var slots=new com.google.gson.JsonArray();
        for(int i=0;i<inventory.getContainerSize();i++){var item=inventory.getItem(i);var slot=new com.google.gson.JsonObject();slot.addProperty("slot",i);slot.addProperty("id",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());slot.addProperty("name",item.isEmpty()?"":item.getHoverName().getString());slot.addProperty("count",item.getCount());slots.add(slot);}
        for(int i=0;i<slots.size();i++)slots.get(i).getAsJsonObject().addProperty("iconKey",dev.craftlink.video.ItemIcons.request(inventory.getItem(i)));
        var accessories=AccessoryInventory.slots(this);
        for(int i=0;i<accessories.size();i++){var accessory=accessories.get(i);var item=accessory.inventory().getStackInSlot(accessory.index());var json=new com.google.gson.JsonObject();json.addProperty("slot",41+i);json.addProperty("name",item.isEmpty()?accessory.name():item.getHoverName().getString());json.addProperty("label",accessory.name());json.addProperty("count",item.getCount());json.addProperty("iconKey",dev.craftlink.video.ItemIcons.request(item));slots.add(json);}
        result.add("slots",slots);return result;
    }
    private net.minecraft.world.item.ItemStack addToBackpack(net.minecraft.world.item.ItemStack stack){
        var backpack=new net.minecraft.world.SimpleContainer(36);
        for(int i=0;i<36;i++)backpack.setItem(i,inventory.getItem(i));
        var remainder=backpack.addItem(stack);
        for(int i=0;i<36;i++)inventory.setItem(i,backpack.getItem(i));
        return remainder;
    }
    @Override protected void pickUpItem(net.minecraft.world.entity.item.ItemEntity entity){
        if(!isAlive()||entity.hasPickUpDelay())return;
        var original=entity.getItem();int count=original.getCount();var remainder=addToBackpack(original.copy());int taken=count-remainder.getCount();
        if(taken<=0)return;take(entity,taken);if(remainder.isEmpty())entity.discard();else entity.setItem(remainder);inventoryVersion++;selectSlot(selectedSlot);
    }
    public ClassroomBot(EntityType<? extends ClassroomBot> type, Level level) {
        super(type, level);
        for(var equipment:net.minecraft.world.entity.EquipmentSlot.values())setDropChance(equipment,0);
        moveControl = new MoveControl(this) {
            @Override public void tick() {
                float x = movement!=null?(float)-movement.x():(held.contains("left") ? 1 : 0) - (held.contains("right") ? 1 : 0);
                float z = movement!=null?(float)movement.z():(held.contains("forward") ? 1 : 0) - (held.contains("back") ? 1 : 0);
                float length = Math.max(1, (float)Math.sqrt(x*x+z*z));
                setSpeed(held.contains("sneak") ? .03f : held.contains("sprint") ? .13f : .10f);
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
        if (number < 1 || number > COLORS.length) throw new IllegalArgumentException("Participant number");
        entityData.set(NUMBER, number);
        profile(ParticipantProfile.DEFAULT);
        setInvulnerable(true); setCanPickUpLoot(true);
        addTag("craftlink_bot_"+number);setCustomNameVisible(true);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(NUMBER, 1);entityData.define(FLYING,false);entityData.define(SKIN,0); }
    @Override public float getStandingEyeHeight(Pose pose,net.minecraft.world.entity.EntityDimensions dimensions){return pose==Pose.SWIMMING?.4f:pose==Pose.CROUCHING?1.27f:1.62f;}
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(Pose pose){return pose==Pose.SWIMMING?net.minecraft.world.entity.EntityDimensions.scalable(.6f,.6f):pose==Pose.CROUCHING?net.minecraft.world.entity.EntityDimensions.scalable(.6f,1.5f):super.getDimensions(pose);}
    public int participantNumber() { return entityData.get(NUMBER); }
    @Override public void aiStep() { updateSwingTime(); super.aiStep(); }
    public void accept(RemoteState.Snapshot input,ClassroomRoom.Move movement) {
        this.movement=movement;
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
    @Override protected net.minecraft.world.phys.Vec3 maybeBackOffFromEdge(net.minecraft.world.phys.Vec3 delta,net.minecraft.world.entity.MoverType mover){
        if(flying()||!isShiftKeyDown()||!onGround()||delta.y>0||mover!=net.minecraft.world.entity.MoverType.SELF&&mover!=net.minecraft.world.entity.MoverType.PLAYER)return delta;
        double x=delta.x,z=delta.z,step=Math.max(.6,maxUpStep());
        while(x!=0&&level().noCollision(this,getBoundingBox().move(x,-step,0)))x=Math.abs(x)<.05?0:x-Math.copySign(.05,x);
        while(z!=0&&level().noCollision(this,getBoundingBox().move(0,-step,z)))z=Math.abs(z)<.05?0:z-Math.copySign(.05,z);
        while(x!=0&&z!=0&&level().noCollision(this,getBoundingBox().move(x,-step,z))){x=Math.abs(x)<.05?0:x-Math.copySign(.05,x);z=Math.abs(z)<.05?0:z-Math.copySign(.05,z);}
        return new net.minecraft.world.phys.Vec3(x,delta.y,z);
    }
    public void dropItem(int slot,boolean all){
        dropItem(slot,all,-1);
    }
    public void dropItem(int slot,boolean all,int requested){
        if(slot<0||slot>40)throw new IllegalArgumentException("Invalid drop slot");
        var current=inventory.getItem(slot);if(current.isEmpty()||slot>=36&&slot<40&&net.minecraft.world.item.enchantment.EnchantmentHelper.hasBindingCurse(current))return;
        var stack=inventory.removeItem(slot,requested>0?Math.min(current.getCount(),requested):all?current.getCount():1);var eyes=getEyePosition();
        var entity=new net.minecraft.world.entity.item.ItemEntity(level(),eyes.x,eyes.y-.3,eyes.z,stack);
        entity.setDeltaMovement(getLookAngle().scale(.3).add(0,.15,0));entity.setDefaultPickUpDelay();entity.setThrower(getUUID());
        if(!level().addFreshEntity(entity))addToBackpack(stack);
        inventoryVersion++;selectSlot(selectedSlot);
    }
    public void copyInventoryTo(Player player){
        for(int i=0;i<41;i++)if(!net.minecraft.world.item.ItemStack.matches(player.getInventory().getItem(i),inventory.getItem(i)))player.getInventory().setItem(i,inventory.getItem(i).copy());
        player.getInventory().selected=selectedSlot;
    }
    public void copyInventoryFrom(Player player){
        boolean changed=false;
        for(int i=0;i<41;i++){var next=player.getInventory().getItem(i);if(!net.minecraft.world.item.ItemStack.matches(inventory.getItem(i),next)){inventory.setItem(i,next.copy());changed=true;}}
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
