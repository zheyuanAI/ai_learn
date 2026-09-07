package com.ailearn.platform.core.config;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * Core 使用的 UUID 类型处理器。
 * <p>
 * PostgreSQL 原生 uuid、H2 UUID 和字符串 UUID 在 MyBatis 参数及结果映射中统一转换，避免
 * 注解 ResultMap 在不同数据库驱动下无法找到类型处理器。
 * </p>
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = {JdbcType.OTHER, JdbcType.VARCHAR}, includeNullJdbcType = true)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    /** 设置 UUID 参数；PostgreSQL 使用原生对象，兼容显式 VARCHAR 绑定。 */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, UUID parameter,
                                    JdbcType jdbcType) throws SQLException {
        if (jdbcType == JdbcType.VARCHAR) {
            ps.setString(index, parameter.toString());
        } else {
            ps.setObject(index, parameter);
        }
    }

    /** 从命名列读取 UUID。 */
    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUuid(rs.getObject(columnName));
    }

    /** 从索引列读取 UUID。 */
    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUuid(rs.getObject(columnIndex));
    }

    /** 从存储过程结果读取 UUID。 */
    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUuid(cs.getObject(columnIndex));
    }

    /** 把数据库驱动返回的 UUID、字节或字符串安全转换为领域 UUID。 */
    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof byte[] bytes && bytes.length == 16) {
            long most = 0;
            long least = 0;
            for (int index = 0; index < 8; index++) {
                most = (most << 8) | (bytes[index] & 0xffL);
            }
            for (int index = 8; index < 16; index++) {
                least = (least << 8) | (bytes[index] & 0xffL);
            }
            return new UUID(most, least);
        }
        String text = value.toString();
        return text.isBlank() ? null : UUID.fromString(text);
    }
}
