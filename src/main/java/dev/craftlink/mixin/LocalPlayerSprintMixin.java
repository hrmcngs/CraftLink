package dev.craftlink.mixin;

import dev.craftlink.CraftLink.Client;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerSprintMixin {
    // In 1.20.1 the two isDown reads in aiStep are both sprint intent checks.
    // Augment the decision, not the KeyMapping state; vanilla still checks food, collisions, etc.
    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"), require = 2)
    private boolean craftlink$sprintIntent(KeyMapping physicalSprint) {
        return physicalSprint.isDown() || Client.INPUT.held("sprint");
    }
}
