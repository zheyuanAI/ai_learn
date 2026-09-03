package com.ailearn.platform.auth.config;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * 将数据库时间类型映射为项目现有的 LocalDateTime。
 * <p>
 * PostgreSQL 的 TIMESTAMPTZ 不能直接通过 JDBC 的
 * {@code ResultSet#getObject(String, LocalDateTime.class)} 读取；该处理器先读取
 * PostgreSQL 驱动返回的 OffsetDateTime，再转换为项目 API 使用的 LocalDateTime。
 * 对 H2 测试数据库返回的 Timestamp 也保留兼容处理。
 * </p>
 */
@MappedTypes(LocalDateTime.class)
public class TimestampWithTimeZoneLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    /**
     * 将 LocalDateTime 写入数据库时间列。
     * 入参：JDBC 参数位置、LocalDateTime 值及可选 JDBC 类型；出参：无；流程：使用标准 Timestamp 写入，兼容 PostgreSQL 与 H2。
     *
     * @param ps 预编译语句
     * @param i 参数位置
     * @param parameter 待写入的本地日期时间
     * @param jdbcType JDBC 类型
     * @throws SQLException 写入参数失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setTimestamp(i, Timestamp.valueOf(parameter));
    }

    /**
     * 按列名读取时间值。
     * 入参：结果集和列名；出参：LocalDateTime 或 null；流程：读取 JDBC 原始对象并统一转换。
     *
     * @param rs 查询结果集
     * @param columnName 列名
     * @return 转换后的本地日期时间
     * @throws SQLException 读取结果失败时抛出
     */
    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLocalDateTime(rs.getObject(columnName));
    }

    /**
     * 按列序号读取时间值。
     * 入参：结果集和列序号；出参：LocalDateTime 或 null；流程：读取 JDBC 原始对象并统一转换。
     *
     * @param rs 查询结果集
     * @param columnIndex 列序号
     * @return 转换后的本地日期时间
     * @throws SQLException 读取结果失败时抛出
     */
    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLocalDateTime(rs.getObject(columnIndex));
    }

    /**
     * 读取存储过程返回的时间值。
     * 入参：可调用语句和列序号；出参：LocalDateTime 或 null；流程：读取 JDBC 原始对象并统一转换。
     *
     * @param cs 可调用语句
     * @param columnIndex 列序号
     * @return 转换后的本地日期时间
     * @throws SQLException 读取结果失败时抛出
     */
    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toLocalDateTime(cs.getObject(columnIndex));
    }

    /**
     * 兼容 PostgreSQL TIMESTAMPTZ、普通 SQL 时间戳和测试数据库返回的 LocalDateTime。
     * 入参：JDBC 原始时间对象；出参：LocalDateTime 或 null；流程：按驱动实际返回类型选择无损的本地时间转换方式。
     *
     * @param value JDBC 返回的原始值
     * @return 转换后的本地日期时间
     * @throws SQLException 返回了未支持的时间对象类型时抛出
     */
    private LocalDateTime toLocalDateTime(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new SQLException("不支持的时间列返回类型: " + value.getClass().getName());
    }
}
