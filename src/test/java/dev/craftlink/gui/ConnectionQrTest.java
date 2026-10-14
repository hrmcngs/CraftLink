package dev.craftlink.gui;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import dev.craftlink.security.Session;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionQrTest {
    @Test void qrRoundTripPreservesPrivateConnectionUrl() throws Exception {
        String url = "http://192.168.1.20:8765/#" + new Session().token();
        var modules = ConnectionQr.encode(url);
        int scale = 4, width = modules.getWidth() * scale, height = modules.getHeight() * scale;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) pixels[y * width + x] = modules.get(x / scale, y / scale) ? 0xff000000 : 0xffffffff;
        var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        assertEquals(url, new QRCodeReader().decode(bitmap).getText());
    }
    @Test void emptyConnectionCannotGenerateQr() {
        assertThrows(IllegalArgumentException.class, () -> ConnectionQr.encode(""));
    }
}
