/**
 * 用途：生成 datetime-local 控件可直接显示的当前本地时间。
 * 出参：不含时区后缀的 YYYY-MM-DDTHH:mm 字符串。
 * 流程：使用本地日期分量格式化，避免 toISOString() 先转换 UTC 导致界面提前 8 小时。
 */
export function currentLocalDateTimeValue(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
    + `T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}
