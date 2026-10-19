package dev.craftlink.mixin;
import dev.craftlink.classroom.client.UploadedSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayer.class)
public abstract class LocalSkinMixin {
 @Inject(method="getSkinTextureLocation",at=@At("HEAD"),cancellable=true)
 private void craftlink$skin(CallbackInfoReturnable<ResourceLocation> callback){if((Object)this==Minecraft.getInstance().player){var texture=UploadedSkins.location(UploadedSkins.localId());if(texture!=null)callback.setReturnValue(texture);}}
 @Inject(method="getModelName",at=@At("HEAD"),cancellable=true)
 private void craftlink$model(CallbackInfoReturnable<String> callback){if((Object)this==Minecraft.getInstance().player&&UploadedSkins.location(UploadedSkins.localId())!=null)callback.setReturnValue(UploadedSkins.slim(UploadedSkins.localId())?"slim":"default");}
}
