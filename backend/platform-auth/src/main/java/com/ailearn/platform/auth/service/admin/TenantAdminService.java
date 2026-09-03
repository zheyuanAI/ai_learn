package com.ailearn.platform.auth.service.admin;

import com.ailearn.platform.auth.domain.dto.admin.TenantUpdateRequest;
import com.ailearn.platform.auth.domain.vo.admin.TenantAdminVo;

/**
 * 租户后台管理业务服务接口。
 * <p>
 * 提供当前租户信息查询与基本配置更新能力，强制基于安全上下文租户范围隔离。
 * </p>
 */
public interface TenantAdminService {

    /**
     * 获取当前安全上下文中的租户详细画像。
     * <p>
     * 【用途】供管理后台展示当前租户的编码、名称、状态与创建时间。
     * 主要入参：无（严格从 TenantContextHolder 读取当前租户 ID）；
     * 返回结果：TenantAdminVo 租户详情；
     * 简要流程：从上下文获取租户 ID，查询租户表，若不存在抛出 404，否则转换为 VO 返回。
     * </p>
     *
     * @return 租户管理视图对象
     */
    TenantAdminVo getCurrentTenantDetail();

    /**
     * 修改当前租户的基本信息与状态。
     * <p>
     * 【用途】供管理员更新租户的企业展示名称或运营状态。
     * 主要入参：request (租户更新参数，含名称与状态)；
     * 返回结果：更新后的 TenantAdminVo 租户详情；
     * 简要流程：从上下文获取租户 ID，加载租户实体，更新对应字段并持久化落库。
     * </p>
     *
     * @param request 租户更新请求参数
     * @return 更新后的租户管理视图对象
     */
    TenantAdminVo updateCurrentTenant(TenantUpdateRequest request);
}
