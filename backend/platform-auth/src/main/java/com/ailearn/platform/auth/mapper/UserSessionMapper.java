package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.entity.UserSession;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户会话 Mapper 数据访问接口。
 */
@Mapper
public interface UserSessionMapper extends BaseMapper<UserSession> {

    /**
     * 根据 JTI 查询会话记录。
     *
     * @param jti JWT 唯一标识
     * @return 会话记录或 null
     */
    default UserSession findByJti(String jti) {
        return selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getJti, jti));
    }

    /**
     * 查询指定用户当前有效的会话记录。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 活跃会话记录或 null
     */
    default UserSession findActiveSessionByUserId(UUID tenantId, UUID userId) {
        return selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getTenantId, tenantId)
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getStatus, "ACTIVE")
                .orderByDesc(UserSession::getLoginAt)
                .last("LIMIT 1"));
    }

    /**
     * 废弃撤销指定用户的所有活跃会话（用于后登踢前）。
     *
     * @param tenantId      租户 ID
     * @param userId        用户 ID
     * @param revokedAt     撤销时间戳
     * @param revokedReason 撤销原因
     * @return 更新行数
     */
    @Update("UPDATE auth_session SET status = 'REVOKED', revoked_at = #{revokedAt}, revoked_reason = #{revokedReason} WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND status = 'ACTIVE'")
    int revokeActiveSessions(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId, @Param("revokedAt") LocalDateTime revokedAt, @Param("revokedReason") String revokedReason);

    /**
     * 根据 JTI 显式撤销指定会话（用于主动注销）。
     *
     * @param jti           JWT 唯一标识
     * @param revokedAt     撤销时间戳
     * @param revokedReason 撤销原因
     * @return 更新行数
     */
    @Update("UPDATE auth_session SET status = 'REVOKED', revoked_at = #{revokedAt}, revoked_reason = #{revokedReason} WHERE jti = #{jti} AND status = 'ACTIVE'")
    int revokeByJti(@Param("jti") String jti, @Param("revokedAt") LocalDateTime revokedAt, @Param("revokedReason") String revokedReason);
}
