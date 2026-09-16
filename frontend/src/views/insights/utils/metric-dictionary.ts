/**
 * 看板指标字典定义与转换工具
 * 职责：
 * 1. 提供后端各事实域指标键（snake_case）到中文标签、单位及语义分类的规范映射；
 * 2. 避免前端直接暴露原始英文 key，提升用户体验；
 * 3. 辅助提取核心 Hero KPI 与次级展示指标。
 */

export interface MetricMeta {
  label: string;
  unit?: string;
  isQuantity?: boolean;
}

export const METRIC_DICTIONARY: Record<string, MetricMeta> = {
  // 1. 库存事实 (inventory)
  balance_count: { label: "库位记录", unit: "条" },
  on_hand_qty: { label: "在手总量", unit: "件", isQuantity: true },
  reserved_qty: { label: "业务预留", unit: "件", isQuantity: true },
  available_qty: { label: "可用库存", unit: "件", isQuantity: true },

  // 2. 履约事实 (fulfillment)
  purchase_order_count: { label: "采购订单", unit: "单" },
  sales_order_count: { label: "销售订单", unit: "单" },
  ordered_qty: { label: "需求总量", unit: "件", isQuantity: true },
  received_qty: { label: "已收货量", unit: "件", isQuantity: true },
  picked_qty: { label: "已拣货量", unit: "件", isQuantity: true },
  shipped_qty: { label: "已发货量", unit: "件", isQuantity: true },
  completed_order_count: { label: "完结订单", unit: "单" },

  // 3. 制造事实 (manufacturing)
  work_order_count: { label: "工单总数", unit: "单" },
  planned_qty: { label: "计划总量", unit: "件", isQuantity: true },
  in_progress_count: { label: "执行中工单", unit: "单" },
  completed_count: { label: "已完工工单", unit: "单" },
  reported_qty: { label: "报工总量", unit: "件", isQuantity: true },
  qualified_qty: { label: "报工合格量", unit: "件", isQuantity: true },
  defect_qty: { label: "报工缺陷量", unit: "件", isQuantity: true },
  issued_qty: { label: "领料总量", unit: "件", isQuantity: true },
  returned_qty: { label: "退料总量", unit: "件", isQuantity: true },
  quality_blocked_count: { label: "质检阻滞", unit: "单" },

  // 4. 质量事实 (quality)
  inspection_count: { label: "检验批数", unit: "批" },
  inspected_qty: { label: "检验总量", unit: "件", isQuantity: true },
  unqualified_qty: { label: "不合格量", unit: "件", isQuantity: true },
  passed_count: { label: "合格批数", unit: "批" },
  failed_count: { label: "不合格批数", unit: "批" },
  disposition_count: { label: "处置单数", unit: "单" },
  released_qty: { label: "放行总量", unit: "件", isQuantity: true },
  scrapped_qty: { label: "报废总量", unit: "件", isQuantity: true },

  // 5. 设备事实 (device)
  device_count: { label: "设备总数", unit: "台" },
  active_device_count: { label: "激活设备", unit: "台" },
  online_device_count: { label: "在线设备", unit: "台" },
  offline_device_count: { label: "离线设备", unit: "台" },

  // 6. 告警事实 (alarm)
  alarm_count: { label: "告警总数", unit: "起" },
  triggered_count: { label: "活动告警", unit: "起" },
  acked_count: { label: "已确认告警", unit: "起" },
  recovered_unacked_count: { label: "恢复未确认", unit: "起" },
  recovered_count: { label: "已闭环告警", unit: "起" },

  // 7. 追溯事实 (traceability)
  transaction_count: { label: "交易流水", unit: "笔" },
  transaction_qty: { label: "流转总量", unit: "件", isQuantity: true },
  traceable_order_count: { label: "可追溯订单", unit: "单" },
};

/**
 * 将任意输入安全转化为数字，防止 NaN 导致图表渲染崩溃
 * @param val 待解析值
 * @param fallback 降级默认值
 */
export function parseMetricNumber(val: unknown, fallback: number = 0): number {
  if (typeof val === "number") return isNaN(val) ? fallback : val;
  if (typeof val === "string") {
    const parsed = parseFloat(val);
    return isNaN(parsed) ? fallback : parsed;
  }
  return fallback;
}
