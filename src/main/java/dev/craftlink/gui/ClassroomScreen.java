package dev.craftlink.gui;

import com.google.zxing.common.BitMatrix;
import dev.craftlink.CraftLink.Client;
import dev.craftlink.classroom.ClassroomRoom;
import dev.craftlink.config.LinkConfig;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Host lobby and emergency pause. Does not pause the integrated world. */
public final class ClassroomScreen extends Screen {
    private final Screen parent;
    private int page;
    private String roster = "", qrUrl = "";
    private BitMatrix qr;
    private Button start, pause;
    private Button mode;
    public ClassroomScreen(Screen parent) { super(Component.literal("CraftLink みんなで参加 / 最大8台")); this.parent = parent; }
    @Override protected void init() {
        int x = width/2;
        addRenderableWidget(Button.builder(Component.literal("別のWi-Fi"), b -> minecraft.setScreen(new ExternalConnectionScreen(this, true))).bounds(4, 4, 100, 20).build());
        mode=addRenderableWidget(Button.builder(Component.literal("参加者: サバイバル"),b->{var current=Client.CLASSROOM.room();if(current!=null)current.mode(current.mode()==ClassroomRoom.Mode.SURVIVAL?ClassroomRoom.Mode.CREATIVE:ClassroomRoom.Mode.SURVIVAL);}).bounds(width-154,4,150,20).build());
        start = addRenderableWidget(Button.builder(Component.literal("受付を開始"), b -> {
            if (minecraft.getSingleplayerServer() != null && minecraft.player != null && !usbLaunch()) {
                var settings = LinkConfig.launchSettings();
                Client.CLASSROOM.start(LinkConfig.CLASSROOM_PORT.get(), settings.bindAddress(), settings.advertisedHost(), LinkConfig.externalUrl(true));
            }
        }).bounds(x-155,height-76,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("部屋を終了"), b -> Client.stopClassroom()).bounds(x-50,height-76,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("全員停止"), b -> { var room=Client.CLASSROOM.room(); if(room!=null)room.pause(true); }).bounds(x+55,height-76,100,20).build());
        pause = addRenderableWidget(Button.builder(Component.literal("開始 / 再開"), b -> { var room=Client.CLASSROOM.room();if(room!=null){room.pause(false);minecraft.setScreen(null);} }).bounds(x-155,height-51,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("接続先切替"), b -> Client.CLASSROOM.nextAddress()).bounds(x-50,height-51,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("URLコピー"), b -> minecraft.keyboardHandler.setClipboard(Client.CLASSROOM.url())).bounds(x+55,height-51,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("前 / 次"), b -> { page=1-page;roster="";rebuildWidgets(); }).bounds(x-155,height-26,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("BOTを呼び戻す"), b -> Client.BOTS.recall()).bounds(x-50,height-26,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("戻る"), b -> onClose()).bounds(x+55,height-26,100,20).build());
        var room = Client.CLASSROOM.room();
        List<ClassroomRoom.View> peers = room == null ? List.of() : room.views();
        for (int i=page*4;i<Math.min(peers.size(),(page+1)*4);i++) {
            var peer=peers.get(i);int y=61+(i%4)*22;
            var allow = addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(peer.number()+"号 "+peer.name()+" / "+(!peer.connected()?"再接続待ち":peer.approved()?"参加中":"接続中"),Math.max(50,width-235))),b->{var current=Client.CLASSROOM.room();if(current!=null)current.approve(peer.id());})
                .bounds(165,y,Math.max(60,width-225),20).build());
            allow.active = peer.connected() && !peer.approved();
            addRenderableWidget(Button.builder(Component.literal("切断"),b->{var current=Client.CLASSROOM.room();if(current!=null)current.remove(peer.id());})
                .bounds(width-56,y,50,20).build());
        }
        roster = peers.toString();
    }
    @Override public void tick() {
        var room=Client.CLASSROOM.room();String next=room==null?"[]":room.views().toString();
        if(!roster.equals(next)) rebuildWidgets();
        start.active = !Client.CLASSROOM.running() && minecraft.getSingleplayerServer()!=null && minecraft.player!=null && !usbLaunch();
        pause.active = Client.CLASSROOM.running();
        mode.active=room!=null;
        mode.setMessage(Component.literal("参加者: "+(room!=null&&room.mode()==ClassroomRoom.Mode.CREATIVE?"クリエイティブ":"サバイバル")));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g);
        g.drawString(font,Component.literal("CraftLink"),111,9,0xffffff);
        var room=Client.CLASSROOM.room();
        String status=Client.CLASSROOM.status()+(room==null?"":" / "+room.views().size()+"台 / BOT "+Client.BOTS.entityIds().size()+"体 / "+(room.paused()?"停止中":"操作中"));
        g.drawCenteredString(font,status,width/2,27,0xaaffaa);
        g.drawCenteredString(font,usbLaunch()?"参加モードはLAN起動（sh run.sh）を使用してください":minecraft.getSingleplayerServer()==null?"シングルプレイのワールドで開いてください":"QRを読むだけでBOT参加 / スマホにBOT視点を配信",width/2,43,0xaaaaaa);
        String url=Client.CLASSROOM.url();
        if(!url.equals(qrUrl)){qrUrl=url;qr=null;if(!url.isEmpty())try{qr=ConnectionQr.encode(url);}catch(Exception ignored){}}
        if(qr!=null){int scale=Math.max(1,Math.min(3,Math.min(145,height-150)/qr.getWidth()));int size=qr.getWidth()*scale,left=(160-size)/2;
            g.fill(left,62,left+size,62+size,0xffffffff);
            for(int y=0;y<qr.getHeight();y++)for(int x=0;x<qr.getWidth();x++)if(qr.get(x,y))g.fill(left+x*scale,62+y*scale,left+(x+1)*scale,62+(y+1)*scale,0xff000000);
        }
        g.drawCenteredString(font,font.plainSubstrByWidth(url,width-12),width/2,height-89,0xffffff);
        if(!Client.BOT_VIDEO.error().isEmpty()) g.drawCenteredString(font,Client.BOT_VIDEO.error(),width/2,height-101,0xff8888);
        super.render(g,mx,my,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
    private boolean usbLaunch(){return System.getProperty("craftlink.transport","lan").equals("usb");}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
