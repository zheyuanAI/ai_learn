-- ============================================================================
-- 阶段 0-7 UI 修复：只将默认租户的既有系统种子菜单对齐到正式前端业务路由。
-- 每项迁移同时匹配租户、旧路由和旧组件，绝不覆盖其他租户同编码菜单或租户自定义值。
-- ============================================================================

UPDATE auth_menu
SET route_path = '/master-data?tab=products',
    component_path = 'views/masterdata/MasterDataView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND menu_code = 'master_product'
  AND route_path = '/master-data/products'
  AND component_path = 'views/master/ProductList.vue';

UPDATE auth_menu
SET route_path = '/master-data?tab=warehouses',
    component_path = 'views/masterdata/MasterDataView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND menu_code = 'master_warehouse'
  AND route_path = '/master-data/warehouses'
  AND component_path = 'views/master/WarehouseList.vue';

UPDATE auth_menu
SET route_path = '/inventory/balances',
    component_path = 'views/inventory/InventoryBalanceView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND menu_code = 'master_inventory'
  AND route_path = '/master-data/inventory'
  AND component_path = 'views/master/InventoryBalance.vue';

UPDATE auth_menu
SET route_path = '/purchasing/orders',
    component_path = 'views/purchasing/PurchaseOrderListView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND menu_code = 'purchase_order'
  AND route_path = '/purchase/orders'
  AND component_path = 'views/purchase/PurchaseOrderList.vue';

UPDATE auth_menu
SET route_path = '/purchasing/receipts',
    component_path = 'views/purchasing/PurchaseOrderListView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND menu_code = 'purchase_inbound'
  AND route_path = '/purchase/inbound'
  AND component_path = 'views/purchase/PurchaseInbound.vue';

UPDATE auth_menu
SET route_path = '/purchasing/putaway',
    component_path = 'views/purchasing/PutawayTaskView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND menu_code = 'purchase_putaway'
  AND route_path = '/purchase/putaway'
  AND component_path = 'views/purchase/PutawayTaskList.vue';

UPDATE auth_menu
SET route_path = '/sales/picks',
    component_path = 'views/sales/PickTaskView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND menu_code = 'sales_outbound'
  AND route_path = '/sales/outbound'
  AND component_path = 'views/sales/SalesOutbound.vue';

UPDATE auth_menu
SET route_path = '/mes/dispatch',
    component_path = 'views/manufacturing/DispatchView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND menu_code = 'mes_execution'
  AND route_path = '/mes/execution'
  AND component_path = 'views/mes/OperationExecution.vue';

UPDATE auth_menu
SET route_path = '/gis/site-maps',
    component_path = 'views/insights/SiteMapListView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND menu_code = 'gis'
  AND route_path = '/gis/map'
  AND component_path = 'views/gis/SiteMap.vue';
