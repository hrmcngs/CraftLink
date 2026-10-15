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
        addRenderableWidget(Button.builder(Component.literal("みんなで参加"), b -> minecraft.setScreen(new ClassroomScreen(this))).bounds(4, 4, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("別のWi-Fi"), b -> minecraft.setScreen(new ExternalConnectionScreen(this, false))).bounds(width - 104, 4, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("接続を開始"), b -> { var settings = LinkConfig.launchSettings(); Client.SERVER.start(settings.port(), settings.bindAddress(), settings.advertisedHost(), LinkConfig.externalUrl(false)); }).bounds(x - 155, height - 76, 74, 20).build());
        addRenderableWidget(Button.builder(Component.literal("停止"), b -> Client.SERVER.stop()).bounds(x - 76, height - 76, 74, 20).build());
        addRenderableWidget(Button.builder(Component.literal("端末を切断"), b -> Client.SERVER.disconnect()).bounds(x + 82, height - 76, 73, 20).build());
        renewButton = addRenderableWidget(Button.builder(Component.literal("QR再発行"), b -> Client.SERVER.renewSession())
            .tooltip(Tooltip.create(Component.literal("接続中の端末を切断し、古いURLを無効にして新しい専用URLを生成します")))
            .bounds(x + 3, height - 76, 74, 20).build());
        addRenderableWidget(Button.builder(Component.literal("接続を許可して戻る"), b -> { if (Client.SERVER.pending()) { Client.SERVER.approve(); minecraft.setScreen(null); } }).bounds(x - 155, height - 51, 205, 20).build());
        addRenderableWidget(Button.builder(Component.literal("戻る"), b -> onClose()).bounds(x + 55, height - 51, 100, 20).build());
        addressButton = addRenderableWidget(Button.builder(Component.literal("接続先を切替"), b -> Client.SERVER.nextAddress()).bounds(x - 155, height - 26, 205, 20).build());
        addRenderableWidget(Button.builder(Component.literal("URLをコピー"), b -> minecraft.keyboardHandler.setClipboard(Client.SERVER.url())).bounds(x + 55, height - 26, 100, 20).build());
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, font.plainSubstrByWidth(title.getString(), Math.max(80, width - 220)), width / 2, 9, 0xffffff);
        g.drawCenteredString(font, Client.SERVER.status() + " / 端末: " + Client.SERVER.devices() + " / ポート: " + LinkConfig.launchSettings().port(), width / 2, 25, 0xaaffaa);
        addressButton.setMessage(Component.literal("接続先: " + (Client.SERVER.external() ? "HTTPS中継" : Client.SERVER.running() ? Client.SERVER.selectedHost() : "自動")));
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
