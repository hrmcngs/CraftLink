package dev.craftlink.gui;

import dev.craftlink.CraftLink.Client;
import dev.craftlink.config.LinkConfig;
import dev.craftlink.network.ExternalEndpoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit PC opt-in; changing the tunnel origin requires stopping the endpoint first. */
public final class ExternalConnectionScreen extends Screen {
    private final Screen parent;
    private final boolean classroom;
    private EditBox address;
    private Button save, clear;
    private String error = "";
    public ExternalConnectionScreen(Screen parent, boolean classroom) {
        super(Component.literal("別のWi-Fiから接続 / HTTPS")); this.parent=parent; this.classroom=classroom;
    }
    private boolean running() { return classroom ? Client.CLASSROOM.running() : Client.SERVER.running(); }
    @Override protected void init() {
        int left=width/2-155;
        address=new EditBox(font,left,88,310,20,Component.literal("外部中継のHTTPS URL"));
        address.setMaxLength(512); address.setValue((classroom?LinkConfig.CLASSROOM_EXTERNAL_URL:LinkConfig.EXTERNAL_URL).get());
        address.setHint(Component.literal("https://example.trycloudflare.com"));
        addRenderableWidget(address);
        save=addRenderableWidget(Button.builder(Component.literal("保存して戻る"), b->apply()).bounds(left,116,150,20).build());
        clear=addRenderableWidget(Button.builder(Component.literal("LAN接続に戻す"), b->{address.setValue("");apply();}).bounds(left+160,116,150,20).build());
        addRenderableWidget(Button.builder(Component.literal("戻る"), b->onClose()).bounds(width/2-100,height-27,200,20).build());
        setInitialFocus(address); tick();
    }
    private void apply() {
        if(running())return;
        try {
            String normalized=ExternalEndpoint.normalize(address.getValue());
            (classroom?LinkConfig.CLASSROOM_EXTERNAL_URL:LinkConfig.EXTERNAL_URL).set(normalized);
            LinkConfig.SPEC.save(); onClose();
        }catch(IllegalArgumentException invalid){error="https://ホスト名 のみを入力してください（パス・#トークンは不要）";}
    }
    @Override public void tick() { address.tick();address.setEditable(!running());save.active=!running();clear.active=!running(); }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g);g.drawCenteredString(font,title,width/2,15,0xffffff);
        g.drawWordWrap(font,Component.literal("Macの別ターミナルで sh tunnel.sh "+(classroom?"classroom":"controller")+" を実行し、表示されたHTTPS URLを貼り付けます。"),width/2-155,40,310,0xaaaaaa);
        g.drawString(font,"HTTPS URL（空欄ならLAN接続）",width/2-155,75,0xffffff);
        g.drawWordWrap(font,Component.literal(running()?"接続を停止してから変更してください。":error.isEmpty()?"保存後、元の画面で接続を開始するとQRがHTTPS用になります。Mac・スマホ両方のインターネット接続と外部中継が必要です。":error),width/2-155,148,310,error.isEmpty()?0xaaaaaa:0xff8888);
        super.render(g,mx,my,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
