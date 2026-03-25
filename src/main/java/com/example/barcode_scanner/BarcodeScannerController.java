package com.example.barcode_scanner;

import com.google.zxing.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/scan")
public class BarcodeScannerController {

    private final BarcodeScannerService barcodeScannerService;

    public BarcodeScannerController(BarcodeScannerService barcodeScannerService) {
        this.barcodeScannerService = barcodeScannerService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> scanBarcode(@RequestParam("file") MultipartFile file) {
        try {
            long start = System.currentTimeMillis();
            String result = barcodeScannerService.scanImage(file);
            long latency = System.currentTimeMillis() - start;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result,
                    "latencyMs", latency
            ));
        } catch (NotFoundException e) {
            log.warn("No barcode detected in the provided image");
            return ResponseEntity.status(404).body(Map.of(
                    "success", false, 
                    "error", "No barcode found in image"
            ));
        } catch (Exception e) {
            log.error("Internal scanning error", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, 
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
            ));
        }
    }
}
