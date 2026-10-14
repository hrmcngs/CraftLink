package dev.craftlink.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.craftlink.CraftLink.Client;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class VideoProjectionMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void craftlink$projection(PoseStack pose, float partial, long deadline, boolean outline, Camera camera,
                                      GameRenderer renderer, LightTexture light, Matrix4f projection, CallbackInfo ci) {
        if (Client.VIDEO.active()) Client.VIDEO.projection(new Matrix4f(projection).mul(pose.last().pose()).invert());
    }
}
