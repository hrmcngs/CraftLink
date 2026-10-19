package dev.craftlink.gui;
import dev.craftlink.classroom.client.UploadedSkins;
import dev.craftlink.skin.*;
import java.nio.file.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class LocalSkinScreen extends Screen {
 private final Screen parent;private EditBox path;private boolean slim;private String status="64×64のPNG。インターネット接続は不要です。";
 public LocalSkinScreen(Screen parent){super(Component.literal("ローカルスキン"));this.parent=parent;}
 @Override protected void init(){
  int x=width/2;path=addRenderableWidget(new EditBox(font,x-150,48,300,20,Component.literal("PNGのファイルパス")));path.setMaxLength(2048);
  addRenderableWidget(Button.builder(Component.literal("PNGを選択"),button->{String chosen=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("スキンPNGを選択",System.getProperty("user.home"),(org.lwjgl.PointerBuffer)null,"PNG",false);if(chosen!=null)path.setValue(chosen);}).bounds(x-150,75,145,20).build());
  addRenderableWidget(Button.builder(Component.literal("腕: 通常"),button->{slim=!slim;button.setMessage(Component.literal(slim?"腕: 細い":"腕: 通常"));}).bounds(x+5,75,145,20).build());
  addRenderableWidget(Button.builder(Component.literal("適用・保存"),button->{try{Path file=Path.of(path.getValue());if(Files.size(file)>65536)throw new java.io.IOException("PNGは64KB以内にしてください。");var skin=SkinPng.decode(Files.readAllBytes(file),slim);SkinRepository.global().store(skin);UploadedSkins.setLocal(skin.id());status="保存しました。自分のプレイヤーに反映します。";}catch(Exception error){status=error.getMessage()==null?"PNGを読み込めませんでした。":error.getMessage();}}).bounds(x-150,102,145,20).build());
  addRenderableWidget(Button.builder(Component.literal("元のスキンに戻す"),button->{try{UploadedSkins.setLocal("");status="元のスキンに戻しました。";}catch(Exception error){status="保存できませんでした。";}}).bounds(x+5,102,145,20).build());
  addRenderableWidget(Button.builder(Component.literal("戻る"),button->onClose()).bounds(x-75,height-28,150,20).build());
 }
 @Override public void render(GuiGraphics graphics,int x,int y,float partial){renderBackground(graphics);graphics.drawCenteredString(font,title,width/2,18,0xffffff);graphics.drawWordWrap(font,Component.literal(status),width/2-150,137,300,0xeeeeee);super.render(graphics,x,y,partial);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
