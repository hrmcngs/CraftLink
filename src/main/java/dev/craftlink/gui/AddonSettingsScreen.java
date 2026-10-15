package dev.craftlink.gui;
import dev.craftlink.addon.AddonActions;
import dev.craftlink.config.LinkConfig;
import dev.craftlink.CraftLink.Client;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** PC-only opt-in: phones cannot add arbitrary key bindings or callbacks. */
public final class AddonSettingsScreen extends Screen {
    private final Screen parent;
    private int page;
    private boolean empty;
    public AddonSettingsScreen(Screen parent) { super(Component.translatable("craftlink.addons.title")); this.parent = parent; }
    @Override protected void init() {
        var available = AddonActions.available(minecraft);
        empty = available.isEmpty();
        int rows = Math.max(1, (height - 112) / 24), pages = Math.max(1, (available.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (int i = page * rows; i < Math.min(available.size(), (page + 1) * rows); i++) {
            var action = available.get(i);
            boolean selected = LinkConfig.ADDON_ACTIONS.get().contains(action.id());
            var label = Component.literal(font.plainSubstrByWidth(action.label(), 230)).append(": ").append(selected ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
            var button = Button.builder(label, b -> {
                var values = new ArrayList<String>(LinkConfig.ADDON_ACTIONS.get());
                if (!values.remove(action.id()) && values.size() < 16) values.add(action.id());
                LinkConfig.ADDON_ACTIONS.set(values);
                Client.SERVER.setAddonActions(AddonActions.enabled(minecraft));
                rebuildWidgets();
            }).bounds(width / 2 - 155, 48 + (i % rows) * 24, 310, 20).tooltip(Tooltip.create(Component.literal(action.id()))).build();
            button.active = selected || LinkConfig.ADDON_ACTIONS.get().size() < 16;
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("craftlink.addons.clear"), b -> {
            LinkConfig.ADDON_ACTIONS.set(java.util.List.of()); Client.SERVER.setAddonActions(java.util.List.of()); rebuildWidgets();
        }).bounds(width / 2 - 155, height - 54, 100, 20).build());
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(width / 2 - 45, height - 54, 95, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(width / 2 + 60, height - 54, 95, 20).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, height - 27, 200, 20).build());
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 15, 0xffffff);
        g.drawCenteredString(font, Component.translatable("craftlink.addons.help"), width / 2, 32, 0xaaaaaa);
        if (empty) g.drawCenteredString(font, Component.translatable("craftlink.addons.empty"), width / 2, 58, 0xaaaaaa);
        super.render(g, mx, my, partial);
    }
    @Override public void removed() { LinkConfig.SPEC.save(); super.removed(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
