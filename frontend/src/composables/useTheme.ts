import { useDark, useToggle } from "@vueuse/core";

/**
 * 响应式暗黑模式管理
 * 自动持久化至 localStorage，并在 <html> 上切换 .dark 类名
 * 本项目默认使用暗色模式，后续可在顶栏添加切换按钮
 */
export const isDark = useDark({
  selector: "html",
  attribute: "class",
  valueDark: "dark",
  valueLight: "",
  initialValue: "dark",
});

export const toggleTheme = useToggle(isDark);
