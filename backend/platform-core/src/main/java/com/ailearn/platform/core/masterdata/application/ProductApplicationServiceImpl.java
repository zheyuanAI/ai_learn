package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.domain.entity.Product;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.masterdata.domain.service.MasterDataValidator;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.ProductSaveRequest;
import com.ailearn.platform.core.masterdata.dto.ProductView;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 商品应用服务实现。
 */
@Service
public class ProductApplicationServiceImpl
        extends AbstractMasterDataApplicationService<Product, ProductSaveRequest, ProductView>
        implements ProductApplicationService {

    public ProductApplicationServiceImpl(MasterDataRepository<Product> repository) {
        super(repository);
    }

    @Autowired
    public ProductApplicationServiceImpl(MasterDataRepository<Product> repository,
                                         IdempotencyStorage storage, ObjectMapper objectMapper) {
        super(repository, storage, objectMapper);
    }

    /**
     * 分页查询当前租户商品。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    @Override
    @PreAuthorize("hasAuthority('inv:product:view')")
    public MasterDataPageResult<ProductView> page(MasterDataPageQuery query) {
        return super.page(query);
    }

    /**
     * 查询当前租户商品详情。
     *
     * @param id 商品 ID
     * @return 商品详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:product:view')")
    public ProductView detail(UUID id) {
        return super.detail(id);
    }

    /**
     * 创建商品。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:product:manage')")
    public ProductView create(ProductSaveRequest request) {
        return super.create(request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:product:manage')")
    public ProductView create(ProductSaveRequest request, String idempotencyKey) {
        return super.create(request, idempotencyKey);
    }

    /**
     * 修改商品。
     *
     * @param id 商品 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:product:manage')")
    public ProductView update(UUID id, ProductSaveRequest request) {
        return super.update(id, request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:product:manage')")
    public ProductView update(UUID id, ProductSaveRequest request, String idempotencyKey) {
        return super.update(id, request, idempotencyKey);
    }

    /**
     * 启用或停用商品。
     *
     * @param id 商品 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:product:manage')")
    public ProductView changeStatus(UUID id, StatusChangeRequest request) {
        return super.changeStatus(id, request == null ? null : request.getStatus());
    }

    @Override
    @PreAuthorize("hasAuthority('inv:product:manage')")
    public ProductView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey) {
        return super.changeStatus(id, request == null ? null : request.getStatus(), idempotencyKey);
    }

    /**
     * 逻辑删除商品。
     *
     * @param id 商品 ID
     */
    @Override
    @PreAuthorize("hasAuthority('inv:product:manage')")
    public void delete(UUID id) {
        super.delete(id);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:product:manage')")
    public void delete(UUID id, String idempotencyKey) {
        super.delete(id, idempotencyKey);
    }

    /**
     * 构造商品实体并转换 HTTP 数值。
     *
     * @param request 创建请求
     * @return 已映射领域字段的实体
     */
    @Override
    protected Product newEntity(ProductSaveRequest request) {
        Product entity = new Product();
        entity.setSku(MasterDataValidator.requireCode("sku", request.getSku()));
        entity.setName(MasterDataValidator.requireName("name", request.getName()));
        entity.setSpec(MasterDataValidator.optionalText("spec", request.getSpec(), 256));
        entity.setUom(MasterDataValidator.requireCode("uom", request.getUom()));
        entity.setCategory(MasterDataValidator.optionalText("category", request.getCategory(), 64));
        entity.setBatchManaged(batchManaged(request));
        entity.setUnitPrice(MasterDataValidator.optionalNonNegativeDecimal("unitPrice", request.getUnitPrice()));
        entity.setMinStock(MasterDataValidator.optionalNonNegativeDecimal("minStock", request.getMinStock()));
        entity.setMaxStock(MasterDataValidator.optionalNonNegativeDecimal("maxStock", request.getMaxStock()));
        entity.setSafetyStock(MasterDataValidator.optionalNonNegativeDecimal("safetyStock", request.getSafetyStock()));
        return entity;
    }

    @Override
    protected String resourceKey() {
        return "product";
    }

    @Override
    protected Class<ProductView> viewType() {
        return ProductView.class;
    }

    /**
     * 应用商品修改字段，未提供的可选字段保持原值。
     *
     * @param entity 当前实体
     * @param request 修改请求
     */
    @Override
    protected void applyFields(Product entity, ProductSaveRequest request) {
        if (request.getSku() != null && !request.getSku().isBlank()) {
            entity.setSku(MasterDataValidator.requireCode("sku", request.getSku()));
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(MasterDataValidator.requireName("name", request.getName()));
        }
        if (request.getSpec() != null) {
            entity.setSpec(MasterDataValidator.optionalText("spec", request.getSpec(), 256));
        }
        if (request.getUom() != null && !request.getUom().isBlank()) {
            entity.setUom(MasterDataValidator.requireCode("uom", request.getUom()));
        }
        if (request.getCategory() != null) {
            entity.setCategory(MasterDataValidator.optionalText("category", request.getCategory(), 64));
        }
        if (batchManagedValue(request) != null) {
            entity.setBatchManaged(batchManagedValue(request));
        }
        if (request.getUnitPrice() != null) {
            entity.setUnitPrice(MasterDataValidator.optionalNonNegativeDecimal("unitPrice", request.getUnitPrice()));
        }
        if (request.getMinStock() != null) {
            entity.setMinStock(MasterDataValidator.optionalNonNegativeDecimal("minStock", request.getMinStock()));
        }
        if (request.getMaxStock() != null) {
            entity.setMaxStock(MasterDataValidator.optionalNonNegativeDecimal("maxStock", request.getMaxStock()));
        }
        if (request.getSafetyStock() != null) {
            entity.setSafetyStock(MasterDataValidator.optionalNonNegativeDecimal("safetyStock", request.getSafetyStock()));
        }
        if (request.getRemark() != null) {
            entity.setRemark(request.getRemark().trim());
        }
    }

    /**
     * 校验商品专有字段。
     *
     * @param request 请求对象
     * @param creating 是否为创建操作
     */
    @Override
    protected void validatePayload(ProductSaveRequest request, boolean creating) {
        if (request.getName() != null || creating) {
            MasterDataValidator.requireName("name", request.getName());
        }
        if (request.getUom() != null || creating) {
            MasterDataValidator.requireCode("uom", request.getUom());
        }
        MasterDataValidator.optionalText("spec", request.getSpec(), 256);
        MasterDataValidator.optionalText("category", request.getCategory(), 64);
        MasterDataValidator.optionalNonNegativeDecimal("unitPrice", request.getUnitPrice());
        MasterDataValidator.optionalNonNegativeDecimal("minStock", request.getMinStock());
        MasterDataValidator.optionalNonNegativeDecimal("maxStock", request.getMaxStock());
        MasterDataValidator.optionalNonNegativeDecimal("safetyStock", request.getSafetyStock());
    }

    /**
     * 获取商品编码。
     *
     * @param request 请求对象
     * @return SKU
     */
    @Override
    protected String codeOf(ProductSaveRequest request) {
        return request.getSku();
    }

    /**
     * 返回 SKU 校验字段名。
     *
     * @return sku
     */
    @Override
    protected String codeField() {
        return "sku";
    }

    /**
     * 将商品实体映射为响应视图并将数值转回字符串。
     *
     * @param entity 商品实体
     * @return 商品视图
     */
    @Override
    protected ProductView toView(Product entity) {
        ProductView view = MasterDataViewSupport.copyBase(entity, new ProductView(), entity.getSku(), entity.getName());
        view.setSku(entity.getSku());
        view.setSpec(entity.getSpec());
        view.setUom(entity.getUom());
        view.setCategory(entity.getCategory());
        boolean batchManaged = Boolean.TRUE.equals(entity.getBatchManaged());
        view.setBatchManaged(batchManaged);
        view.setBatchMgmt(batchManaged);
        view.setUnitPrice(MasterDataViewSupport.decimalToString(entity.getUnitPrice()));
        view.setMinStock(MasterDataViewSupport.decimalToString(entity.getMinStock()));
        view.setMaxStock(MasterDataViewSupport.decimalToString(entity.getMaxStock()));
        view.setSafetyStock(MasterDataViewSupport.decimalToString(entity.getSafetyStock()));
        view.setRemark(entity.getRemark());
        return view;
    }

    /**
     * 返回商品资源名称。
     *
     * @return 商品
     */
    @Override
    protected String resourceName() {
        return "商品";
    }

    private Boolean batchManagedValue(ProductSaveRequest request) {
        return request.getBatchManaged() != null ? request.getBatchManaged() : request.getBatchMgmt();
    }

    private boolean batchManaged(ProductSaveRequest request) {
        return Boolean.TRUE.equals(batchManagedValue(request));
    }
}
