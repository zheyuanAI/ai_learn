package com.ailearn.platform.core.inventory.infrastructure;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Core PostgreSQL 幂等记录 Mapper。
 * <p>
 * 只允许创建、完成和失败失效三类动作；库存业务事实仍由外层事务负责，Mapper 不提供物理删除。
 * </p>
 */
@Mapper
public interface CoreIdempotencyMapper {

    /**
     * 将已过期的活动记录逻辑失效，释放租户内幂等键的部分唯一索引槽位。
     *
     * @param tenantId 可信租户
     * @param idempotencyKey 幂等键
     * @return 失效记录数
     */
    @Update("""
            UPDATE core_idempotency_record
               SET status = 'FAILED',
                   isdel = 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId}
               AND idempotency_key = #{idempotencyKey}
               AND expires_at <= CURRENT_TIMESTAMP
               AND isdel = 0
            """)
    int expire(@Param("tenantId") UUID tenantId,
               @Param("idempotencyKey") String idempotencyKey);

    /**
     * 竞争创建幂等处理中记录；相同租户和键由数据库唯一索引仲裁。
     *
     * @param tenantId 可信租户
     * @param idempotencyKey 幂等键
     * @param operation 业务操作域
     * @param requestHash 请求载荷摘要
     * @param ttlSeconds 幂等保留秒数
     * @param operatorId 可信操作人
     * @return 成功创建为 1，已被其他请求占用为 0
     */
    @Insert("""
            INSERT INTO core_idempotency_record
                (id, tenant_id, idempotency_key, operation, request_hash, claim_token, status, expires_at,
                 created_by, created_at, updated_by, updated_at, isdel)
            VALUES
                (#{id}, #{tenantId}, #{idempotencyKey}, #{operation}, #{requestHash}, #{claimToken}, 'PENDING',
                 CURRENT_TIMESTAMP + (#{ttlSeconds} * INTERVAL '1 second'),
                 #{operatorId}, CURRENT_TIMESTAMP, #{operatorId}, CURRENT_TIMESTAMP, 0)
            ON CONFLICT (tenant_id, idempotency_key)
                WHERE isdel = 0 DO NOTHING
            """)
    int tryAcquire(@Param("id") UUID id,
                   @Param("claimToken") UUID claimToken,
                   @Param("tenantId") UUID tenantId,
                   @Param("idempotencyKey") String idempotencyKey,
                   @Param("operation") String operation,
                   @Param("requestHash") String requestHash,
                   @Param("ttlSeconds") long ttlSeconds,
                   @Param("operatorId") UUID operatorId);

    /**
     * 查询未过期幂等记录，所有读取均限定可信租户。
     *
     * @param tenantId 可信租户
     * @param idempotencyKey 幂等键
     * @return 幂等记录或 null
     */
    @Select("""
            SELECT tenant_id, idempotency_key, request_hash, claim_token, status, response_body, expires_at
              FROM core_idempotency_record
             WHERE tenant_id = #{tenantId}
               AND idempotency_key = #{idempotencyKey}
               AND expires_at > CURRENT_TIMESTAMP
               AND isdel = 0
             LIMIT 1
            """)
    @Results(id = "coreIdempotencyRowMap", value = {
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "idempotencyKey", column = "idempotency_key"),
            @Result(property = "requestHash", column = "request_hash"),
            @Result(property = "claimToken", column = "claim_token"),
            @Result(property = "status", column = "status"),
            @Result(property = "responseBody", column = "response_body"),
            @Result(property = "expiresAt", column = "expires_at")
    })
    CoreIdempotencyRow findActive(@Param("tenantId") UUID tenantId,
                                  @Param("idempotencyKey") String idempotencyKey);

    /**
     * 在业务事务提交后保存成功响应。
     *
     * @param tenantId 可信租户
     * @param idempotencyKey 幂等键
     * @param responseBody 首次成功响应 JSON
     * @param ttlSeconds 成功结果保留秒数
     * @param operatorId 可信操作人
     * @return 更新记录数
     */
    @Update("""
            UPDATE core_idempotency_record
               SET status = 'SUCCESS',
                   response_body = #{responseBody},
                   expires_at = CURRENT_TIMESTAMP + (#{ttlSeconds} * INTERVAL '1 second'),
                   updated_by = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId}
               AND idempotency_key = #{idempotencyKey}
               AND claim_token = #{claimToken}
               AND status = 'PENDING'
               AND isdel = 0
            """)
    int complete(@Param("tenantId") UUID tenantId,
                 @Param("idempotencyKey") String idempotencyKey,
                 @Param("claimToken") UUID claimToken,
                 @Param("responseBody") String responseBody,
                 @Param("ttlSeconds") long ttlSeconds,
                 @Param("operatorId") UUID operatorId);

    /**
     * 旧接口兼容完成入口；新业务必须使用带 claim token 的 CAS 入口。
     */
    @Update("""
            UPDATE core_idempotency_record
               SET status = 'SUCCESS', response_body = #{responseBody},
                   expires_at = CURRENT_TIMESTAMP + (#{ttlSeconds} * INTERVAL '1 second'),
                   updated_by = #{operatorId}, updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId} AND idempotency_key = #{idempotencyKey}
               AND isdel = 0
            """)
    int completeLegacy(@Param("tenantId") UUID tenantId,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("responseBody") String responseBody,
                       @Param("ttlSeconds") long ttlSeconds,
                       @Param("operatorId") UUID operatorId);

    /**
     * 业务失败时逻辑失效幂等记录，允许客户端使用新请求重试。
     *
     * @param tenantId 可信租户
     * @param idempotencyKey 幂等键
     * @param errorMessage 失败原因
     * @param operatorId 可信操作人
     * @return 更新记录数
     */
    @Update("""
            UPDATE core_idempotency_record
               SET status = 'FAILED',
                   error_message = #{errorMessage},
                   isdel = 1,
                   updated_by = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId}
               AND idempotency_key = #{idempotencyKey}
               AND claim_token = #{claimToken}
               AND status = 'PENDING'
               AND isdel = 0
            """)
    int fail(@Param("tenantId") UUID tenantId,
             @Param("idempotencyKey") String idempotencyKey,
             @Param("claimToken") UUID claimToken,
             @Param("errorMessage") String errorMessage,
             @Param("operatorId") UUID operatorId);

    /**
     * 旧接口兼容失败入口；新业务必须使用带 claim token 的 CAS 入口。
     */
    @Update("""
            UPDATE core_idempotency_record
               SET status = 'FAILED', error_message = #{errorMessage},
                   isdel = 1, updated_by = #{operatorId}, updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId} AND idempotency_key = #{idempotencyKey}
               AND isdel = 0
            """)
    int failLegacy(@Param("tenantId") UUID tenantId,
                   @Param("idempotencyKey") String idempotencyKey,
                   @Param("errorMessage") String errorMessage,
                   @Param("operatorId") UUID operatorId);
}
