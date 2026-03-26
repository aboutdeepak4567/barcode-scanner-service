package com.example.barcode_scanner;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class BarcodeScannerService {

    private final Cache<String, String> barcodeCache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    public String scanImage(MultipartFile file) throws IOException, NotFoundException {
        return scanImageBytes(file.getBytes());
    }

    public String scanImageBytes(byte[] fileBytes) throws IOException, NotFoundException {
        long startTime = System.currentTimeMillis();
        String fileHash = DigestUtils.md5DigestAsHex(fileBytes);

        String cachedResult = barcodeCache.getIfPresent(fileHash);
        if (cachedResult != null) {
            log.info("⚡ Cache HIT! Instantly returning decoded barcode for hash: {}", fileHash);
            return cachedResult;
        }

        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(fileBytes));
        if (originalImage == null) {
            throw new IllegalArgumentException("Could not read image data");
        }

        try {
            String result = decode(originalImage, startTime);
            barcodeCache.put(fileHash, result);
            return result;
        } catch (NotFoundException e) {
            log.debug("Standard decode failed, falling back to OpenCV preprocessing pipeline...");
        }

        BufferedImage preprocessedImage = preprocessWithOpenCV(originalImage);
        String finalResult = decode(preprocessedImage, startTime);
        barcodeCache.put(fileHash, finalResult);
        return finalResult;
    }

    public Map<String, Object> processZipBatch(MultipartFile zipFile) throws IOException {
        long globalStart = System.currentTimeMillis();
        Map<String, String> results = new ConcurrentHashMap<>();
        List<Future<Void>> futures = new ArrayList<>();

        // Create a Java 21 Project Loom Virtual Thread Executor!
        // This will spawn an ultra-lightweight thread for every single image in the zip concurrently!
        try (ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
             ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
             
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !isImageFile(entry.getName())) {
                    continue;
                }

                String filename = entry.getName();
                
                // Read bytes fully from the stream for each file
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[1024];
                int count;
                while ((count = zis.read(data, 0, 1024)) != -1) {
                    buffer.write(data, 0, count);
                }
                final byte[] imageBytes = buffer.toByteArray();

                // Submit to Virtual Thread
                futures.add(virtualThreadExecutor.submit(() -> {
                    try {
                        String decodedText = scanImageBytes(imageBytes);
                        results.put(filename, decodedText);
                    } catch (NotFoundException e) {
                        results.put(filename, "ERROR: No barcode detected");
                    } catch (Exception e) {
                        results.put(filename, "ERROR: " + e.getMessage());
                    }
                    return null;
                }));
            }
            
            // Wait for all virtual threads to beautifully execute concurrently
            for (Future<Void> future : futures) {
                try { future.get(); } catch (Exception ignored) {}
            }
        }
        
        return Map.of(
            "totalProcessed", results.size(),
            "results", results,
            "totalLatencyMs", System.currentTimeMillis() - globalStart
        );
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
    }

    private String decode(BufferedImage image, long globalStartTime) throws NotFoundException {
        // Instantiate locally for 100% thread-safety inside Virtual Threads
        MultiFormatReader barcodeReader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        barcodeReader.setHints(hints);

        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Result result = barcodeReader.decodeWithState(bitmap);
        return result.getText();
    }

    private BufferedImage preprocessWithOpenCV(BufferedImage image) {
        // Instantiate locally for 100% thread-safety inside Virtual Threads
        try (Java2DFrameConverter toBufferedImageConverter = new Java2DFrameConverter();
             OpenCVFrameConverter.ToMat toMatConverter = new OpenCVFrameConverter.ToMat()) {
             
            Frame frame = toBufferedImageConverter.convert(image);
            Mat matImage = toMatConverter.convert(frame);
            Mat grayMat = new Mat();
            
            int channels = matImage.channels();
            if (channels == 3) {
                opencv_imgproc.cvtColor(matImage, grayMat, opencv_imgproc.COLOR_BGR2GRAY);
            } else if (channels == 4) {
                opencv_imgproc.cvtColor(matImage, grayMat, opencv_imgproc.COLOR_BGRA2GRAY);
            } else {
                grayMat = matImage; // Already 1 channel
            }

            // 1. Gaussian Blur: mathematically smooths out low-light camera sensor grain and JPEG compression artifacts
            Mat blurredMat = new Mat();
            opencv_imgproc.GaussianBlur(grayMat, blurredMat, new org.bytedeco.opencv.opencv_core.Size(5, 5), 0);

            // 2. Adaptive Gaussian Thresholding: dynamically repairs crumpled/faded ink by aggressively binarizing local pixel neighborhoods
            Mat thresholdMat = new Mat();
            opencv_imgproc.adaptiveThreshold(blurredMat, thresholdMat, 255, opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, opencv_imgproc.THRESH_BINARY, 11, 2);

            Frame processedFrame = toMatConverter.convert(thresholdMat);
            return toBufferedImageConverter.getBufferedImage(processedFrame);
        }
    }
}
