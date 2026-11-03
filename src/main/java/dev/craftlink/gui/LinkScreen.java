package dev.craftlink.gui;
import com.google.zxing.common.BitMatrix;
import dev.craftlink.CraftLink.Client;
import dev.craftlink.config.LinkConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LinkScreen extends Screen {
    private final Screen parent;
    private String qrUrl = "";
    private BitMatrix qr;
    private Button addressButton, renewButton;
    public LinkScreen(Screen parent) { super(Component.literal("CraftLink 0.1.0 / " + Minecraft.getInstance().getUser().getName())); this.parent = parent; }
    @Override protected void init() {
        int x = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.skin_png"),b->minecraft.setScreen(new LocalSkinScreen(this))).bounds(4,28,100,20).build());
        addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.join_with_friends"), b -> minecraft.setScreen(new ClassroomScreen(this))).bounds(4, 4, 100, 20).build());
        if(dev.craftlink.network.ConnectionPolicy.remoteEnabled()) addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.remote_connection"), b -> minecraft.setScreen(new ExternalConnectionScreen(this, false))).bounds(width - 104, 4, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.start"), b -> { var settings = LinkConfig.launchSettings(); Client.SERVER.start(settings.port(), settings.bindAddress(), settings.advertisedHost(), LinkConfig.externalUrl(false)); }).bounds(x - 155, height - 76, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.stop"), b -> Client.SERVER.stop()).bounds(x - 76, height - 76, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.disconnect_device"), b -> Client.SERVER.disconnect()).bounds(x + 82, height - 76, 73, 20).build());
        renewButton = addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.renew_qr"), b -> Client.SERVER.renewSession())
            .tooltip(Tooltip.create(Component.translatable("craftlink.ui.disconnect_the_device_and_replace_the_old_url_with_a_new_private_url")))
            .bounds(x + 3, height - 76, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.allow_and_return"), b -> { if (Client.SERVER.pending()) { Client.SERVER.approve(); minecraft.setScreen(null); } }).bounds(x - 155, height - 51, 205, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.back"), b -> onClose()).bounds(x + 55, height - 51, 100, 20).build());
        addressButton = addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.change_address"), b -> Client.SERVER.nextAddress()).bounds(x - 155, height - 26, 205, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.copy_url"), b -> minecraft.keyboardHandler.setClipboard(Client.SERVER.url())).bounds(x + 55, height - 26, 100, 20).build());
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, font.plainSubstrByWidth(title.getString(), Math.max(80, width - 220)), width / 2, 9, 0xffffff);
        g.drawCenteredString(font, Client.SERVER.status() + net.minecraft.client.resources.language.I18n.get("craftlink.ui.devices") + Client.SERVER.devices() + net.minecraft.client.resources.language.I18n.get("craftlink.ui.port") + LinkConfig.launchSettings().port(), width / 2, 25, 0xaaffaa);
        addressButton.setMessage(Component.literal(net.minecraft.client.resources.language.I18n.get("craftlink.ui.address") + (Client.SERVER.external() ? net.minecraft.client.resources.language.I18n.get("craftlink.ui.https_relay") : Client.SERVER.running() ? Client.SERVER.selectedHost() : net.minecraft.client.resources.language.I18n.get("craftlink.ui.automatic"))));
        addressButton.active = Client.SERVER.running() && !Client.SERVER.external();
        renewButton.active = Client.SERVER.running();
        String url = Client.SERVER.url();
        if (!url.equals(qrUrl)) { qrUrl = url; qr = null; if (!url.isEmpty()) try { qr = ConnectionQr.encode(url); } catch (Exception ignored) {} }
        if (qr != null) {
            int scale = Math.max(1, Math.min(3, (height - 135) / qr.getWidth()));
            int size = qr.getWidth() * scale, left = (width - size) / 2;
            g.fill(left, 42, left + size, 42 + size, 0xffffffff);
            for (int y = 0; y < qr.getHeight(); y++) for (int x = 0; x < qr.getWidth(); x++) if (qr.get(x,y)) g.fill(left + x * scale, 42 + y * scale, left + (x + 1) * scale, 42 + (y + 1) * scale, 0xff000000);
        }
        g.drawCenteredString(font, font.plainSubstrByWidth(url, width - 12), width / 2, height - 90, 0xffffff);
        super.render(g, mx, my, partial);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
