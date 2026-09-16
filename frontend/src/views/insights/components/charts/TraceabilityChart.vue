<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
/**
 * 全链路追溯六轴雷达覆盖图 (TraceabilityChart)
 * 职责：展示六大业务事实域（采购、销售、工单、质检、设备、告警）的闭环覆盖分布
 */

import { ref, watch } from "vue";
import type { EChartsOption } from "echarts";
import { useChart } from "./useChart";
import { CHART_COLORS, DEFAULT_TOOLTIP } from "./theme";
import { parseMetricNumber } from "../../utils/metric-dictionary";

const props = defineProps<{
  metrics?: Record<string, unknown>;
}>();

const chartRef = ref<HTMLElement | null>(null);

function getOption(): EChartsOption {
  const purchase = parseMetricNumber(props.metrics?.purchase_order_count, 10);
  const sales = parseMetricNumber(props.metrics?.sales_order_count, 10);
  const workOrder = parseMetricNumber(props.metrics?.work_order_count, 10);
  const inspect = parseMetricNumber(props.metrics?.inspection_count, 10);
  const device = parseMetricNumber(props.metrics?.device_count, 10);
  const alarm = parseMetricNumber(props.metrics?.alarm_count, 5);

  const values = [purchase, sales, workOrder, inspect, device, alarm];
  const maxVal = Math.max(...values, 20);
  const axisMax = Math.ceil(maxVal * 1.2);

  return {
    tooltip: {
      ...DEFAULT_TOOLTIP,
    },
    radar: {
      indicator: [
        { name: "采购", max: axisMax },
        { name: "销售", max: axisMax },
        { name: "工单", max: axisMax },
        { name: "质检", max: axisMax },
        { name: "设备", max: axisMax },
        { name: "告警", max: axisMax },
      ],
      radius: "68%",
      center: ["50%", "52%"],
      shape: "polygon",
      splitArea: {
        areaStyle: {
          color: ["rgba(56, 189, 248, 0.02)", "rgba(56, 189, 248, 0.05)"],
        },
      },
      splitLine: {
        lineStyle: {
          color: "rgba(56, 189, 248, 0.15)",
        },
      },
      axisLine: {
        lineStyle: {
          color: "rgba(56, 189, 248, 0.2)",
        },
      },
      axisName: {
        color: CHART_COLORS.textSecondary,
        fontSize: 10,
        fontFamily: "var(--font-mono, monospace)",
      },
    },
    series: [
      {
        type: "radar",
        data: [
          {
            value: values,
            name: "全域事实关联",
            areaStyle: {
              color: "rgba(56, 189, 248, 0.16)",
            },
            lineStyle: {
              color: CHART_COLORS.accent,
              width: 2,
            },
            itemStyle: {
              color: CHART_COLORS.accent,
              borderColor: "#ffffff",
              borderWidth: 1,
            },
            symbol: "circle",
            symbolSize: 5,
          },
        ],
      },
    ],
  };
}

const { updateChart } = useChart(chartRef, getOption);

watch(() => props.metrics, () => {
  updateChart();
}, { deep: true });
</script>

<style scoped>
.chart-container {
  width: 100%;
  height: 180px;
}
</style>
