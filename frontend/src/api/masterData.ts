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
 * 查询仓库列表
 * 接口路径：GET /api/warehouses
 */
export async function getWarehouses(): Promise<ApiResponse<Warehouse[]>> {
  return await request<Warehouse[]>({
    url: "/api/warehouses",
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
