package dev.craftlink.mixin;

import dev.craftlink.CraftLink.Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftInputMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void craftlink$interactions(CallbackInfo ci) {
        Client.INPUT.interactions((Minecraft)(Object)this, (MinecraftActions)(Object)this);
    }
    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
    private boolean craftlink$continueMining(boolean keyboardAttack) {
        return keyboardAttack || Client.INPUT.continueAttack();
    }
    @Inject(method = "continueAttack", at = @At("HEAD"))
    private void craftlink$touchTarget(boolean held, CallbackInfo ci) { Client.INPUT.videoTouch.beforeMining((Minecraft)(Object)this); }
    @Inject(method = "continueAttack", at = @At("RETURN"))
    private void craftlink$restoreTarget(boolean held, CallbackInfo ci) { Client.INPUT.videoTouch.afterMining((Minecraft)(Object)this); }
    // Vanilla releases item use when the physical mouse is up. Remote hold is a separate owner.
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"))
    private void craftlink$releaseUse(MultiPlayerGameMode gameMode, Player player) {
        if (!Client.INPUT.held("use")) gameMode.releaseUsingItem(player);
    }
}
