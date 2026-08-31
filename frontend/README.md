# Frontend

## 目标
- 使用 Vue 3 + TypeScript + Vite 承接真实业务页面开发
- 当前只搭壳，不写具体业务逻辑
- 页面结构与导航用于表达一期目标范围，不代表业务功能已经实现

## 规格入口
- 当前开发计划与一期范围：`../docs/specs/00-project/正式项目计划.md`
- 页面范围与交互基线：`../docs/specs/00-project/原型与交互说明.md`、`../docs/prototype/README.md`
- 领域规则、接口与验收：对应领域目录的 `概述.md`、`领域模型.md`、`接口契约.md`、`验收标准.md`

## 本地开发环境基线
- 本前端工程的 Node 版本由 `package.json` 的 `engines` 约束为 20.x。
- Java、Maven、PostgreSQL、服务端口及参考工程隔离规则以 `../docs/specs/00-project/架构设计.md` 和根目录 `../README.md` 为准。

## 下一步
1. 接入登录页与租户上下文
2. 落菜单、权限点和路由守卫
3. 沿“销售需求—采购—生产—仓储—发货”黄金业务闭环逐页接入真实接口
