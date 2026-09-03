package com.ailearn.platform.auth.config;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * MyBatis UUID 类型处理器。
 * <p>
 * 兼容处理 PostgreSQL UUID 原生类型、H2 内存数据库 UUID 以及字符串 UUID 在 ResultSet / PreparedStatement 中的双向转换。
 * </p>
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = {JdbcType.OTHER, JdbcType.VARCHAR}, includeNullJdbcType = true)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        if (jdbcType == JdbcType.VARCHAR) {
            ps.setString(i, parameter.toString());
        } else {
            ps.setObject(i, parameter);
        }
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object val = rs.getObject(columnName);
        return toUUID(val);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object val = rs.getObject(columnIndex);
        return toUUID(val);
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object val = cs.getObject(columnIndex);
        return toUUID(val);
    }

    private UUID toUUID(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof UUID uuid) {
            return uuid;
        }
        if (val instanceof byte[] bytes) {
            // H2 某些版本可能以二进制字节流返回 UUID
            if (bytes.length == 16) {
                long msb = 0;
                long lsb = 0;
                for (int i = 0; i < 8; i++) {
                    msb = (msb << 8) | (bytes[i] & 0xff);
                }
                for (int i = 8; i < 16; i++) {
                    lsb = (lsb << 8) | (bytes[i] & 0xff);
                }
                return new UUID(msb, lsb);
            }
        }
        String str = val.toString();
        if (str.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
