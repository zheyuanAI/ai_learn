package com.ailearn.platform.auth.domain.vo;

import java.io.Serializable;
import java.util.UUID;

/**
 * 派工用同租户操作员目录视图。
 * <p>只暴露派工所需的最小身份字段，不返回密码、联系方式或角色授权明细。</p>
 */
public record OperatorDirectoryVo(UUID id, String userNo, String username, String realName,
                                  String status) implements Serializable {
}
