package dev.craftlink.video;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

public final class JpegEncoder {
    private JpegEncoder() {}
    public static byte[] encode(int width, int height, int[] abgr) throws IOException {
        if (width < 1 || height < 1 || width > 1920 || height > 1080 || abgr.length != width * height) throw new IllegalArgumentException("Invalid frame size");
        int[] rgb = new int[abgr.length];
        for (int i = 0; i < abgr.length; i++) rgb[i] = ((abgr[i] & 0xff) << 16) | (abgr[i] & 0xff00) | ((abgr[i] >>> 16) & 0xff);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, rgb, 0, width);
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("JPEG encoder unavailable");
        var writer = writers.next();
        try (var bytes = new ByteArrayOutputStream(); var output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); parameters.setCompressionQuality(.7f);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), parameters); output.flush();
            return bytes.toByteArray();
        } finally { writer.dispose(); }
    }
}
