import { createRouter, createWebHistory } from "vue-router";
import PrototypeHome from "../views/PrototypeHome.vue";
import DomainView from "../views/DomainView.vue";

const routes = [
  { path: "/", component: PrototypeHome },
  {
    path: "/erp-wms",
    component: DomainView,
    props: {
      title: "供需与仓储",
      summary: "销售需求、生产工单与采购来源人工关联；收货进入暂存位，上架与拣货只移位，销售预留后由发货扣减实物库存。",
      specPath: "docs/specs/10-erp-wms",
      prototypePath: "docs/prototype/pages/purchase-inbound.html / sales-outbound.html",
    },
  },
  {
    path: "/mes",
    component: DomainView,
    props: {
      title: "制造执行",
      summary: "生产工单关联来源销售行，以领退料、派工、OperationExecution、报工、质检和成品入库记录执行事实。",
      specPath: "docs/specs/20-mes",
      prototypePath: "docs/prototype/pages/work-order.html",
    },
  },
  {
    path: "/iot",
    component: DomainView,
    props: {
      title: "IoT 设备事实",
      summary: "一期 MQTT 消息按 message_id/sequence 去重，遥测、设备状态与告警分别保存，并补充工序执行上下文。",
      specPath: "docs/specs/30-iot-digital-twin",
      prototypePath: "docs/prototype/pages/device-alarm.html",
    },
  },
  {
    path: "/gis",
    component: DomainView,
    props: {
      title: "二维地图与看板",
      summary: "只读展示库存、订单、生产、质量、设备与告警事实，不建立第二套事实来源。",
      specPath: "docs/specs/40-gis-dashboard",
      prototypePath: "docs/prototype/pages/site-map.html / dashboard.html",
    },
  },
  {
    path: "/ai",
    component: DomainView,
    props: {
      title: "AI 只读助手",
      summary: "通过受权限约束的只读工具查询库存、订单、工单、告警与追溯信息，并展示来源与调用审计。",
      specPath: "docs/specs/50-ai-assistant",
      prototypePath: "docs/prototype/pages/ai-assistant.html / tool-audit.html",
    },
  },
];

export default createRouter({
  history: createWebHistory(),
  routes,
});
