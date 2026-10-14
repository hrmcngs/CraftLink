package dev.craftlink.gui;

import dev.craftlink.CraftLink.Client;
import dev.craftlink.config.LinkConfig;
import dev.craftlink.config.TouchLayout;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Native Minecraft widgets, background, focus navigation and narration. */
public final class TouchSettingsScreen extends Screen {
    private final Screen parent;
    public TouchSettingsScreen(Screen parent) { super(Component.translatable("craftlink.controls.title")); this.parent = parent; }
    @Override protected void init() {
        int left = width / 2 - 155, top = Math.max(48, height / 6 + 24);
        addRenderableWidget(CycleButton.<TouchLayout>builder(layout -> Component.translatable("craftlink.layout." + layout.id()))
            .withValues(TouchLayout.values()).withInitialValue(LinkConfig.TOUCH_LAYOUT.get())
            .create(left, top, 310, 20, Component.translatable("craftlink.controls.layout"), (button, layout) -> {
                LinkConfig.TOUCH_LAYOUT.set(layout); publish();
            }));
        addRenderableWidget(new SettingSlider(left, top + 24, 150, "craftlink.controls.sensitivity", LinkConfig.TOUCH_SENSITIVITY.get(), 0.03, 0.5,
            value -> { LinkConfig.TOUCH_SENSITIVITY.set(Math.round(value * 100) / 100.0); publish(); }, false));
        addRenderableWidget(new SettingSlider(left + 160, top + 24, 150, "craftlink.controls.size", LinkConfig.TOUCH_BUTTON_SIZE.get(), 80, 140,
            value -> { LinkConfig.TOUCH_BUTTON_SIZE.set((int)Math.round(value)); publish(); }, true));
        addRenderableWidget(Button.builder(Component.translatable("craftlink.controls.connect"), b -> minecraft.setScreen(new LinkScreen(this)))
            .bounds(width / 2 - 100, top + 52, 200, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, height - 27, 200, 20).build());
    }
    private void publish() { Client.SERVER.setControllerPreferences(LinkConfig.controllerPreferences()); }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 15, 0xffffff);
        g.drawCenteredString(font, Component.translatable("craftlink.controls.subtitle"), width / 2, 35, 0xa0a0a0);
        if (height >= 220) g.drawWordWrap(font, Component.translatable("craftlink.controls.help"), width / 2 - 150, Math.max(48, height / 6 + 24) + 82, 300, 0xa0a0a0);
        super.render(g, mx, my, partial);
    }
    @Override public void removed() { LinkConfig.SPEC.save(); super.removed(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    private static final class SettingSlider extends AbstractSliderButton {
        private final String label;
        private final double min, max;
        private final DoubleConsumer changed;
        private final boolean percentage;
        SettingSlider(int x, int y, int width, String label, double initial, double min, double max, DoubleConsumer changed, boolean percentage) {
            super(x, y, width, 20, Component.empty(), (initial - min) / (max - min));
            this.label = label; this.min = min; this.max = max; this.changed = changed; this.percentage = percentage; updateMessage();
        }
        @Override protected void updateMessage() {
            double setting = min + value * (max - min);
            setMessage(Component.translatable(label).append(": " + (percentage ? Math.round(setting) + "%" : String.format(Locale.ROOT, "%.2f", setting))));
        }
        @Override protected void applyValue() { changed.accept(min + value * (max - min)); }
    }
}
