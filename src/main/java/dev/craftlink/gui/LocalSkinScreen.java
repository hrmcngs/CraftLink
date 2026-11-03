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
 public LocalSkinScreen(Screen parent){super(Component.translatable("craftlink.ui.local_skin"));this.parent=parent;}
 @Override protected void init(){
  int x=width/2;path=addRenderableWidget(new EditBox(font,x-150,48,300,20,Component.translatable("craftlink.ui.png_file_path")));path.setMaxLength(2048);
  addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.choose_png"),button->{String chosen=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(net.minecraft.client.resources.language.I18n.get("craftlink.ui.choose_skin_png"),System.getProperty("user.home"),(org.lwjgl.PointerBuffer)null,"PNG",false);if(chosen!=null)path.setValue(chosen);}).bounds(x-150,75,145,20).build());
  addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.arms_classic"),button->{slim=!slim;button.setMessage(Component.literal(slim?net.minecraft.client.resources.language.I18n.get("craftlink.ui.arms_slim"):net.minecraft.client.resources.language.I18n.get("craftlink.ui.arms_classic")));}).bounds(x+5,75,145,20).build());
  addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.apply_and_save"),button->{try{Path file=Path.of(path.getValue());if(Files.size(file)>65536)throw new java.io.IOException(net.minecraft.client.resources.language.I18n.get("craftlink.ui.png_must_be_64_kb_or_smaller"));var skin=SkinPng.decode(Files.readAllBytes(file),slim);SkinRepository.global().store(skin);UploadedSkins.setLocal(skin.id());status=net.minecraft.client.resources.language.I18n.get("craftlink.ui.saved_applying_to_your_player");}catch(Exception error){status=error.getMessage()==null?net.minecraft.client.resources.language.I18n.get("craftlink.ui.could_not_read_png"):error.getMessage();}}).bounds(x-150,102,145,20).build());
  addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.restore_original_skin"),button->{try{UploadedSkins.setLocal("");status=net.minecraft.client.resources.language.I18n.get("craftlink.ui.restored_original_skin");}catch(Exception error){status=net.minecraft.client.resources.language.I18n.get("craftlink.ui.could_not_save");}}).bounds(x+5,102,145,20).build());
  addRenderableWidget(Button.builder(Component.translatable("craftlink.ui.back"),button->onClose()).bounds(x-75,height-28,150,20).build());
 }
 @Override public void render(GuiGraphics graphics,int x,int y,float partial){renderBackground(graphics);graphics.drawCenteredString(font,title,width/2,18,0xffffff);graphics.drawWordWrap(font,Component.literal(status),width/2-150,137,300,0xeeeeee);super.render(graphics,x,y,partial);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
