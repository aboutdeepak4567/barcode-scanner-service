package com.example.barcode_scanner;

import com.google.zxing.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@CrossOrigin(origins = "*") // Allows the Vercel UI to communicate with this backend
@RequestMapping("/api/v1/scan")
@Tag(name = "Barcode Scanner", description = "API for scanning barcodes from images")
public class BarcodeScannerController {

    private final BarcodeScannerService barcodeScannerService;

    public BarcodeScannerController(BarcodeScannerService barcodeScannerService) {
        this.barcodeScannerService = barcodeScannerService;
    }

    @Operation(summary = "Health check", description = "Verifies the service is running successfully.")
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of("success", true, "message", "Scanner API is healthy"));
    }

    @Operation(summary = "Scan a barcode from an uploaded image", description = "Uploads an image, processes it using OpenCV and ZXing, and returns the decoded barcode data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Barcode successfully decoded",
                    content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "404", description = "No barcode found in image", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error during scanning", content = @Content)
    })
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> scanBarcode(
            @Parameter(description = "Image file containing the barcode", required = true)
            @RequestParam("file") MultipartFile file) {
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
