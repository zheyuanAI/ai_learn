/**
 * 主数据管理 API 服务 (Master Data API)
 * 提供商品物料、仓库与库位、往来客户、供应商的数据管理能力
 * 遵循 docs/specs/10-erp-wms 规范契约，纯粹直连真实后端 REST 接口
 * 绝不捕获异常伪造成功，真实错误抛出至视图呈现 ErrorState
 */

import request, { type ApiResponse } from "../utils/request";
import type { PageQuery, PageResult } from "../types/common";
import type {
  Product,
  ProductQuery,
  Uom,
  Warehouse,
  Location,
  LocationQuery,
  Customer,
  Supplier,
} from "../types/inventory";

/**
 * 分页查询物料列表
 * 接口路径：GET /api/products
 */
export async function getProducts(query: ProductQuery = {}): Promise<ApiResponse<PageResult<Product>>> {
  return await request<PageResult<Product>>({
    url: "/api/products",
    method: "GET",
    params: query,
  });
}

/**
 * 获取物料单条详情
 * 接口路径：GET /api/products/{id}
 */
export async function getProductById(id: string | number): Promise<ApiResponse<Product>> {
  return await request<Product>({
    url: `/api/products/${id}`,
    method: "GET",
  });
}

/**
 * 创建新物料
 * 接口路径：POST /api/products
 */
export async function createProduct(payload: Partial<Product>): Promise<ApiResponse<Product>> {
  return await request<Product>({
    url: "/api/products",
    method: "POST",
    data: payload,
  });
}

/**
 * 更新物料属性
 * 接口路径：PUT /api/products/{id}
 */
export async function updateProduct(id: string | number, payload: Partial<Product>): Promise<ApiResponse<Product>> {
  return await request<Product>({
    url: `/api/products/${id}`,
    method: "PUT",
    data: payload,
  });
}

/**
 * 分页查询计量单位列表
 * 接口路径：GET /api/uoms
 */
export async function getUoms(query: PageQuery = {}): Promise<ApiResponse<PageResult<Uom>>> {
  return await request<PageResult<Uom>>({
    url: "/api/uoms",
    method: "GET",
    params: query,
  });
}

/**
 * 获取计量单位详情
 * 接口路径：GET /api/uoms/{id}
 */
export async function getUomById(id: string | number): Promise<ApiResponse<Uom>> {
  return await request<Uom>({
    url: `/api/uoms/${id}`,
    method: "GET",
  });
}

/**
 * 创建新计量单位
 * 接口路径：POST /api/uoms
 */
export async function createUom(payload: Partial<Uom>): Promise<ApiResponse<Uom>> {
  return await request<Uom>({
    url: "/api/uoms",
    method: "POST",
    data: payload,
  });
}

/**
 * 更新计量单位
 * 接口路径：PUT /api/uoms/{id}
 */
export async function updateUom(id: string | number, payload: Partial<Uom>): Promise<ApiResponse<Uom>> {
  return await request<Uom>({
    url: `/api/uoms/${id}`,
    method: "PUT",
    data: payload,
  });
}

/**
 * 变更计量单位启用状态
 * 接口路径：PATCH /api/uoms/{id}/status
 */
export async function changeUomStatus(id: string | number, status: "ACTIVE" | "INACTIVE"): Promise<ApiResponse<Uom>> {
  return await request<Uom>({
    url: `/api/uoms/${id}/status`,
    method: "PATCH",
    data: { status },
  });
}

/**
 * 逻辑删除计量单位
 * 接口路径：DELETE /api/uoms/{id}
 */
export async function deleteUom(id: string | number): Promise<ApiResponse<void>> {
  return await request<void>({
    url: `/api/uoms/${id}`,
    method: "DELETE",
  });
}

/**
 * 分页或全量查询仓库列表
 * 接口路径：GET /api/warehouses
 */
export async function getWarehouses(query: PageQuery = {}): Promise<ApiResponse<PageResult<Warehouse>>> {
  return await request<PageResult<Warehouse>>({
    url: "/api/warehouses",
    method: "GET",
    params: query,
  });
}

/**
 * 查询单个仓库详情
 * 接口路径：GET /api/warehouses/{id}
 */
export async function getWarehouseById(id: string | number): Promise<ApiResponse<Warehouse>> {
  return await request<Warehouse>({
    url: `/api/warehouses/${id}`,
    method: "GET",
  });
}

/**
 * 创建仓库
 * 接口路径：POST /api/warehouses
 */
export async function createWarehouse(payload: Partial<Warehouse>): Promise<ApiResponse<Warehouse>> {
  return await request<Warehouse>({
    url: "/api/warehouses",
    method: "POST",
    data: payload,
  });
}

/**
 * 更新仓库属性
 * 接口路径：PUT /api/warehouses/{id}
 */
export async function updateWarehouse(id: string | number, payload: Partial<Warehouse>): Promise<ApiResponse<Warehouse>> {
  return await request<Warehouse>({
    url: `/api/warehouses/${id}`,
    method: "PUT",
    data: payload,
  });
}

/**
 * 变更仓库启用状态
 * 接口路径：PATCH /api/warehouses/{id}/status
 */
export async function changeWarehouseStatus(id: string | number, status: "ACTIVE" | "INACTIVE"): Promise<ApiResponse<Warehouse>> {
  return await request<Warehouse>({
    url: `/api/warehouses/${id}/status`,
    method: "PATCH",
    data: { status },
  });
}

/**
 * 逻辑删除仓库
 * 接口路径：DELETE /api/warehouses/{id}
 */
export async function deleteWarehouse(id: string | number): Promise<ApiResponse<void>> {
  return await request<void>({
    url: `/api/warehouses/${id}`,
    method: "DELETE",
  });
}

