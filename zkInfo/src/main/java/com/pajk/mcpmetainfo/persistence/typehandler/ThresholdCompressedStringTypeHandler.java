package com.pajk.mcpmetainfo.persistence.typehandler;

import com.pajk.mcpmetainfo.persistence.util.CompressionUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 根据阈值自动压缩字符串的 TypeHandler（Brotli + GZIP 兼容）。
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class ThresholdCompressedStringTypeHandler extends BaseTypeHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(ThresholdCompressedStringTypeHandler.class);

    private static final String TRUNC_SUFFIX = "...[TRUNCATED]";
    private static final byte[] TRUNC_SUFFIX_BYTES = TRUNC_SUFFIX.getBytes(StandardCharsets.UTF_8);

    private final int threshold;
    private final String handlerName;

    public ThresholdCompressedStringTypeHandler() {
        this(CompressionUtils.getCompressionThreshold());
    }

    public ThresholdCompressedStringTypeHandler(int threshold) {
        this.threshold = threshold;
        this.handlerName = getClass().getSimpleName();
        log.info("TypeHandler {} initialized. Byte limit: {}", handlerName, threshold);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        try {
            if (parameter == null) {
                ps.setString(i, null);
                return;
            }
            byte[] originalBytes = parameter.getBytes(StandardCharsets.UTF_8);
            String processed = CompressionUtils.compress(parameter, threshold);
            if (!CompressionUtils.isCompressed(parameter) && CompressionUtils.isCompressed(processed)) {
                log.debug("TypeHandler {}: Brotli compressed from {} bytes to {} bytes (threshold={})",
                        handlerName, originalBytes.length,
                        processed.getBytes(StandardCharsets.UTF_8).length, threshold);
            }
            if (processed.getBytes(StandardCharsets.UTF_8).length > threshold) {
                log.warn("TypeHandler {}: compressed payload still exceeds limit ({}>{}), applying truncation fallback.",
                        handlerName, processed.getBytes(StandardCharsets.UTF_8).length, threshold);
                processed = truncate(parameter);
            }
            ps.setString(i, processed);
        } catch (Exception ex) {
            log.error("TypeHandler {} failed to compress, writing original payload. cause={}", handlerName, ex.getMessage());
            ps.setString(i, truncate(parameter));
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return CompressionUtils.decompress(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return CompressionUtils.decompress(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return CompressionUtils.decompress(cs.getString(columnIndex));
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= threshold) {
            return value;
        }
        int available = Math.max(threshold - TRUNC_SUFFIX_BYTES.length, 0);
        if (available <= 0) {
            return TRUNC_SUFFIX;
        }
        String head = new String(bytes, 0, available, StandardCharsets.UTF_8);
        return head + TRUNC_SUFFIX;
    }
}

