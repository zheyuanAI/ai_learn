package com.ailearn.platform.core.quality.domain;

/**
 * 采购到货质量处置类型。
 */
public enum QualityDispositionType {
    /** 合格品放行到收货暂存位。 */
    Release,
    /** 不合格品退回供应方。 */
    Return,
    /** 不合格品报废。 */
    Scrap
}
