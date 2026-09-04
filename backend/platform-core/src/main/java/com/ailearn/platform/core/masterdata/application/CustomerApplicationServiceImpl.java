package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.domain.entity.Customer;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.masterdata.domain.service.MasterDataValidator;
import com.ailearn.platform.core.masterdata.dto.CustomerSaveRequest;
import com.ailearn.platform.core.masterdata.dto.CustomerView;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 客户应用服务实现。
 */
@Service
public class CustomerApplicationServiceImpl
        extends AbstractMasterDataApplicationService<Customer, CustomerSaveRequest, CustomerView>
        implements CustomerApplicationService {

    public CustomerApplicationServiceImpl(MasterDataRepository<Customer> repository) {
        super(repository);
    }

    @Autowired
    public CustomerApplicationServiceImpl(MasterDataRepository<Customer> repository,
                                          IdempotencyStorage storage, ObjectMapper objectMapper) {
        super(repository, storage, objectMapper);
    }

    /**
     * 分页查询当前租户客户。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    @Override
    @PreAuthorize("hasAuthority('inv:customer:view')")
    public MasterDataPageResult<CustomerView> page(MasterDataPageQuery query) {
        return super.page(query);
    }

    /**
     * 查询当前租户客户详情。
     *
     * @param id 客户 ID
     * @return 客户详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:customer:view')")
    public CustomerView detail(UUID id) {
        return super.detail(id);
    }

    /**
     * 创建客户。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:customer:manage')")
    public CustomerView create(CustomerSaveRequest request) {
        return super.create(request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:customer:manage')")
    public CustomerView create(CustomerSaveRequest request, String idempotencyKey) {
        return super.create(request, idempotencyKey);
    }

    /**
     * 修改客户。
     *
     * @param id 客户 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:customer:manage')")
    public CustomerView update(UUID id, CustomerSaveRequest request) {
        return super.update(id, request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:customer:manage')")
    public CustomerView update(UUID id, CustomerSaveRequest request, String idempotencyKey) {
        return super.update(id, request, idempotencyKey);
    }

    /**
     * 启用或停用客户。
     *
     * @param id 客户 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:customer:manage')")
    public CustomerView changeStatus(UUID id, StatusChangeRequest request) {
        return super.changeStatus(id, request == null ? null : request.getStatus());
    }

    @Override
    @PreAuthorize("hasAuthority('inv:customer:manage')")
    public CustomerView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey) {
        return super.changeStatus(id, request == null ? null : request.getStatus(), idempotencyKey);
    }

    /**
     * 逻辑删除客户。
     *
     * @param id 客户 ID
     */
    @Override
    @PreAuthorize("hasAuthority('inv:customer:manage')")
    public void delete(UUID id) {
        super.delete(id);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:customer:manage')")
    public void delete(UUID id, String idempotencyKey) {
        super.delete(id, idempotencyKey);
    }

    /**
     * 构造客户实体。
     *
     * @param request 创建请求
     * @return 已映射领域字段的实体
     */
    @Override
    protected Customer newEntity(CustomerSaveRequest request) {
        Customer entity = new Customer();
        entity.setCustomerCode(MasterDataValidator.requireCode("customerCode", request.getCustomerCode()));
        entity.setCustomerName(MasterDataValidator.requireName("customerName", request.getCustomerName()));
        entity.setContactPerson(MasterDataValidator.optionalText("contactPerson", request.getContactPerson(), 64));
        entity.setContactPhone(MasterDataValidator.optionalText("contactPhone", request.getContactPhone(), 64));
        entity.setShippingAddress(MasterDataValidator.optionalText(
                "shippingAddress", request.getShippingAddress(), 256));
        return entity;
    }

    @Override
    protected String resourceKey() {
        return "customer";
    }

    @Override
    protected Class<CustomerView> viewType() {
        return CustomerView.class;
    }

    /**
     * 应用客户修改字段。
     *
     * @param entity 当前实体
     * @param request 修改请求
     */
    @Override
    protected void applyFields(Customer entity, CustomerSaveRequest request) {
        if (request.getCustomerCode() != null && !request.getCustomerCode().isBlank()) {
            entity.setCustomerCode(MasterDataValidator.requireCode("customerCode", request.getCustomerCode()));
        }
        if (request.getCustomerName() != null && !request.getCustomerName().isBlank()) {
            entity.setCustomerName(MasterDataValidator.requireName("customerName", request.getCustomerName()));
        }
        if (request.getContactPerson() != null) {
            entity.setContactPerson(MasterDataValidator.optionalText("contactPerson", request.getContactPerson(), 64));
        }
        if (request.getContactPhone() != null) {
            entity.setContactPhone(MasterDataValidator.optionalText("contactPhone", request.getContactPhone(), 64));
        }
        if (request.getShippingAddress() != null) {
            entity.setShippingAddress(MasterDataValidator.optionalText(
                    "shippingAddress", request.getShippingAddress(), 256));
        }
        if (request.getRemark() != null) {
            entity.setRemark(request.getRemark().trim());
        }
    }

    /**
     * 校验客户专有字段。
     *
     * @param request 请求对象
     * @param creating 是否为创建操作
     */
    @Override
    protected void validatePayload(CustomerSaveRequest request, boolean creating) {
        if (request.getCustomerName() != null || creating) {
            MasterDataValidator.requireName("customerName", request.getCustomerName());
        }
        MasterDataValidator.optionalText("contactPerson", request.getContactPerson(), 64);
        MasterDataValidator.optionalText("contactPhone", request.getContactPhone(), 64);
        MasterDataValidator.optionalText("shippingAddress", request.getShippingAddress(), 256);
    }

    /**
     * 获取客户编码。
     *
     * @param request 请求对象
     * @return 客户编码
     */
    @Override
    protected String codeOf(CustomerSaveRequest request) {
        return request.getCustomerCode();
    }

    /**
     * 返回客户编码字段名。
     *
     * @return customerCode
     */
    @Override
    protected String codeField() {
        return "customerCode";
    }

    /**
     * 将客户实体映射为响应视图。
     *
     * @param entity 客户实体
     * @return 客户视图
     */
    @Override
    protected CustomerView toView(Customer entity) {
        CustomerView view = MasterDataViewSupport.copyBase(
                entity, new CustomerView(), entity.getCustomerCode(), entity.getCustomerName());
        view.setCustomerCode(entity.getCustomerCode());
        view.setCustomerName(entity.getCustomerName());
        view.setContactPerson(entity.getContactPerson());
        view.setContactPhone(entity.getContactPhone());
        view.setShippingAddress(entity.getShippingAddress());
        view.setRemark(entity.getRemark());
        return view;
    }

    /**
     * 返回客户资源名称。
     *
     * @return 客户
     */
    @Override
    protected String resourceName() {
        return "客户";
    }
}
