package dev.craftlink.video;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotVideoQualityTest {
    @Test void presetsEncodeValidLargerFramesWithinPacketLimit() throws Exception {
        var encoder=new JpegEncoder();
        for(var quality:BotVideoQuality.values()) {
            assertSame(quality,BotVideoQuality.parse(quality.id));
            int[] pixels=new int[quality.width*quality.height];
            for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000 | (i*73856093 & 0xffffff);
            byte[] jpeg=encoder.encodeFrame(quality.width,quality.height,pixels,quality.jpegQuality);
            var decoded=ImageIO.read(new ByteArrayInputStream(jpeg));
            assertEquals(quality.width,decoded.getWidth());assertEquals(quality.height,decoded.getHeight());
            assertTrue(VideoFrame.packet(1,quality.width,quality.height,0,jpeg).length<=512020);
        }
        assertThrows(IllegalArgumentException.class,()->BotVideoQuality.parse("4k"));
        assertThrows(IllegalArgumentException.class,()->encoder.encodeFrame(1,1,new int[1],Float.NaN));
    }
}
