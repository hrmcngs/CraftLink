package dev.craftlink.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** A visible player-owned Curios entry point on every creative category too. */
public final class PlayerAccessoriesIntegration {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void initialize(ScreenEvent.Init.Post event){
        if(!(event.getScreen() instanceof InventoryScreen||event.getScreen() instanceof CreativeModeInventoryScreen))return;
        var screen=(AbstractContainerScreen<?>)event.getScreen();
        for(var listener:java.util.List.copyOf(event.getListenersList())){
            if(listener instanceof AbstractButton original&&listener.getClass().getName().equals("top.theillusivec4.curios.client.gui.CuriosButton")){
                event.removeListener(listener);
                event.addListener(new AccessoriesButton(screen,original));
                break;
            }
        }
    }
    public static final class AccessoriesButton extends Button {
        private final AbstractContainerScreen<?> parent;
        private AccessoriesButton(AbstractContainerScreen<?> parent,AbstractButton original){
            super(0,0,72,20,Component.literal("アクセサリー"),button->original.onPress(),DEFAULT_NARRATION);
            this.parent=parent;setTooltip(Tooltip.create(Component.literal("プレイヤーのアクセサリーを開く")));
            position();
        }
        private void position(){
            int x=parent.getGuiLeft()+parent.getXSize()+4;
            int available=parent.width-x-4;
            if(available>=24){setX(x);setY(Math.max(4,Math.min(parent.height-24,parent.getGuiTop()+40)));setWidth(Math.min(72,available));}
            else{setX(Math.max(4,parent.width-76));setY(Math.max(4,parent.height-24));setWidth(72);}
            setMessage(Component.literal(getWidth()<48?"◇":"アクセサリー"));
        }
        @Override public void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partial){position();super.renderWidget(graphics,mouseX,mouseY,partial);}
    }
    private PlayerAccessoriesIntegration(){}
}
