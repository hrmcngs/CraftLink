package dev.craftlink.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ControlsIntegration {
    @SubscribeEvent public static void initialize(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ControlsScreen screen)) return;
        // Insert a vanilla-sized row before Done, keeping all existing controls intact.
        for (var listener : event.getListenersList()) {
            if (listener instanceof Button done && done.getMessage().equals(CommonComponents.GUI_DONE)) {
                int row = done.getY();
                done.setY(row + 24);
                event.addListener(Button.builder(Component.translatable("craftlink.controls.title"),
                    b -> Minecraft.getInstance().setScreen(new TouchSettingsScreen(screen)))
                    .bounds(screen.width / 2 - 155, row, 310, 20).build());
                break;
            }
        }
    }
    private ControlsIntegration() {}
}
