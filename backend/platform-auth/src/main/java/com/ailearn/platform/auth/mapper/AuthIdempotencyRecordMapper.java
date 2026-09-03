package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.entity.AuthIdempotencyRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 认证幂等记录 Mapper 数据访问接口。
 */
@Mapper
public interface AuthIdempotencyRecordMapper extends BaseMapper<AuthIdempotencyRecord> {

    /**
     * 根据租户 ID、端点与幂等键查询幂等记录。
     *
     * @param tenantId       租户 ID
     * @param endpoint       接口端点
     * @param idempotencyKey 幂等唯一键
     * @return 幂等记录或 null
     */
    @Select("SELECT * FROM auth_idempotency_record WHERE tenant_id = #{tenantId} AND endpoint = #{endpoint} AND idempotency_key = #{idempotencyKey} LIMIT 1")
    AuthIdempotencyRecord findByKey(@Param("tenantId") UUID tenantId, @Param("endpoint") String endpoint, @Param("idempotencyKey") String idempotencyKey);
}
