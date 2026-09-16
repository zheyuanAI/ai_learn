/**
 * 看板图表通用主题与样式配置
 * 与项目深色工业科技风设计语言统一：
 * 采用深邃夜空底色、青蓝高亮 (#38bdf8)、翠绿 (#34d399)、暖琥珀 (#f59e0b)、警戒红 (#f87171)
 */

export const CHART_COLORS = {
  accent: "#38bdf8",
  accentDeep: "#0284c7",
  green: "#34d399",
  greenDeep: "#059669",
  amber: "#f59e0b",
  amberDeep: "#d97706",
  red: "#f87171",
  redDeep: "#dc2626",
  purple: "#a78bfa",
  purpleDeep: "#7c3aed",
  textPrimary: "#f8fafc",
  textSecondary: "#94a3b8",
  textDim: "#64748b",
  border: "rgba(56, 189, 248, 0.15)",
  bgCard: "#081021",
};

/**
 * 通用 Tooltip 样式配置
 */
export const DEFAULT_TOOLTIP = {
  backgroundColor: "rgba(8, 16, 33, 0.95)",
  borderColor: "rgba(56, 189, 248, 0.3)",
  borderWidth: 1,
  padding: [8, 12],
  textStyle: {
    color: "#f8fafc",
    fontSize: 12,
  },
};
