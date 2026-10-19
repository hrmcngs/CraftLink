package dev.craftlink.skin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class SkinPngTest {
 @TempDir Path directory;
 static byte[] png(int width,int height)throws Exception{var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB),"png",output);return output.toByteArray();}
 @Test void canonicalSkinIdIncludesArmModelAndSurvivesOfflineReload()throws Exception{
  byte[] png=png(64,64);var wide=SkinPng.decode(png,false);var slim=SkinPng.decode(png,true);
  assertNotEquals(wide.id(),slim.id());assertEquals(wide.id(),SkinPng.fromData(wide.data(),false).id());
  var repository=new SkinRepository(directory);repository.store(slim);repository.local(slim.id());
  var reopened=new SkinRepository(directory);assertEquals(slim.id(),reopened.local());assertEquals(slim.id(),reopened.load(slim.id()).id());assertTrue(reopened.load(slim.id()).slim());
  reopened.local("");assertEquals("",reopened.local());
 }
 @Test void rejectsNonPngOversizedOrWrongDimensionsAndArbitraryPaths()throws Exception{
  assertThrows(IOException.class,()->SkinPng.decode(png(32,32),false));assertThrows(IOException.class,()->SkinPng.decode(new byte[65537],false));
  assertThrows(IOException.class,()->SkinPng.fromData("https://example.test/skin.png",false));assertThrows(IOException.class,()->SkinPng.fromData("data:image/png;base64,@@@",false));
  var repository=new SkinRepository(directory);assertThrows(IOException.class,()->repository.load("custom:../../secret"));
 }
}
