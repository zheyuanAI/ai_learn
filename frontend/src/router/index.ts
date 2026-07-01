import { createRouter, createWebHistory } from "vue-router";
import PrototypeHome from "../views/PrototypeHome.vue";
import DomainView from "../views/DomainView.vue";

const routes = [
  { path: "/", component: PrototypeHome },
  {
    path: "/erp-wms",
    component: DomainView,
    props: {
      title: "ERP/WMS",
      summary: "采购、入库、上架、销售、冻结、拣货、出库、调拨、盘点与应收应付。",
      specPath: "specs/10-erp-wms",
      prototypePath: "prototype/pages/purchase-inbound.html / sales-outbound.html",
    },
  },
  {
    path: "/mes",
    component: DomainView,
    props: {
      title: "MES",
      summary: "BOM、工艺路线、工单、派工、报工、质检、返工、成品入库。",
      specPath: "specs/20-mes",
      prototypePath: "prototype/pages/work-order.html",
    },
  },
  {
    path: "/iot",
    component: DomainView,
    props: {
      title: "IoT",
      summary: "MQTT、Modbus TCP、OPC UA 模拟接入，遥测、状态、告警联动。",
      specPath: "specs/30-iot-digital-twin",
      prototypePath: "prototype/pages/device-alarm.html",
    },
  },
  {
    path: "/gis",
    component: DomainView,
    props: {
      title: "GIS / Dashboard",
      summary: "地图、综合看板、简化三维与告警联动。",
      specPath: "specs/40-gis-dashboard",
      prototypePath: "prototype/pages/site-map.html / digital-twin.html",
    },
  },
  {
    path: "/ai",
    component: DomainView,
    props: {
      title: "AI Assistant",
      summary: "知识库、只读工具、日报与调用审计。",
      specPath: "specs/50-ai-assistant",
      prototypePath: "prototype/pages/ai-assistant.html / knowledge-base.html / tool-audit.html",
    },
  },
];

export default createRouter({
  history: createWebHistory(),
  routes,
});
