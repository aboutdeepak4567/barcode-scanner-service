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
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Service
public class BarcodeScannerService {

    // Caffeine Cache for MD5 Image hashes
    private final Cache<String, String> barcodeCache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(java.time.Duration.ofHours(24))
            .build();

    private final MultiFormatReader barcodeReader;
    
    // JavaCV Converters for OpenCV interop
    private final OpenCVFrameConverter.ToMat toMatConverter;
    private final Java2DFrameConverter toBufferedImageConverter;

    public BarcodeScannerService() {
        this.barcodeReader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        this.barcodeReader.setHints(hints);

        this.toMatConverter = new OpenCVFrameConverter.ToMat();
        this.toBufferedImageConverter = new Java2DFrameConverter();
    }

    public String scanImage(MultipartFile file) throws IOException, NotFoundException {
        long startTime = System.currentTimeMillis();
        byte[] fileBytes = file.getBytes();
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

        // 1. Try fast ZXing decode first for pristine images (lowest latency)
        try {
            String result = decode(originalImage, startTime);
            barcodeCache.put(fileHash, result);
            return result;
        } catch (NotFoundException e) {
            log.debug("Standard decode failed, falling back to OpenCV preprocessing pipeline...");
        }

        // 2. OpenCV Fallback Preprocessing: Grayscale & Contrast Enhancement
        BufferedImage preprocessedImage = preprocessWithOpenCV(originalImage);

        // 3. Second decode attempt on enhanced image
        String finalResult = decode(preprocessedImage, startTime);
        barcodeCache.put(fileHash, finalResult);
        return finalResult;
    }
    
    private String decode(BufferedImage image, long globalStartTime) throws NotFoundException {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Result result = barcodeReader.decodeWithState(bitmap);
        
        long latency = System.currentTimeMillis() - globalStartTime;
        log.info("Barcode decoded successfully in {}ms: {}", latency, result.getText());
        return result.getText();
    }

    private BufferedImage preprocessWithOpenCV(BufferedImage image) {
        // Convert BufferedImage to JavaCV Frame, then to OpenCV Mat
        Frame frame = toBufferedImageConverter.convert(image);
        Mat matImage = toMatConverter.convert(frame);
        
        Mat grayMat = new Mat();
        
        // Convert to Grayscale
        // Handle images with alpha channels (ARGB/RGBA) natively
        int channels = matImage.channels();
        if (channels == 3) {
            opencv_imgproc.cvtColor(matImage, grayMat, opencv_imgproc.COLOR_BGR2GRAY);
        } else if (channels == 4) {
            opencv_imgproc.cvtColor(matImage, grayMat, opencv_imgproc.COLOR_BGRA2GRAY);
        } else {
            grayMat = matImage; // Already 1 channel
        }
        
        // Optional: Morphological operations or thresholding can be added here
        // to further enhance barcode lines before handing back to ZXing.
        // A simple adaptive threshold often dramatically improves barcode detection
        // opencv_imgproc.adaptiveThreshold(grayMat, enhancedMat, 255, opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, opencv_imgproc.THRESH_BINARY, 11, 2);

        // Convert back
        Frame processedFrame = toMatConverter.convert(grayMat);
        return toBufferedImageConverter.getBufferedImage(processedFrame);
    }
}
