package dev.craftlink.gui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** Encode the actual per-session controller URL locally, including its token. */
public final class ConnectionQr {
    private ConnectionQr() {}
    public static BitMatrix encode(String url) throws WriterException {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Connection URL is empty");
        return new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 1, 1);
    }
}
