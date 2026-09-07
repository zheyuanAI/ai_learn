-- ============================================================================
-- 阶段 0-7 UI 修复：把演示菜单对齐到当前前端正式业务路由。
-- 只修正菜单路由和组件元数据，不改权限关系；按 menu_code 更新可兼容多个租户。
-- ============================================================================

UPDATE auth_menu
SET route_path = '/master-data?tab=products',
    component_path = 'views/masterdata/MasterDataView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'master_product'
  AND (
      route_path IS DISTINCT FROM '/master-data?tab=products'
      OR component_path IS DISTINCT FROM 'views/masterdata/MasterDataView.vue'
  );

UPDATE auth_menu
SET route_path = '/master-data?tab=warehouses',
    component_path = 'views/masterdata/MasterDataView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'master_warehouse'
  AND (
      route_path IS DISTINCT FROM '/master-data?tab=warehouses'
      OR component_path IS DISTINCT FROM 'views/masterdata/MasterDataView.vue'
  );

UPDATE auth_menu
SET route_path = '/inventory/balances',
    component_path = 'views/inventory/InventoryBalanceView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'master_inventory'
  AND (
      route_path IS DISTINCT FROM '/inventory/balances'
      OR component_path IS DISTINCT FROM 'views/inventory/InventoryBalanceView.vue'
  );

UPDATE auth_menu
SET route_path = '/purchasing/orders',
    component_path = 'views/purchasing/PurchaseOrderListView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'purchase_order'
  AND (
      route_path IS DISTINCT FROM '/purchasing/orders'
      OR component_path IS DISTINCT FROM 'views/purchasing/PurchaseOrderListView.vue'
  );

UPDATE auth_menu
SET route_path = '/purchasing/receipts',
    component_path = 'views/purchasing/PurchaseOrderListView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'purchase_inbound'
  AND (
      route_path IS DISTINCT FROM '/purchasing/receipts'
      OR component_path IS DISTINCT FROM 'views/purchasing/PurchaseOrderListView.vue'
  );

UPDATE auth_menu
SET route_path = '/purchasing/putaway',
    component_path = 'views/purchasing/PutawayTaskView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'purchase_putaway'
  AND (
      route_path IS DISTINCT FROM '/purchasing/putaway'
      OR component_path IS DISTINCT FROM 'views/purchasing/PutawayTaskView.vue'
  );

UPDATE auth_menu
SET route_path = '/sales/picks',
    component_path = 'views/sales/PickTaskView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'sales_outbound'
  AND (
      route_path IS DISTINCT FROM '/sales/picks'
      OR component_path IS DISTINCT FROM 'views/sales/PickTaskView.vue'
  );

UPDATE auth_menu
SET route_path = '/mes/dispatch',
    component_path = 'views/manufacturing/DispatchView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'mes_execution'
  AND (
      route_path IS DISTINCT FROM '/mes/dispatch'
      OR component_path IS DISTINCT FROM 'views/manufacturing/DispatchView.vue'
  );

UPDATE auth_menu
SET route_path = '/gis/site-maps',
    component_path = 'views/insights/SiteMapListView.vue',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'gis'
  AND (
      route_path IS DISTINCT FROM '/gis/site-maps'
      OR component_path IS DISTINCT FROM 'views/insights/SiteMapListView.vue'
  );
