package dev.craftlink.mixin;
import dev.craftlink.addon.AddonActions;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Query-only bridge; physical down state, modifiers and click counts remain untouched. */
@Mixin(KeyMapping.class)
abstract class AddonKeyMappingMixin {
    @Inject(method = "isDown", at = @At("RETURN"), cancellable = true)
    private void craftlink$addonDown(CallbackInfoReturnable<Boolean> ci) {
        KeyMapping key = (KeyMapping)(Object)this;
        if (!ci.getReturnValue() && key.getKeyConflictContext().isActive() && AddonActions.held(key)) ci.setReturnValue(true);
    }
    @Inject(method = "consumeClick", at = @At("RETURN"), cancellable = true)
    private void craftlink$addonClick(CallbackInfoReturnable<Boolean> ci) {
        KeyMapping key = (KeyMapping)(Object)this;
        // Consume our edge even if a simultaneous physical click already satisfied the read.
        if (AddonActions.consume(key) && key.getKeyConflictContext().isActive()) ci.setReturnValue(true);
    }
}
