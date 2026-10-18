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
    private Vec3 previousPosition;
    private boolean previouslyGrounded;
    private long chargeStarted=-1;
    private long weaponAttackStarted=-1;
    private net.minecraft.world.item.ItemStack chargingWeapon=net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack attributeHand=net.minecraft.world.item.ItemStack.EMPTY;
    private void syncWeaponAttributes(){
        var current=player.getMainHandItem();
        if(net.minecraft.world.item.ItemStack.matches(attributeHand,current))return;
        player.getAttributes().removeAttributeModifiers(attributeHand.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        player.getAttributes().addTransientAttributeModifiers(current.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        attributeHand=current.copy();
    }
    public void cancelSkill(){chargeStarted=-1;weaponAttackStarted=-1;chargingWeapon=net.minecraft.world.item.ItemStack.EMPTY;}
    static boolean isOwnActor(ClassroomBot bot,net.minecraft.world.entity.Entity entity){return entity instanceof ActionPlayer actor&&actor.wearer==bot;}
    private static final class ActionPlayer extends FakePlayer {
        int attackTicks=20;
        ClassroomBot wearer;
        @Override public boolean isAlliedTo(net.minecraft.world.entity.Entity other){return other==wearer||super.isAlliedTo(other);}
        @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other){
            if(wearer==null||other==this||other.getUUID().equals(getUUID()))return false;
            var team=wearer.getTeam();
            return team==null||!team.isAlliedTo(other.getTeam())||team.isAllowFriendlyFire();
        }
        boolean menuVisible;
        int nextMenu;
        String menuTitle="クラフト";
        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider){
            closeContainer();var menu=provider.createMenu(nextMenu=nextMenu%100+1,getInventory(),this);
            if(menu==null)return java.util.OptionalInt.empty();
            containerMenu=menu;menuTitle=provider.getDisplayName().getString();menuVisible=true;
            return java.util.OptionalInt.of(menu.containerId);
        }
        @Override public void closeContainer(){super.closeContainer();menuVisible=false;}
        @Override public boolean isHurt(){return wearer==null?super.isHurt():wearer.getHealth()>0&&wearer.getHealth()<wearer.getMaxHealth();}
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
            if(wearer==null)return false;boolean result=wearer.hurt(source,amount);setHealth(wearer.getHealth());return result;
        }
        @Override public void heal(float amount){
            if(wearer==null){super.heal(amount);return;}wearer.heal(amount);setHealth(wearer.getHealth());
        }
        ActionPlayer(ServerLevel level,GameProfile profile){super(level,profile);}
        void advanceUse(boolean held){
            var current=getItemInHand(getUsedItemHand());
            if(!net.minecraftforge.common.ForgeHooks.canContinueUsing(useItem,current)){stopUsingItem();return;}
            useItem=current;if(held)updateUsingItem(useItem);else releaseUsingItem();
        }
        @Override public float getAttackStrengthScale(float partial){return net.minecraft.util.Mth.clamp((attackTicks+partial)/(20f/(float)getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)),0,1);}
        @Override public void resetAttackStrengthTicker(){attackTicks=0;super.resetAttackStrengthTicker();}
    }
    public BotBlockActions(ServerLevel level,java.util.UUID id,int number){
        this.level=level;
        player=new ActionPlayer(level,new GameProfile(id,"CraftLink_"+number));
        gameMode=player.gameMode;
    }
    public void tickStatus(ClassroomBot bot,ClassroomRoom.Mode mode){
        player.wearer=bot;player.setPos(bot.getX(),bot.getY(),bot.getZ());
        player.setYRot(bot.getYRot());player.setXRot(bot.getXRot());player.setYHeadRot(bot.getYRot());player.setShiftKeyDown(bot.isShiftKeyDown());player.setOnGround(bot.onGround());
        player.tickCount=bot.tickCount;
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(bot.getMaxHealth());
        player.setHealth(bot.getHealth());bot.copyInventoryTo(player);syncWeaponAttributes();
        for(var effect:java.util.List.copyOf(player.getActiveEffects()))if(!bot.hasEffect(effect.getEffect()))player.removeEffect(effect.getEffect());
        for(var effect:bot.getActiveEffects()){
            var existing=player.getEffect(effect.getEffect());
            if(existing==null||existing.getAmplifier()!=effect.getAmplifier()){
                player.removeEffect(effect.getEffect());player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
            }else player.getActiveEffectsMap().put(effect.getEffect(),new net.minecraft.world.effect.MobEffectInstance(effect));
        }
        if(mode==ClassroomRoom.Mode.SURVIVAL){
            if(previousPosition!=null){
                double distance=bot.position().distanceTo(previousPosition);
                if(distance<4&&!bot.flying())player.causeFoodExhaustion((float)(distance*(bot.isSwimming()?.01:bot.isSprinting()?.1:0)));
                if(previouslyGrounded&&!bot.onGround()&&bot.getDeltaMovement().y>0)player.causeFoodExhaustion(bot.isSprinting()?.2f:.05f);
            }
            var hunger=bot.getEffect(net.minecraft.world.effect.MobEffects.HUNGER);
            if(hunger!=null)player.getFoodData().addExhaustion(.005f*(hunger.getAmplifier()+1));
            player.getFoodData().tick(player);
        }
        previousPosition=bot.position();previouslyGrounded=bot.onGround();
        if(player.takeXpDelay>0)player.takeXpDelay--;
        for(var orb:level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,bot.getBoundingBox().inflate(.5)))if(orb.isAlive())orb.playerTouch(player);
        if(WeaponSkills.available()){
            net.minecraftforge.event.ForgeEventFactory.onPlayerPreTick(player);
            net.minecraftforge.event.ForgeEventFactory.onPlayerPostTick(player);
            transferEffects(bot);
            if(player.getDeltaMovement().lengthSqr()>0){bot.setDeltaMovement(player.getDeltaMovement());player.setDeltaMovement(Vec3.ZERO);}
        }
        bot.copyInventoryFrom(player);
    }
    private String lastMenu="";
    public void ui(ClassroomBot bot,ClassroomRoom.UiAction action,ClassroomRoom room,java.util.UUID peer){
        var menu=player.containerMenu;
        switch(action.kind()){
            case "skillStart" -> {if(chargeStarted<0&&!player.menuVisible)chargeStarted=level.getGameTime();}
            case "skillCancel" -> chargeStarted=-1;
            case "skillRelease" -> {
                if(chargeStarted<0)return;long ticks=level.getGameTime()-chargeStarted;chargeStarted=-1;if(player.menuVisible)return;
                WeaponSkills.attack(player,ticks>=20?Math.min(1,ticks/60f):-1);bot.swing(InteractionHand.MAIN_HAND);transferEffects(bot);bot.copyInventoryFrom(player);
            }
            case "sheathe" -> {if(!player.menuVisible){WeaponSkills.sheathe(player);bot.copyInventoryFrom(player);}}
            case "backpack" -> {
                if(player.menuVisible)return;
                String error=SophisticatedBackpacks.open(bot,player);
                bot.copyInventoryFrom(player);
                if(error!=null){var status=new com.google.gson.JsonObject();status.addProperty("type","backpackStatus");status.addProperty("message",error);room.publishInventory(peer,status);}
            }
            case "craft" -> {player.closeContainer();player.menuVisible=true;player.menuTitle="クラフト";}
            case "close" -> {if(action.menu()==menu.containerId){player.closeContainer();bot.copyInventoryFrom(player);}}
            case "click","quick","collect" -> {
                if(!player.menuVisible||action.menu()!=menu.containerId||!menu.stillValid(player))return;
                if(action.slot()!= -999&&(action.slot()<0||action.slot()>=menu.slots.size()))return;
                menu.clicked(action.slot(),action.button(),action.kind().equals("quick")?net.minecraft.world.inventory.ClickType.QUICK_MOVE:action.kind().equals("collect")?net.minecraft.world.inventory.ClickType.PICKUP_ALL:net.minecraft.world.inventory.ClickType.PICKUP,player);
                menu.broadcastChanges();bot.copyInventoryFrom(player);
            }
            case "recipe" -> {
                if(!player.menuVisible||action.menu()!=menu.containerId||!menu.stillValid(player)||!(menu instanceof net.minecraft.world.inventory.RecipeBookMenu<?> book))return;
                var id=net.minecraft.resources.ResourceLocation.tryParse(action.text());if(id==null)return;
                var recipe=level.getRecipeManager().byKey(id).orElse(null);
                if(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting&&crafting.canCraftInDimensions(book.getGridWidth(),book.getGridHeight())){
                    player.awardRecipes(java.util.List.of(recipe));book.handlePlacement(false,recipe,player);bot.copyInventoryFrom(player);
                }
            }
            case "chat" -> {
                String text=net.minecraft.SharedConstants.filterText(action.text()).strip();if(text.isEmpty())return;
                if(text.startsWith("/")){
                    int result=level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(0),text.substring(1));
                    var response=new com.google.gson.JsonObject();response.addProperty("type","chat");response.addProperty("text",result>0?"コマンドを実行しました":"実行できませんでした。コマンド名と権限を確認してください");room.publishInventory(peer,response);
                }else{
                    String message="<"+bot.getName().getString()+"> "+text;level.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(message),false);room.chat(message);
                }
            }
        }
    }
    public void close(ClassroomBot bot){cancel();bot.copyInventoryTo(player);player.closeContainer();bot.copyInventoryFrom(player);}
    public void publishMenu(ClassroomRoom room,java.util.UUID peer){
        if(level.getGameTime()%5!=0)return;
        var menu=player.containerMenu;
        if(player.menuVisible&&!menu.stillValid(player))player.closeContainer();
        var result=new com.google.gson.JsonObject();result.addProperty("type","menu");result.addProperty("open",player.menuVisible);result.addProperty("id",menu.containerId);result.addProperty("title",player.menuTitle);
        var slots=new com.google.gson.JsonArray();
        if(player.menuVisible){
            int left=Math.min(0,menu.slots.stream().mapToInt(slot->slot.x).min().orElse(0));
            int top=Math.min(0,menu.slots.stream().mapToInt(slot->slot.y).min().orElse(0));
            for(var slot:menu.slots){if(slots.size()>=256)break;var stack=slot.getItem();var value=new com.google.gson.JsonObject();value.addProperty("slot",slot.index);value.addProperty("x",slot.x-left);value.addProperty("y",slot.y-top);value.addProperty("name",stack.isEmpty()?"":stack.getHoverName().getString());value.addProperty("count",stack.getCount());value.addProperty("iconKey",dev.craftlink.video.ItemIcons.request(stack));slots.add(value);}
            var cursor=menu.getCarried();result.addProperty("carried",cursor.isEmpty()?"":cursor.getHoverName().getString()+" ×"+cursor.getCount());
            var recipes=new com.google.gson.JsonArray();
            if(menu instanceof net.minecraft.world.inventory.RecipeBookMenu<?> book){
                var contents=new net.minecraft.world.entity.player.StackedContents();player.getInventory().fillStackedContents(contents);book.fillCraftSlotsStackedContents(contents);
                for(var recipe:level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)){
                    if(recipes.size()>=256)break;
                    if(!recipe.canCraftInDimensions(book.getGridWidth(),book.getGridHeight())||!contents.canCraft(recipe,null))continue;
                    var output=recipe.getResultItem(level.registryAccess());var value=new com.google.gson.JsonObject();value.addProperty("id",recipe.getId().toString());value.addProperty("name",output.getHoverName().getString());recipes.add(value);
                }
            }
            result.add("recipes",recipes);
        }
        result.add("slots",slots);String serialized=result.toString();
        if(!serialized.equals(lastMenu)){lastMenu=serialized;dev.craftlink.video.ItemIcons.send(room,peer,result);}
    }
    private void transferEffects(ClassroomBot bot){
        for(var effect:java.util.List.copyOf(bot.getActiveEffects()))if(!player.hasEffect(effect.getEffect()))bot.removeEffect(effect.getEffect());
        for(var effect:java.util.List.copyOf(player.getActiveEffects())){
            var existing=bot.getEffect(effect.getEffect());
            if(existing==null||existing.getAmplifier()!=effect.getAmplifier()||effect.getDuration()>existing.getDuration()||effect.isInfiniteDuration()&&!existing.isInfiniteDuration())bot.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
        }
    }
    public com.google.gson.JsonObject status(ClassroomBot bot,ClassroomRoom.Mode mode){
        var result=new com.google.gson.JsonObject();result.addProperty("type","vitals");result.addProperty("survival",mode==ClassroomRoom.Mode.SURVIVAL);result.addProperty("inWater",bot.isInWater());
        result.addProperty("health",bot.getHealth());result.addProperty("maxHealth",bot.getMaxHealth());result.addProperty("absorption",bot.getAbsorptionAmount());result.addProperty("armor",bot.getArmorValue());
        result.addProperty("food",player.getFoodData().getFoodLevel());result.addProperty("level",player.experienceLevel);result.addProperty("progress",player.experienceProgress);
        var effects=new com.google.gson.JsonArray();
        for(var effect:bot.getActiveEffects()){
            var json=new com.google.gson.JsonObject();json.addProperty("id",net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()).toString());
            json.addProperty("name",net.minecraft.network.chat.Component.translatable(effect.getDescriptionId()).getString());json.addProperty("amplifier",effect.getAmplifier());json.addProperty("duration",effect.getDuration());json.addProperty("color",effect.getEffect().getColor());effects.add(json);
        }
        result.add("effects",effects);return result;
    }
    public void tick(ClassroomBot bot,RemoteState.Snapshot input,ClassroomRoom.Mode mode,ClassroomRoom.Aim aim){
        GameType requested=mode==ClassroomRoom.Mode.CREATIVE?GameType.CREATIVE:GameType.SURVIVAL;
        player.attackTicks=Math.min(100,player.attackTicks+1);player.getCooldowns().tick();
        if(gameMode.getGameModeForPlayer()!=requested){player.stopUsingItem();cancel();cancelSkill();((dev.craftlink.mixin.BlockActionAccessor)gameMode).craftlink$mode(requested,gameMode.getGameModeForPlayer());}
        if(player.menuVisible){cancel();cancelSkill();return;}
        boolean tapped=input.presses().contains("tap")||input.held().contains("tap");
        boolean chargeHeld=input.held().contains("attack")&&bot.getMainHandItem().getUseDuration()>0;
        boolean useHeld=input.held().contains("use")||chargeHeld;
        if(weaponAttackStarted<0 && breaking==null && !tapped && !player.isUsingItem() && !input.held().contains("attack") && !input.held().contains("use") && !input.presses().contains("attack") && !input.presses().contains("use")){if(cooldown>0)cooldown--;return;}
        player.setPos(bot.getX(),bot.getY(),bot.getZ());player.setYRot(bot.getYRot());player.setXRot(bot.getXRot());
        player.setShiftKeyDown(bot.isShiftKeyDown());player.setPose(bot.getPose());player.setOnGround(bot.onGround());
        bot.copyInventoryTo(player);syncWeaponAttributes();
        if(player.isUsingItem()){
            cancel();
            player.advanceUse(useHeld);transferEffects(bot);
            bot.copyInventoryFrom(player);return;
        }
        Vec3 origin=bot.getEyePosition();
        Vec3 direction=bot.getLookAngle();
        if(aim!=null){
            var view=aim.view();if(origin.distanceToSqr(new Vec3(view.eyeX(),view.eyeY(),view.eyeZ()))>4){cancel();return;}
            var ray=BotTouchRay.direction(aim);direction=new Vec3(ray.x,ray.y,ray.z);
            player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));player.setYHeadRot(player.getYRot());
            player.setXRot((float)-Math.toDegrees(Math.asin(net.minecraft.util.Mth.clamp(direction.y,-1,1))));
        }
        BlockHitResult hit=level.clip(new ClipContext(origin,origin.add(direction.scale(player.getBlockReach())),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        boolean valid=hit.getType()==HitResult.Type.BLOCK && level.hasChunkAt(hit.getBlockPos()) && level.getWorldBorder().isWithinBounds(hit.getBlockPos()) && level.mayInteract(player,hit.getBlockPos());
        double reach=player.getEntityReach();var end=origin.add(direction.scale(reach));
        var entityHit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player,origin,end,bot.getBoundingBox().expandTowards(direction.scale(reach)).inflate(1),
            entity->entity!=bot && entity.isAlive() && entity.isPickable() && !entity.isSpectator()
                && (!(entity instanceof net.minecraft.world.entity.player.Player target)||player.canHarmPlayer(target)),
            hit.getType()==HitResult.Type.BLOCK?Math.min(reach*reach,origin.distanceToSqr(hit.getLocation())):reach*reach);
        boolean weapon=WeaponSkills.supports(player.getMainHandItem())&&!chargeHeld;
        if(weaponAttackStarted>=0&&!net.minecraft.world.item.ItemStack.isSameItemSameTags(chargingWeapon,player.getMainHandItem())){weaponAttackStarted=-1;chargingWeapon=net.minecraft.world.item.ItemStack.EMPTY;}
        if(weaponAttackStarted>=0&&!input.held().contains("attack")){
            long ticks=level.getGameTime()-weaponAttackStarted;weaponAttackStarted=-1;chargingWeapon=net.minecraft.world.item.ItemStack.EMPTY;
            if(weapon&&ticks>=20){WeaponSkills.attack(player,Math.min(1,ticks/60f));bot.swing(InteractionHand.MAIN_HAND);transferEffects(bot);bot.copyInventoryFrom(player);return;}
        }
        if(weapon&&!useHeld&&(input.held().contains("attack")||input.presses().contains("attack")||tapped&&!valid&&entityHit==null)){
            cancel();
            if(input.presses().contains("attack")||weaponAttackStarted<0){
                WeaponSkills.attack(player,-1);bot.swing(InteractionHand.MAIN_HAND);
                if(input.held().contains("attack")){weaponAttackStarted=level.getGameTime();chargingWeapon=player.getMainHandItem().copy();}
            }
            transferEffects(bot);bot.copyInventoryFrom(player);return;
        }
        if(cooldown>0)cooldown--;
        if(!useHeld&&(input.held().contains("attack") || input.presses().contains("attack")||tapped&&entityHit!=null)){
            if(entityHit!=null){cancel();if(cooldown==0&&player.getAttackStrengthScale(0)>=.99f){if(!WeaponSkills.attack(player,-1))player.attack(entityHit.getEntity());bot.swing(InteractionHand.MAIN_HAND);cooldown=4;}}
            else if(valid && cooldown==0)mine(bot,hit);else cancel();
        }else cancel();
        if(cooldown==0 && (useHeld||input.presses().contains("use")||tapped&&entityHit==null)){
            cancel();
            for(var hand:InteractionHand.values()){
                var result=entityHit!=null?player.interactOn(entityHit.getEntity(),hand):valid&&!chargeHeld?gameMode.useItemOn(player,level,player.getItemInHand(hand),hand,hit):net.minecraft.world.InteractionResult.PASS;
                if(result==net.minecraft.world.InteractionResult.FAIL)break;
                if(!result.consumesAction())result=gameMode.useItem(player,level,player.getItemInHand(hand),hand);
                if(result.consumesAction()){bot.swing(hand);break;}
            }
            cooldown=4;
        }
        transferEffects(bot);bot.copyInventoryFrom(player);
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
