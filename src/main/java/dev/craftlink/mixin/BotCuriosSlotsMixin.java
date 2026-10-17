package dev.craftlink.mixin;

import java.util.Map;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Curios gates its capability and item validators on entity slot definitions. */
@Pseudo
@Mixin(targets="top.theillusivec4.curios.mixin.CuriosImplMixinHooks", remap=false)
abstract class BotCuriosSlotsMixin {
    @Inject(method="getEntitySlots", at=@At("HEAD"), cancellable=true, remap=false)
    private static void craftlink$playerSlots(EntityType<?> type, boolean client, CallbackInfoReturnable<Map<?,?>> result) {
        if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals("craftlink:classroom_bot")) return;
        try {
            var api=Class.forName("top.theillusivec4.curios.api.CuriosApi");
            result.setReturnValue((Map<?,?>)api.getMethod("getPlayerSlots",boolean.class).invoke(null,client));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot inherit Curios player slots for BOT",e);
        }
    }
}
