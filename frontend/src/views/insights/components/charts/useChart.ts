/**
 * ECharts 挂载与响应式自适应统一 Hook (useChart)
 * 职责：
 * 1. 统一管理图表实例的初始化、按需重绘与卸载销毁；
 * 2. 基于 ResizeObserver 监听容器尺寸微调，保证窗口与卡片布局缩放时图表自适应；
 * 3. 避免重复样板代码，提升代码健壮度与可维护性。
 */

import { shallowRef, onMounted, onUnmounted, type Ref } from "vue";
import type { EChartsOption, EChartsType } from "echarts";
import * as echarts from "echarts/core";
import { BarChart, GaugeChart, PieChart, RadarChart } from "echarts/charts";
import { GridComponent, LegendComponent, RadarComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

// 修改用途：只注册当前七个看板图表实际使用的类型与组件，降低首次进入看板时的脚本体积。
echarts.use([
  BarChart, GaugeChart, PieChart, RadarChart,
  GridComponent, LegendComponent, RadarComponent, TooltipComponent,
  CanvasRenderer,
]);

export function useChart(
  containerRef: Ref<HTMLElement | null>,
  getOption: () => EChartsOption
) {
  const chartInstance = shallowRef<EChartsType | null>(null);
  let resizeObserver: ResizeObserver | null = null;

  const initChart = () => {
    if (!containerRef.value) return;
    if (!chartInstance.value) {
      chartInstance.value = echarts.init(containerRef.value);
    }
    const option = getOption();
    chartInstance.value.setOption(option, true);
  };

  const updateChart = () => {
    if (!chartInstance.value) {
      initChart();
      return;
    }
    const option = getOption();
    chartInstance.value.setOption(option, true);
  };

  onMounted(() => {
    initChart();
    if (containerRef.value && typeof ResizeObserver !== "undefined") {
      resizeObserver = new ResizeObserver(() => {
        chartInstance.value?.resize();
      });
      resizeObserver.observe(containerRef.value);
    }
  });

  onUnmounted(() => {
    if (resizeObserver) {
      resizeObserver.disconnect();
      resizeObserver = null;
    }
    if (chartInstance.value) {
      chartInstance.value.dispose();
      chartInstance.value = null;
    }
  });

  return {
    chartInstance,
    updateChart,
  };
}
