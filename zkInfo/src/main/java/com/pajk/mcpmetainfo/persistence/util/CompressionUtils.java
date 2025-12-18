package com.pajk.mcpmetainfo.persistence.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 压缩/解压工具（使用 GZIP）。
 * 注意：Brotli 支持已移除，如需使用请添加 brotli4j 依赖。
 */
public final class CompressionUtils {

    private static final Logger log = LoggerFactory.getLogger(CompressionUtils.class);

    private static final String LEGACY_GZIP_PREFIX = "[COMPRESSED]";
    private static final int DEFAULT_THRESHOLD = 2048;

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private CompressionUtils() {
    }

    public static String compress(String data) {
        return compress(data, DEFAULT_THRESHOLD);
    }

    public static String compress(String data, int thresholdBytes) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        if (isAlreadyCompressed(data)) {
            return data;
        }

        byte[] sourceBytes = data.getBytes(StandardCharsets.UTF_8);
        if (sourceBytes.length < thresholdBytes) {
            return data;
        }

        // 使用 GZIP 压缩
        String gzip = tryGzipEncode(sourceBytes);
        if (gzip != null) {
            return gzip;
        }

        log.warn("GZIP compression failed. Returning original payload.");
        return data;
    }

    public static String decompress(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        // 限制压缩数据的大小，防止解压缩阻塞
        // 如果压缩数据本身超过 1MB，可能解压后会非常大，直接返回截断提示
        final int MAX_COMPRESSED_SIZE = 1024 * 1024; // 1MB
        if (data.length() > MAX_COMPRESSED_SIZE) {
            log.warn("Compressed data too large ({} bytes), skipping decompression to prevent blocking", data.length());
            return "[数据过大，跳过解压缩，原始大小: " + (data.length() / 1024) + "KB]";
        }
        
        if (data.startsWith(LEGACY_GZIP_PREFIX)) {
            return decodeGzip(data.substring(LEGACY_GZIP_PREFIX.length()));
        }
        return data;
    }

    public static boolean isCompressed(String data) {
        return data != null && data.startsWith(LEGACY_GZIP_PREFIX);
    }

    public static int getCompressionThreshold() {
        return DEFAULT_THRESHOLD;
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    private static boolean isAlreadyCompressed(String data) {
        return data.startsWith(LEGACY_GZIP_PREFIX);
    }

    private static String tryGzipEncode(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
            gzip.finish();
            return LEGACY_GZIP_PREFIX + BASE64_ENCODER.encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.warn("GZIP compression failed: {}", e.getMessage());
            return null;
        }
    }

    // 解压后的最大大小限制：2MB（防止内存溢出和阻塞）
    private static final int MAX_DECOMPRESSED_SIZE = 2 * 1024 * 1024;

    private static String decodeGzip(String base64) {
        try {
            byte[] compressed = BASE64_DECODER.decode(base64);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 GZIPInputStream gzip = new GZIPInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                int totalRead = 0;
                while ((len = gzip.read(buffer)) != -1) {
                    // 检查解压后的大小，防止过大导致阻塞
                    if (totalRead + len > MAX_DECOMPRESSED_SIZE) {
                        int remaining = MAX_DECOMPRESSED_SIZE - totalRead;
                        if (remaining > 0) {
                            baos.write(buffer, 0, remaining);
                        }
                        log.warn("GZIP decompressed data exceeds limit ({} bytes), truncating to prevent blocking", 
                                totalRead + len);
                        String result = baos.toString(StandardCharsets.UTF_8);
                        return result + "\n\n...[解压后数据过大，已截断，仅显示前 " + (MAX_DECOMPRESSED_SIZE / 1024 / 1024) + "MB]";
                    }
                    baos.write(buffer, 0, len);
                    totalRead += len;
                }
                return baos.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            log.warn("GZIP decode failed: {}", ex.getMessage());
            return base64;
        }
    }
}
