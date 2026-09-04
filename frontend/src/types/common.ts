/**
 * 公共基础领域类型定义 (Common Domain Types)
 * 遵循阶段 2-7 前端实施规范：
 * 1. 数量、金额等采用字符串传输与显示，杜绝前端浮点精度损失
 * 2. 按钮和操作入口依据 allowedActions 控制
 * 3. 规范通用分页与四态模型
 */

/**
 * 分页查询基础入参
 */
export interface PageQuery {
  page?: number;
  size?: number;
  keyword?: string;
  status?: string;
  sortField?: string;
  sortOrder?: "asc" | "desc";
  [key: string]: any;
}

/**
 * 分页查询通用结果包装
 */
export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
  totalPages?: number;
}

/**
 * 业务操作入口权限判定（来自后端详情响应）
 */
export interface AllowedAction {
  action: string;
  enabled: boolean;
  reason?: string;
}

/**
 * 界面四态枚举
 */
export type ViewState = "loading" | "ready" | "empty" | "error" | "unauthorized";

/**
 * 状态徽标颜色类别
 */
export type BadgeType = "default" | "primary" | "success" | "warning" | "danger" | "info";

/**
 * 键值对字典选项
 */
export interface OptionItem<T = string | number> {
  label: string;
  value: T;
  disabled?: boolean;
  tag?: string;
}

/**
 * 基础实体生命周期字段
 */
export interface BaseEntity {
  id: string | number;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
  remark?: string;
}
