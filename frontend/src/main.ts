import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";

// Element Plus 暗色模式变量 + 自定义工业主题覆盖
import "./styles/index.css";

// Dayjs 中文语言包（确保 DatePicker 等组件完全中文本地化）
import "dayjs/locale/zh-cn";
import dayjs from "dayjs";
dayjs.locale("zh-cn");

// 项目基础样式
import "./styles.css";

createApp(App).use(createPinia()).use(router).mount("#app");
