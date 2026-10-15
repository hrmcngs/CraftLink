package dev.craftlink.classroom;

import com.mojang.authlib.GameProfile;
import dev.craftlink.input.RemoteState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

/** One non-spawned action player per BOT. Vanilla/Forge checks, loot and tools run on the server. */
public final class BotBlockActions {
    private final ServerLevel level;
    private final ActionPlayer player;
    private final ServerPlayerGameMode gameMode;
    private BlockPos breaking;
    private Direction face=Direction.UP;
    private float progress;
    private int cooldown, sequence;
    private static final class ActionPlayer extends FakePlayer {
        int attackTicks=20;
        ActionPlayer(ServerLevel level,GameProfile profile){super(level,profile);}
        @Override public float getAttackStrengthScale(float partial){return net.minecraft.util.Mth.clamp((attackTicks+partial)/(20f/(float)getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)),0,1);}
        @Override public void resetAttackStrengthTicker(){attackTicks=0;super.resetAttackStrengthTicker();}
    }
    public BotBlockActions(ServerLevel level,java.util.UUID id,int number){
        this.level=level;
        player=new ActionPlayer(level,new GameProfile(id,"CraftLink_"+number));
        gameMode=player.gameMode;
    }
    public void tick(ClassroomBot bot,RemoteState.Snapshot input,ClassroomRoom.Mode mode,ClassroomRoom.Aim aim){
        GameType requested=mode==ClassroomRoom.Mode.CREATIVE?GameType.CREATIVE:GameType.SURVIVAL;
        player.attackTicks=Math.min(100,player.attackTicks+1);player.getCooldowns().tick();
        if(gameMode.getGameModeForPlayer()!=requested){cancel();((dev.craftlink.mixin.BlockActionAccessor)gameMode).craftlink$mode(requested,gameMode.getGameModeForPlayer());}
        if(breaking==null && !input.held().contains("attack") && !input.held().contains("use") && !input.presses().contains("attack") && !input.presses().contains("use")){if(cooldown>0)cooldown--;return;}
        player.setPos(bot.getX(),bot.getY(),bot.getZ());player.setYRot(bot.getYRot());player.setXRot(bot.getXRot());
        player.setShiftKeyDown(bot.isShiftKeyDown());player.setPose(bot.getPose());player.setOnGround(bot.onGround());
        var oldHand=player.getMainHandItem().copy();bot.copyInventoryTo(player);
        if(!net.minecraft.world.item.ItemStack.matches(oldHand,player.getMainHandItem())){
            player.getAttributes().removeAttributeModifiers(oldHand.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
            player.getAttributes().addTransientAttributeModifiers(player.getMainHandItem().getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        }
        Vec3 origin=bot.getEyePosition();
        Vec3 direction=bot.getLookAngle();
        if(aim!=null){
            var view=aim.view();if(origin.distanceToSqr(new Vec3(view.eyeX(),view.eyeY(),view.eyeZ()))>4){cancel();return;}
            var ray=BotTouchRay.direction(aim);direction=new Vec3(ray.x,ray.y,ray.z);
        }
        BlockHitResult hit=level.clip(new ClipContext(origin,origin.add(direction.scale(player.getBlockReach())),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        boolean valid=hit.getType()==HitResult.Type.BLOCK && level.hasChunkAt(hit.getBlockPos()) && level.getWorldBorder().isWithinBounds(hit.getBlockPos()) && level.mayInteract(player,hit.getBlockPos());
        double reach=player.getEntityReach();var end=origin.add(direction.scale(reach));
        var entityHit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player,origin,end,bot.getBoundingBox().expandTowards(direction.scale(reach)).inflate(1),
            entity->entity!=bot && !(entity instanceof ClassroomBot) && !(entity instanceof net.minecraft.world.entity.player.Player) && entity.isAlive() && entity.isPickable() && !entity.isSpectator(),
            hit.getType()==HitResult.Type.BLOCK?Math.min(reach*reach,origin.distanceToSqr(hit.getLocation())):reach*reach);
        if(cooldown>0)cooldown--;
        if(input.held().contains("attack") || input.presses().contains("attack")){
            if(entityHit!=null){cancel();if(cooldown==0&&player.getAttackStrengthScale(0)>=.99f){player.attack(entityHit.getEntity());bot.swing(InteractionHand.MAIN_HAND);}}
            else if(valid && cooldown==0)mine(bot,hit);else cancel();
        }else cancel();
        if(valid && cooldown==0 && (input.held().contains("use") || input.presses().contains("use"))){
            cancel();
            var result=gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            if(result.consumesAction())bot.swing(InteractionHand.MAIN_HAND);
            player.closeContainer();cooldown=4;
        }
        bot.copyInventoryFrom(player);
    }
    private void mine(ClassroomBot bot,BlockHitResult hit){
        BlockPos pos=hit.getBlockPos();
        if(!pos.equals(breaking)){
            cancel();breaking=pos.immutable();face=hit.getDirection();progress=0;
            gameMode.handleBlockBreakAction(pos,Action.START_DESTROY_BLOCK,face,level.getMaxBuildHeight(),++sequence);
            if(gameMode.isCreative() || level.getBlockState(pos).isAir()){breaking=null;cooldown=5;return;}
        }
        if(!((dev.craftlink.mixin.BlockActionAccessor)gameMode).craftlink$destroying()){breaking=null;progress=0;return;}
        gameMode.tick();
        var state=level.getBlockState(pos);
        progress+=state.getDestroyProgress(player,level,pos);
        bot.swing(InteractionHand.MAIN_HAND);
        if(progress>=1){
            // The vanilla server validates elapsed ticks and fires Forge's break event.
            gameMode.handleBlockBreakAction(pos,Action.STOP_DESTROY_BLOCK,face,level.getMaxBuildHeight(),++sequence);
            cancel();cooldown=5;
        }
    }
    public void cancel(){
        ((dev.craftlink.mixin.BlockActionAccessor)gameMode).craftlink$delayed(false);
        if(breaking!=null){gameMode.handleBlockBreakAction(breaking,Action.ABORT_DESTROY_BLOCK,face,level.getMaxBuildHeight(),++sequence);level.destroyBlockProgress(player.getId(),breaking,-1);breaking=null;progress=0;}
    }
}
