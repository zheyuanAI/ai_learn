# Backend

## 模块
- `platform-gateway`：统一入口与后续网关能力
- `platform-auth`：认证、租户、用户、角色、菜单
- `platform-core`：ERP/WMS、MES、统计与只读业务查询
- `platform-iot`：设备、遥测、协议模拟与告警
- `platform-shared`：公共类型、异常、基础配置

## 当前状态
- 已创建多模块父工程和服务启动类
- 已预留 `/internal/ping` 探活接口
- 还未接数据库、鉴权、消息队列和业务领域代码
