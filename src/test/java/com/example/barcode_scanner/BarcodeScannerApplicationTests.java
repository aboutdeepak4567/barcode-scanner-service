package com.example.barcode_scanner;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class BarcodeScannerApplicationTests {

    @Autowired
    private BarcodeScannerService barcodeScannerService;

    @Test
    void testScanImage() throws Exception {
        // 1. Generate a test barcode image using ZXing
        String testData = "SPRING-BOOT-2026";
        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix matrix = writer.encode(testData, BarcodeFormat.QR_CODE, 200, 200);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-barcode.png",
                "image/png",
                baos.toByteArray()
        );

        // 2. Scan it using our service
        long start = System.currentTimeMillis();
        String result = barcodeScannerService.scanImage(file);
        long runtime = System.currentTimeMillis() - start;

        // 3. Verify
        assertNotNull(result);
        assertEquals(testData, result);
        System.out.println("Scan successful in " + runtime + "ms");
    }
}
