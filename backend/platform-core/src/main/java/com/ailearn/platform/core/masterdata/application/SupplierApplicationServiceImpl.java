package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.domain.entity.Supplier;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.masterdata.domain.service.MasterDataValidator;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.ailearn.platform.core.masterdata.dto.SupplierSaveRequest;
import com.ailearn.platform.core.masterdata.dto.SupplierView;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 供应商应用服务实现。
 */
@Service
public class SupplierApplicationServiceImpl
        extends AbstractMasterDataApplicationService<Supplier, SupplierSaveRequest, SupplierView>
        implements SupplierApplicationService {

    public SupplierApplicationServiceImpl(MasterDataRepository<Supplier> repository) {
        super(repository);
    }

    @Autowired
    public SupplierApplicationServiceImpl(MasterDataRepository<Supplier> repository,
                                          IdempotencyStorage storage, ObjectMapper objectMapper) {
        super(repository, storage, objectMapper);
    }

    /**
     * 分页查询当前租户供应商。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    @Override
    @PreAuthorize("hasAuthority('inv:supplier:view')")
    public MasterDataPageResult<SupplierView> page(MasterDataPageQuery query) {
        return super.page(query);
    }

    /**
     * 查询当前租户供应商详情。
     *
     * @param id 供应商 ID
     * @return 供应商详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:supplier:view')")
    public SupplierView detail(UUID id) {
        return super.detail(id);
    }

    /**
     * 创建供应商。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:supplier:manage')")
    public SupplierView create(SupplierSaveRequest request) {
        return super.create(request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:supplier:manage')")
    public SupplierView create(SupplierSaveRequest request, String idempotencyKey) {
        return super.create(request, idempotencyKey);
    }

    /**
     * 修改供应商。
     *
     * @param id 供应商 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:supplier:manage')")
    public SupplierView update(UUID id, SupplierSaveRequest request) {
        return super.update(id, request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:supplier:manage')")
    public SupplierView update(UUID id, SupplierSaveRequest request, String idempotencyKey) {
        return super.update(id, request, idempotencyKey);
    }

    /**
     * 启用或停用供应商。
     *
     * @param id 供应商 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:supplier:manage')")
    public SupplierView changeStatus(UUID id, StatusChangeRequest request) {
        return super.changeStatus(id, request == null ? null : request.getStatus());
    }

    @Override
    @PreAuthorize("hasAuthority('inv:supplier:manage')")
    public SupplierView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey) {
        return super.changeStatus(id, request == null ? null : request.getStatus(), idempotencyKey);
    }

    /**
     * 逻辑删除供应商。
     *
     * @param id 供应商 ID
     */
    @Override
    @PreAuthorize("hasAuthority('inv:supplier:manage')")
    public void delete(UUID id) {
        super.delete(id);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:supplier:manage')")
    public void delete(UUID id, String idempotencyKey) {
        super.delete(id, idempotencyKey);
    }

    /**
     * 构造供应商实体。
     *
     * @param request 创建请求
     * @return 已映射领域字段的实体
     */
    @Override
    protected Supplier newEntity(SupplierSaveRequest request) {
        Supplier entity = new Supplier();
        entity.setSupplierCode(MasterDataValidator.requireCode("supplierCode", request.getSupplierCode()));
        entity.setSupplierName(MasterDataValidator.requireName("supplierName", request.getSupplierName()));
        entity.setContactPerson(MasterDataValidator.optionalText("contactPerson", request.getContactPerson(), 64));
        entity.setContactPhone(MasterDataValidator.optionalText("contactPhone", request.getContactPhone(), 64));
        entity.setAddress(MasterDataValidator.optionalText("address", request.getAddress(), 256));
        return entity;
    }

    @Override
    protected String resourceKey() {
        return "supplier";
    }

    @Override
    protected Class<SupplierView> viewType() {
        return SupplierView.class;
    }

    /**
     * 应用供应商修改字段。
     *
     * @param entity 当前实体
     * @param request 修改请求
     */
    @Override
    protected void applyFields(Supplier entity, SupplierSaveRequest request) {
        if (request.getSupplierCode() != null && !request.getSupplierCode().isBlank()) {
            entity.setSupplierCode(MasterDataValidator.requireCode("supplierCode", request.getSupplierCode()));
        }
        if (request.getSupplierName() != null && !request.getSupplierName().isBlank()) {
            entity.setSupplierName(MasterDataValidator.requireName("supplierName", request.getSupplierName()));
        }
        if (request.getContactPerson() != null) {
            entity.setContactPerson(MasterDataValidator.optionalText("contactPerson", request.getContactPerson(), 64));
        }
        if (request.getContactPhone() != null) {
            entity.setContactPhone(MasterDataValidator.optionalText("contactPhone", request.getContactPhone(), 64));
        }
        if (request.getAddress() != null) {
            entity.setAddress(MasterDataValidator.optionalText("address", request.getAddress(), 256));
        }
        if (request.getRemark() != null) {
            entity.setRemark(request.getRemark().trim());
        }
    }

    /**
     * 校验供应商专有字段。
     *
     * @param request 请求对象
     * @param creating 是否为创建操作
     */
    @Override
    protected void validatePayload(SupplierSaveRequest request, boolean creating) {
        if (request.getSupplierName() != null || creating) {
            MasterDataValidator.requireName("supplierName", request.getSupplierName());
        }
        MasterDataValidator.optionalText("contactPerson", request.getContactPerson(), 64);
        MasterDataValidator.optionalText("contactPhone", request.getContactPhone(), 64);
        MasterDataValidator.optionalText("address", request.getAddress(), 256);
    }

    /**
     * 获取供应商编码。
     *
     * @param request 请求对象
     * @return 供应商编码
     */
    @Override
    protected String codeOf(SupplierSaveRequest request) {
        return request.getSupplierCode();
    }

    /**
     * 返回供应商编码字段名。
     *
     * @return supplierCode
     */
    @Override
    protected String codeField() {
        return "supplierCode";
    }

    /**
     * 将供应商实体映射为响应视图。
     *
     * @param entity 供应商实体
     * @return 供应商视图
     */
    @Override
    protected SupplierView toView(Supplier entity) {
        SupplierView view = MasterDataViewSupport.copyBase(
                entity, new SupplierView(), entity.getSupplierCode(), entity.getSupplierName());
        view.setSupplierCode(entity.getSupplierCode());
        view.setSupplierName(entity.getSupplierName());
        view.setContactPerson(entity.getContactPerson());
        view.setContactPhone(entity.getContactPhone());
        view.setAddress(entity.getAddress());
        view.setRemark(entity.getRemark());
        return view;
    }

    /**
     * 返回供应商资源名称。
     *
     * @return 供应商
     */
    @Override
    protected String resourceName() {
        return "供应商";
    }
}
