package com.tickethub.booking.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

@Component
public class QrCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(QrCodeGenerator.class);
    private static final int SIZE = 240;

    /** Returns a data URI, or null if generation fails (the ticket is still valid without it). */
    public String dataUri(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception ex) {
            log.warn("QR generation failed for {}", content, ex);
            return null;
        }
    }
}
