package com.ailearn.platform.core.masterdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.masterdata.application.LocationApplicationServiceImpl;
import com.ailearn.platform.core.masterdata.application.ProductApplicationServiceImpl;
import com.ailearn.platform.core.masterdata.domain.entity.Location;
import com.ailearn.platform.core.masterdata.domain.entity.Product;
import com.ailearn.platform.core.masterdata.domain.enumtype.LocationType;
import com.ailearn.platform.core.masterdata.domain.model.LocationUsageSnapshot;
import com.ailearn.platform.core.masterdata.domain.model.MasterDataPage;
import com.ailearn.platform.core.masterdata.domain.port.LocationUsagePort;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.masterdata.domain.port.WarehouseReferencePort;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.masterdata.dto.LocationSaveRequest;
import com.ailearn.platform.core.masterdata.dto.LocationView;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.ProductSaveRequest;
import com.ailearn.platform.core.masterdata.dto.ProductView;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.ForbiddenException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ValidationException;
import com.ailearn.platform.core.inventory.exception.InventoryException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Task 6 主数据应用服务纯单元测试。
 * <p>
 * 测试不连接数据库或 Redis，通过 Repository/LocationUsagePort mock 验证应用层边界与领域规则。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MasterDataApplicationServiceTest {

    private static final UUID TENANT_A = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Mock
    private MasterDataRepository<Product> productRepository;

    @Mock
    private MasterDataRepository<Location> locationRepository;

    @Mock
    private LocationUsagePort locationUsagePort;

    @Mock
    private WarehouseReferencePort warehouseReferencePort;

    private ProductApplicationServiceImpl productService;
    private LocationApplicationServiceImpl locationService;

    /**
     * 为每个测试设置可信租户和用户上下文。
     */
    @BeforeEach
    void setUpContext() {
        TenantContextHolder.setTenantId(TENANT_A);
        RequestContextHolder.getContext().setUserId(USER_ID);
        productService = new ProductApplicationServiceImpl(productRepository);
        locationService = new LocationApplicationServiceImpl(
                locationRepository, locationUsagePort, warehouseReferencePort);
    }

    /**
     * 清理 ThreadLocal，避免测试之间发生租户或用户污染。
     */
    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    /**
     * 创建商品时只使用可信租户，并把字符串数值转换为 BigDecimal。
     */
    @Test
    void createProductUsesTrustedTenantAndStringNumbers() {
        ProductSaveRequest request = new ProductSaveRequest();
        request.setSku(" RM-SERVO-01 ");
        request.setName("伺服组件");
        request.setUom("件");
        request.setUnitPrice("12.340000");
        request.setMinStock("1.25");
        request.setBatchManaged(true);
        when(productRepository.existsByCode(TENANT_A, "RM-SERVO-01", null)).thenReturn(false);

        ProductView result = productService.create(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).insert(captor.capture());
        Product saved = captor.getValue();
        assertEquals(TENANT_A, saved.getTenantId());
        assertEquals(USER_ID, saved.getCreatedBy());
        assertEquals("RM-SERVO-01", saved.getSku());
        assertEquals(new BigDecimal("12.340000"), saved.getUnitPrice());
        assertEquals("12.340000", result.getUnitPrice());
        assertTrue(result.isBatchManaged());
        assertEquals(4, result.getAllowedActions().size());
    }

    /**
     * 同一租户编码重复时拒绝写入，且不调用 Repository 插入。
     */
    @Test
    void duplicateProductSkuWithinTenantIsRejected() {
        ProductSaveRequest request = productRequest("SKU-001");
        when(productRepository.existsByCode(TENANT_A, "SKU-001", null)).thenReturn(true);

        assertThrows(ConflictException.class, () -> productService.create(request));

        verify(productRepository, never()).insert(any(Product.class));
    }

    @Test
    void keyedProductCreateReplaysAndConflictsByServerDigest() {
        ProductSaveRequest request = productRequest("SKU-IDEMP-001");
        when(productRepository.existsByCode(TENANT_A, "SKU-IDEMP-001", null)).thenReturn(false);
        when(productRepository.existsByCode(TENANT_A, "SKU-IDEMP-001", null)).thenReturn(false);

        ProductView first = productService.create(request, "masterdata-product-create-1");
        ProductView replay = productService.create(request, "masterdata-product-create-1");

        assertEquals(first.getId(), replay.getId());
        verify(productRepository).insert(any(Product.class));

        ProductSaveRequest changed = productRequest("SKU-IDEMP-001");
        changed.setName("变更后的载荷");
        assertThrows(InventoryException.class,
                () -> productService.create(changed, "masterdata-product-create-1"));
        verify(productRepository).insert(any(Product.class));
    }

    /**
     * 查询跨租户商品时只按当前可信租户查询，并按不存在处理。
     */
    @Test
    void crossTenantProductIsInvisible() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(TENANT_A, productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> productService.detail(productId));

        verify(productRepository).findById(TENANT_A, productId);
        verify(productRepository, never()).findById(eq(TENANT_B), eq(productId));
    }

    /**
     * 分页查询使用租户范围，并为每一条记录返回后端计算的 allowedActions。
     */
    @Test
    void pageAddsAllowedActionsAndPassesNormalizedQuery() {
        Product product = product("SKU-001", "启用商品", "ACTIVE");
        MasterDataPageQuery query = new MasterDataPageQuery();
        query.setPage(0);
        query.setSize(500);
        when(productRepository.findPage(eq(TENANT_A), any(MasterDataPageQuery.class)))
                .thenReturn(new MasterDataPage<>(List.of(product), 1, 1, 200));

        MasterDataPageResult<ProductView> result = productService.page(query);

        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
        assertEquals(4, result.getRecords().getFirst().getAllowedActions().size());
        AllowedActionVo disable = result.getRecords().getFirst().getAllowedActions().stream()
                .filter(action -> "disable".equals(action.getAction()))
                .findFirst()
                .orElseThrow();
        assertTrue(disable.isEnabled());
    }

    /**
     * 缺失租户上下文时拒绝写入，防止无租户落库。
     */
    @Test
    void writeWithoutTenantIsForbidden() {
        TenantContextHolder.clear();

        assertThrows(ForbiddenException.class, () -> productService.create(productRequest("SKU-001")));

        verify(productRepository, never()).insert(any(Product.class));
    }

    /**
     * 六种标准库位类型均可创建，枚举之外的值必须拒绝。
     */
    @Test
    void locationTypeIsStrictlyLimitedToSixValues() {
        when(warehouseReferencePort.isActiveInTenant(eq(TENANT_A), any(UUID.class))).thenReturn(true);
        when(locationRepository.existsByCode(eq(TENANT_A), any(String.class), isNull())).thenReturn(false);
        for (LocationType type : LocationType.values()) {
            LocationSaveRequest request = locationRequest(type.name(), "LOC-" + type.name());
            LocationView result = locationService.create(request);
            assertEquals(type.name(), result.getType());
        }

        LocationSaveRequest invalid = locationRequest("Quality_Hold", "LOC-INVALID");
        assertThrows(ValidationException.class, () -> locationService.create(invalid));
        verify(locationRepository, org.mockito.Mockito.times(LocationType.values().length))
                .insert(any(Location.class));
    }

    /**
     * 库位存在实物或有效预留时禁止停用，并且不写入状态变化。
     */
    @Test
    void locationCannotBeDisabledWhenItHasStockOrReservation() {
        UUID locationId = UUID.randomUUID();
        Location location = location(locationId, "LOC-001", "ACTIVE");
        when(locationRepository.findById(TENANT_A, locationId)).thenReturn(Optional.of(location));
        when(locationUsagePort.getUsage(TENANT_A, locationId))
                .thenReturn(new LocationUsageSnapshot(new BigDecimal("1"), BigDecimal.ZERO));

        assertThrows(ConflictException.class,
                () -> locationService.changeStatus(locationId, new StatusChangeRequest("INACTIVE")));

        assertEquals("ACTIVE", location.getStatus());
        verify(locationRepository, never()).update(any(Location.class));
    }

    /**
     * 库位实物和有效预留均为零时允许停用，并保留租户范围查询。
     */
    @Test
    void emptyLocationCanBeDisabled() {
        UUID locationId = UUID.randomUUID();
        Location location = location(locationId, "LOC-001", "ACTIVE");
        when(locationRepository.findById(TENANT_A, locationId)).thenReturn(Optional.of(location));
        when(locationUsagePort.getUsage(TENANT_A, locationId))
                .thenReturn(new LocationUsageSnapshot(BigDecimal.ZERO, BigDecimal.ZERO));

        LocationView result = locationService.changeStatus(
                locationId, new StatusChangeRequest("INACTIVE"));

        assertEquals("INACTIVE", result.getStatus());
        verify(locationRepository).update(location);
        assertNotNull(result.getAllowedActions());
        assertFalse(result.getAllowedActions().stream()
                .filter(action -> "disable".equals(action.getAction()))
                .findFirst()
                .orElseThrow()
                .isEnabled());
    }

    /**
     * 被业务事实引用的主数据只能保留历史，逻辑删除请求被拒绝且不物理删除。
     */
    @Test
    void referencedLocationCannotBeDeleted() {
        UUID locationId = UUID.randomUUID();
        Location location = location(locationId, "LOC-001", "ACTIVE");
        when(locationRepository.findById(TENANT_A, locationId)).thenReturn(Optional.of(location));
        when(locationRepository.hasReferences(TENANT_A, locationId)).thenReturn(true);

        assertThrows(ConflictException.class, () -> locationService.delete(locationId));

        verify(locationRepository, never()).update(any(Location.class));
    }

    /**
     * 构造商品测试请求。
     *
     * @param sku 商品编码
     * @return 最小商品请求
     */
    private ProductSaveRequest productRequest(String sku) {
        ProductSaveRequest request = new ProductSaveRequest();
        request.setSku(sku);
        request.setName("测试商品");
        request.setUom("件");
        return request;
    }

    /**
     * 构造商品实体。
     *
     * @param sku 商品编码
     * @param name 商品名称
     * @param status 商品状态
     * @return 商品实体
     */
    private Product product(String sku, String name, String status) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setTenantId(TENANT_A);
        product.setSku(sku);
        product.setName(name);
        product.setUom("件");
        product.setStatus(status);
        product.setIsdel(0);
        return product;
    }

    /**
     * 构造库位测试请求。
     *
     * @param type 库位类型
     * @param code 库位编码
     * @return 最小库位请求
     */
    private LocationSaveRequest locationRequest(String type, String code) {
        LocationSaveRequest request = new LocationSaveRequest();
        request.setWarehouseId(UUID.randomUUID());
        request.setCode(code);
        request.setName("测试库位");
        request.setType(type);
        request.setCapacity("100.000000");
        return request;
    }

    /**
     * 构造库位实体。
     *
     * @param id 库位 ID
     * @param code 库位编码
     * @param status 库位状态
     * @return 库位实体
     */
    private Location location(UUID id, String code, String status) {
        Location location = new Location();
        location.setId(id);
        location.setTenantId(TENANT_A);
        location.setWarehouseId(UUID.randomUUID());
        location.setCode(code);
        location.setName("测试库位");
        location.setType(LocationType.Storage.name());
        location.setStatus(status);
        location.setIsdel(0);
        return location;
    }
}