/**
 * 分页查询库位列表
 * 接口路径：GET /api/locations
 */
export async function getLocations(query: LocationQuery = {}): Promise<ApiResponse<PageResult<Location>>> {
  return await request<PageResult<Location>>({
    url: "/api/locations",
    method: "GET",
    params: query,
  });
}

/**
 * 获取单个库位详情
 * 接口路径：GET /api/locations/{id}
 */
export async function getLocationById(id: string | number): Promise<ApiResponse<Location>> {
  return await request<Location>({
    url: `/api/locations/${id}`,
    method: "GET",
  });
}

/**
 * 创建库位
 * 接口路径：POST /api/locations
 */
export async function createLocation(payload: Partial<Location>): Promise<ApiResponse<Location>> {
  return await request<Location>({
    url: "/api/locations",
    method: "POST",
    data: payload,
  });
}

/**
 * 更新库位
 * 接口路径：PUT /api/locations/{id}
 */
export async function updateLocation(id: string | number, payload: Partial<Location>): Promise<ApiResponse<Location>> {
  return await request<Location>({
    url: `/api/locations/${id}`,
    method: "PUT",
    data: payload,
  });
}

/**
 * 变更库位状态
 * 接口路径：PATCH /api/locations/{id}/status
 */
export async function changeLocationStatus(id: string | number, status: "ACTIVE" | "INACTIVE"): Promise<ApiResponse<Location>> {
  return await request<Location>({
    url: `/api/locations/${id}/status`,
    method: "PATCH",
    data: { status },
  });
}

/**
 * 逻辑删除库位
 * 接口路径：DELETE /api/locations/{id}
 */
export async function deleteLocation(id: string | number): Promise<ApiResponse<void>> {
  return await request<void>({
    url: `/api/locations/${id}`,
    method: "DELETE",
  });
}

/**
 * 分页查询客户列表
 * 接口路径：GET /api/customers
 */
export async function getCustomers(query: PageQuery = {}): Promise<ApiResponse<PageResult<Customer>>> {
  return await request<PageResult<Customer>>({
    url: "/api/customers",
    method: "GET",
    params: query,
  });
}

/**
 * 获取客户详情
 * 接口路径：GET /api/customers/{id}
 */
export async function getCustomerById(id: string | number): Promise<ApiResponse<Customer>> {
  return await request<Customer>({
    url: `/api/customers/${id}`,
    method: "GET",
  });
}

/**
 * 创建往来客户
 * 接口路径：POST /api/customers
 */
export async function createCustomer(payload: Partial<Customer>): Promise<ApiResponse<Customer>> {
  return await request<Customer>({
    url: "/api/customers",
    method: "POST",
    data: payload,
  });
}

/**
 * 更新往来客户
 * 接口路径：PUT /api/customers/{id}
 */
export async function updateCustomer(id: string | number, payload: Partial<Customer>): Promise<ApiResponse<Customer>> {
  return await request<Customer>({
    url: `/api/customers/${id}`,
    method: "PUT",
    data: payload,
  });
}

/**
 * 变更客户状态
 * 接口路径：PATCH /api/customers/{id}/status
 */
export async function changeCustomerStatus(id: string | number, status: "ACTIVE" | "INACTIVE"): Promise<ApiResponse<Customer>> {
  return await request<Customer>({
    url: `/api/customers/${id}/status`,
    method: "PATCH",
    data: { status },
  });
}

/**
 * 逻辑删除客户
 * 接口路径：DELETE /api/customers/{id}
 */
export async function deleteCustomer(id: string | number): Promise<ApiResponse<void>> {
  return await request<void>({
    url: `/api/customers/${id}`,
    method: "DELETE",
  });
}

/**
 * 分页查询供应商列表
 * 接口路径：GET /api/suppliers
 */
export async function getSuppliers(query: PageQuery = {}): Promise<ApiResponse<PageResult<Supplier>>> {
  return await request<PageResult<Supplier>>({
    url: "/api/suppliers",
    method: "GET",
    params: query,
  });
}

/**
 * 获取供应商详情
 * 接口路径：GET /api/suppliers/{id}
 */
export async function getSupplierById(id: string | number): Promise<ApiResponse<Supplier>> {
  return await request<Supplier>({
    url: `/api/suppliers/${id}`,
    method: "GET",
  });
}

/**
 * 创建供应商
 * 接口路径：POST /api/suppliers
 */
export async function createSupplier(payload: Partial<Supplier>): Promise<ApiResponse<Supplier>> {
  return await request<Supplier>({
    url: "/api/suppliers",
    method: "POST",
    data: payload,
  });
}

/**
 * 更新供应商
 * 接口路径：PUT /api/suppliers/{id}
 */
export async function updateSupplier(id: string | number, payload: Partial<Supplier>): Promise<ApiResponse<Supplier>> {
  return await request<Supplier>({
    url: `/api/suppliers/${id}`,
    method: "PUT",
    data: payload,
  });
}

/**
 * 变更供应商状态
 * 接口路径：PATCH /api/suppliers/{id}/status
 */
export async function changeSupplierStatus(id: string | number, status: "ACTIVE" | "INACTIVE"): Promise<ApiResponse<Supplier>> {
  return await request<Supplier>({
    url: `/api/suppliers/${id}/status`,
    method: "PATCH",
    data: { status },
  });
}

/**
 * 逻辑删除供应商
 * 接口路径：DELETE /api/suppliers/{id}
 */
export async function deleteSupplier(id: string | number): Promise<ApiResponse<void>> {
  return await request<void>({
    url: `/api/suppliers/${id}`,
    method: "DELETE",
  });
}
