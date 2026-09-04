package com.ailearn.platform.core.masterdata.domain.service;

import com.ailearn.platform.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 六类主数据共用的领域校验器。
 */
public final class MasterDataValidator {

    private MasterDataValidator() {
    }

    /**
     * 规范化并校验租户内业务编码。
     *
     * @param field 字段名称
     * @param value 原始编码
     * @return 去除首尾空白后的编码
     */
    public static String requireCode(String field, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw validation(field, "不能为空");
        }
        if (normalized.length() > 64) {
            throw validation(field, "长度不能超过 64 个字符");
        }
        if (normalized.chars().anyMatch(Character::isWhitespace)) {
            throw validation(field, "不能包含空白字符");
        }
        return normalized;
    }

    /**
     * 规范化并校验主数据名称。
     *
     * @param field 字段名称
     * @param value 原始名称
     * @return 去除首尾空白后的名称
     */
    public static String requireName(String field, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw validation(field, "不能为空");
        }
        if (normalized.length() > 128) {
            throw validation(field, "长度不能超过 128 个字符");
        }
        return normalized;
    }

    /**
     * 规范化可选文本，超长文本拒绝写入。
     *
     * @param field 字段名称
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 空白文本转 null 后的值
     */
    public static String optionalText(String field, String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw validation(field, "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    /**
     * 将 HTTP 字符串数值转换为非负 BigDecimal，并限制 PostgreSQL NUMERIC(19,6) 范围。
     *
     * @param field 字段名称
     * @param value 数值字符串，空值表示未填写
     * @return 非负 BigDecimal，空值返回 null
     */
    public static BigDecimal optionalNonNegativeDecimal(String field, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        final BigDecimal decimal;
        try {
            decimal = new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw validation(field, "必须是合法数字");
        }
        if (decimal.signum() < 0) {
            throw validation(field, "不能为负数");
        }
        if (decimal.scale() > 6 || decimal.precision() > 19) {
            throw validation(field, "最多支持 19 位有效数字且小数不超过 6 位");
        }
        return decimal.setScale(Math.max(decimal.scale(), 0), RoundingMode.UNNECESSARY);
    }

    /**
     * 校验可选整数范围。
     *
     * @param field 字段名称
     * @param value 整数值
     * @param min 最小值
     * @param max 最大值
     * @return 校验后的值
     */
    public static Integer optionalInteger(String field, Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw validation(field, "必须在 " + min + " 到 " + max + " 之间");
        }
        return value;
    }

    /**
     * 抛出带字段信息的 422 参数校验异常。
     *
     * @param field 字段名称
     * @param message 校验说明
     * @return 不会正常返回，仅用于表达式抛出
     */
    public static ValidationException validation(String field, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new ValidationException("主数据参数校验失败", errors);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
