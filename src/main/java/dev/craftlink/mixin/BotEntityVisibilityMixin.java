package dev.craftlink.mixin;

import dev.craftlink.CraftLink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

@Mixin(LevelRenderer.class)
public abstract class BotEntityVisibilityMixin {
    /** The PC camera's compiled-section gate must not hide actors from a different camera. */
    @Redirect(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/LevelRenderer;isChunkCompiled(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean craftlink$botEntitySection(LevelRenderer renderer,BlockPos pos){
        var mc=Minecraft.getInstance();
        return CraftLink.Client.BOT_VIDEO.rendering() ? mc.level!=null && mc.level.hasChunkAt(pos) : renderer.isChunkCompiled(pos);
    }
    @Inject(method="renderEntity",at=@At("HEAD"))
    private void craftlink$actorRendered(Entity entity,double x,double y,double z,float partial,PoseStack pose,MultiBufferSource buffers,CallbackInfo ci){
        CraftLink.Client.BOT_VIDEO.entityRendered(entity);
    }
}
