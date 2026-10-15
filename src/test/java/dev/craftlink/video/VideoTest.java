package dev.craftlink.video;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VideoTest {
    @Test void reusedEncoderUpdatesPixelsAndResizesWithoutCorruptingPreviousOutput() throws Exception {
        var encoder = new JpegEncoder();
        int[] red = new int[16 * 16]; java.util.Arrays.fill(red, 0xff0000ff);
        byte[] first = encoder.encodeFrame(16, 16, red);
        int[] blue = new int[16 * 16]; java.util.Arrays.fill(blue, 0xffff0000);
        var second = ImageIO.read(new ByteArrayInputStream(encoder.encodeFrame(16, 16, blue)));
        assertTrue((second.getRGB(8, 8) & 255) > 200);
        assertTrue(((ImageIO.read(new ByteArrayInputStream(first)).getRGB(8, 8) >>> 16) & 255) > 200);
        var resized = ImageIO.read(new ByteArrayInputStream(encoder.encodeFrame(8, 8, new int[64])));
        assertEquals(8, resized.getWidth()); assertEquals(8, resized.getHeight());
    }
    @Test void unprojectionUsesTapLocationAndViewRotation() {
        Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(90),2,.1f,100);
        var center=ScreenRay.direction(new Matrix4f(projection).invert(),.5,.5);
        assertEquals(0,center.x,1e-5);assertEquals(0,center.y,1e-5);assertEquals(-1,center.z,1e-5);
        assertTrue(ScreenRay.direction(new Matrix4f(projection).invert(),.9,.5).x>0);
        assertTrue(ScreenRay.direction(new Matrix4f(projection).invert(),.5,.1).y>0);
        assertTrue(ScreenRay.direction(new Matrix4f(projection).rotateY((float)Math.PI).invert(),.5,.5).z>.99);
        assertThrows(IllegalArgumentException.class,()->ScreenRay.direction(projection,Double.NaN,.5));
    }
    @Test void nativeAbgrPixelsBecomeCorrectJpegColors() throws Exception {
        int[] pixels=new int[16*16];java.util.Arrays.fill(pixels,0xff0000ff);
        byte[] jpeg=JpegEncoder.encode(16,16,pixels);
        var decoded=ImageIO.read(new ByteArrayInputStream(jpeg));int color=decoded.getRGB(8,8);
        assertTrue(((color>>>16)&255)>200);assertTrue((color&255)<60);
        byte[] packet=VideoFrame.packet(42,16,16,1,jpeg);
        var header=ByteBuffer.wrap(packet);
        assertEquals(VideoFrame.MAGIC,header.getInt());assertEquals(42,header.getInt());assertEquals(16,header.getInt());assertEquals(16,header.getInt());assertEquals(1,header.getInt());
        assertEquals(jpeg.length+20,packet.length);
    }
}
