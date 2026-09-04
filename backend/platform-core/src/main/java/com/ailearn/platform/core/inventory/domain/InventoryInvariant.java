package com.ailearn.platform.core.inventory.domain;

import com.ailearn.platform.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 库存内核的纯函数与不变量校验。
 * <p>
 * 该类不访问数据库或线程上下文，便于在边界测试数量精度、余额公式和稳定锁键。
 * </p>
 */
public final class InventoryInvariant {

    /** V2 NUMERIC(19,6) 的统一小数位。 */
    public static final int SCALE = 6;
    /** V2 NUMERIC(19,6) 允许的整数位数。 */
    public static final int INTEGER_DIGITS = 13;
    /** 统一零值，避免不同 scale 造成 JSON/断言不一致。 */
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);

    private InventoryInvariant() {
    }

    /**
     * 校验数量为正且可以无损写入 NUMERIC(19,6)，并返回统一 scale 的副本。
     *
     * @param fieldName 字段名称
     * @param quantity 待校验数量
     * @return scale 固定为 6 的正数
     * @throws ValidationException 数量为空、非正、精度超限或 scale 超限
     */
    public static BigDecimal requirePositive(String fieldName, BigDecimal quantity) {
        if (quantity == null) {
            throw new ValidationException(fieldName + "不能为空");
        }
        if (quantity.signum() <= 0) {
            throw new ValidationException(fieldName + "必须大于 0");
        }
        if (quantity.scale() > SCALE) {
            throw new ValidationException(fieldName + "最多支持 6 位小数");
        }
        int integerDigits = Math.max(quantity.precision() - quantity.scale(), 0);
        if (integerDigits > INTEGER_DIGITS) {
            throw new ValidationException(fieldName + "超出 NUMERIC(19,6) 范围");
        }
        return quantity.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * 校验数量非负并统一为 V2 的六位小数。
     *
     * @param fieldName 字段名称
     * @param quantity 待校验数量
     * @return scale 固定为 6 的非负数
     * @throws ValidationException 数量为空、为负或精度超限
     */
    public static BigDecimal requireNonNegative(String fieldName, BigDecimal quantity) {
        if (quantity == null) {
            throw new ValidationException(fieldName + "不能为空");
        }
        if (quantity.signum() < 0) {
            throw new ValidationException(fieldName + "不能为负数");
        }
        if (quantity.scale() > SCALE) {
            throw new ValidationException(fieldName + "最多支持 6 位小数");
        }
        int integerDigits = Math.max(quantity.precision() - quantity.scale(), 0);
        if (integerDigits > INTEGER_DIGITS) {
            throw new ValidationException(fieldName + "超出 NUMERIC(19,6) 范围");
        }
        return quantity.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * 兼容持久化映射层的零值校验命名，语义等同于 {@link #requireNonNegative(String, BigDecimal)}。
     * 入参：字段名称和待校验数量；出参：固定六位小数的非负数；流程：复用统一 NUMERIC(19,6) 校验，避免各层出现不同精度规则。
     *
     * @param fieldName 字段名称
     * @param quantity 待校验数量
     * @return 固定六位小数的非负数
     */
    public static BigDecimal requirePositiveOrZero(String fieldName, BigDecimal quantity) {
        return requireNonNegative(fieldName, quantity);
    }

    /**
     * 计算并校验库存可用量。
     *
     * @param onHandQty  实物库存
     * @param reservedQty 有效预留
     * @return {@code onHandQty - reservedQty}
     * @throws ValidationException 余额为负或预留超过实物库存
     */
    public static BigDecimal availableQty(BigDecimal onHandQty, BigDecimal reservedQty) {
        BigDecimal onHand = requireNonNegative("onHandQty", onHandQty);
        BigDecimal reserved = requireNonNegative("reservedQty", reservedQty);
        BigDecimal available = onHand.subtract(reserved).setScale(SCALE, RoundingMode.UNNECESSARY);
        if (available.signum() < 0) {
            throw new ValidationException("库存不变量被破坏：reservedQty 不能大于 onHandQty");
        }
        return available;
    }

    /**
     * 校验某个变更后的余额仍满足库存不变量。
     *
     * @param onHandQty 变更后的实物库存
     * @param reservedQty 变更后的有效预留
     * @return 变更后的可用量
     */
    public static BigDecimal requireBalanced(BigDecimal onHandQty, BigDecimal reservedQty) {
        return availableQty(onHandQty, reservedQty);
    }

    /**
     * 校验对象不是 null，统一把错误暴露为受控请求校验异常。
     *
     * @param fieldName 字段名称
     * @param value 字段值
     * @param <T> 值类型
     * @return 原值
     */
    public static <T> T requirePresent(String fieldName, T value) {
        if (value == null) {
            throw new ValidationException(fieldName + "不能为空");
        }
        return Objects.requireNonNull(value);
    }
}
