package com.ecomerceshopeonline.ecomercewepapp.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/**
 * Renders a KHQR payload as a PNG data URI, so the frontend can do
 * {@code <img src={payment.qrImage} />} without a QR library of its own.
 */
@Component
public class QrImageRenderer {

    private static final int DEFAULT_SIZE = 320;
    private static final int MARGIN = 2;

    public String toDataUri(String content) {
        return toDataUri(content, DEFAULT_SIZE);
    }

    public String toDataUri(String content, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    content, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, MARGIN,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));

            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Could not render the KHQR image", ex);
        }
    }
}
