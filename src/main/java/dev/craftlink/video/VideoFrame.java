package dev.craftlink.video;

import java.nio.ByteBuffer;

/** CLV1 + frame id, width, height, context (world=0, GUI=1, unsupported=2), JPEG. */
public final class VideoFrame {
    public static final int MAGIC = 0x434c5631, HEADER_BYTES = 20;
    private VideoFrame() {}
    public static byte[] packet(int id, int width, int height, int context, byte[] jpeg) {
        if (id <= 0 || width < 1 || height < 1 || context < 0 || context > 2 || jpeg.length > 512_000) throw new IllegalArgumentException("Invalid frame");
        return ByteBuffer.allocate(HEADER_BYTES + jpeg.length).putInt(MAGIC).putInt(id).putInt(width).putInt(height).putInt(context).put(jpeg).array();
    }
}
