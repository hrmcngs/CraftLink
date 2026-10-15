package dev.craftlink.video;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

public final class JpegEncoder {
    private BufferedImage image;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
    public JpegEncoder() {}
    public static byte[] encode(int width, int height, int[] abgr) throws IOException {
        return new JpegEncoder().encodeFrame(width, height, abgr);
    }
    /** Confined to the single video encoder worker; reuse frame and output buffers. */
    public byte[] encodeFrame(int width, int height, int[] abgr) throws IOException {
        return encodeFrame(width,height,abgr,.7f);
    }
    public byte[] encodeFrame(int width,int height,int[] abgr,float quality) throws IOException {
        if (!Float.isFinite(quality) || quality < .1f || quality > 1) throw new IllegalArgumentException("Invalid JPEG quality");
        if (width < 1 || height < 1 || width > 1920 || height > 1080 || abgr.length != width * height) throw new IllegalArgumentException("Invalid frame size");
        if (image == null || image.getWidth() != width || image.getHeight() != height)
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] rgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < abgr.length; i++) rgb[i] = ((abgr[i] & 0xff) << 16) | (abgr[i] & 0xff00) | ((abgr[i] >>> 16) & 0xff);
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("JPEG encoder unavailable");
        var writer = writers.next();
        bytes.reset();
        // Explicit memory stream avoids ImageIO temporary-file caching on every frame.
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); parameters.setCompressionQuality(quality);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), parameters); output.flush();
            return bytes.toByteArray();
        } finally { writer.dispose(); }
    }
}
