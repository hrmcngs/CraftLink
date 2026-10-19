package dev.craftlink.mixin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(AbstractContainerScreen.class)
public interface NativeMenuAccess {
 @Invoker("renderBg") void craftlink$background(GuiGraphics graphics,float partial,int x,int y);
 @Invoker("renderLabels") void craftlink$labels(GuiGraphics graphics,int x,int y);
}
