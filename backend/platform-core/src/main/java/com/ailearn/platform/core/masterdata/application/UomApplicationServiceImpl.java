package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.domain.entity.Uom;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.masterdata.domain.service.MasterDataValidator;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.ailearn.platform.core.masterdata.dto.UomSaveRequest;
import com.ailearn.platform.core.masterdata.dto.UomView;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 计量单位应用服务实现。
 */
@Service
public class UomApplicationServiceImpl
        extends AbstractMasterDataApplicationService<Uom, UomSaveRequest, UomView>
        implements UomApplicationService {

    public UomApplicationServiceImpl(MasterDataRepository<Uom> repository) {
        super(repository);
    }

    @Autowired
    public UomApplicationServiceImpl(MasterDataRepository<Uom> repository,
                                     IdempotencyStorage storage, ObjectMapper objectMapper) {
        super(repository, storage, objectMapper);
    }

    /**
     * 分页查询当前租户计量单位。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    @Override
    @PreAuthorize("hasAuthority('inv:uom:view')")
    public MasterDataPageResult<UomView> page(MasterDataPageQuery query) {
        return super.page(query);
    }

    /**
     * 查询当前租户计量单位详情。
     *
     * @param id 计量单位 ID
     * @return 计量单位详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:uom:view')")
    public UomView detail(UUID id) {
        return super.detail(id);
    }

    /**
     * 创建计量单位。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:uom:manage')")
    public UomView create(UomSaveRequest request) {
        return super.create(request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:uom:manage')")
    public UomView create(UomSaveRequest request, String idempotencyKey) {
        return super.create(request, idempotencyKey);
    }

    /**
     * 修改计量单位。
     *
     * @param id 计量单位 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:uom:manage')")
    public UomView update(UUID id, UomSaveRequest request) {
        return super.update(id, request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:uom:manage')")
    public UomView update(UUID id, UomSaveRequest request, String idempotencyKey) {
        return super.update(id, request, idempotencyKey);
    }

    /**
     * 启用或停用计量单位。
     *
     * @param id 计量单位 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:uom:manage')")
    public UomView changeStatus(UUID id, StatusChangeRequest request) {
        return super.changeStatus(id, request == null ? null : request.getStatus());
    }

    @Override
    @PreAuthorize("hasAuthority('inv:uom:manage')")
    public UomView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey) {
        return super.changeStatus(id, request == null ? null : request.getStatus(), idempotencyKey);
    }

    /**
     * 逻辑删除计量单位。
     *
     * @param id 计量单位 ID
     */
    @Override
    @PreAuthorize("hasAuthority('inv:uom:manage')")
    public void delete(UUID id) {
        super.delete(id);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:uom:manage')")
    public void delete(UUID id, String idempotencyKey) {
        super.delete(id, idempotencyKey);
    }

    /**
     * 构造计量单位实体。
     *
     * @param request 创建请求
     * @return 已映射领域字段的实体
     */
    @Override
    protected Uom newEntity(UomSaveRequest request) {
        Uom entity = new Uom();
        entity.setCode(MasterDataValidator.requireCode("code", request.getCode()));
        entity.setName(MasterDataValidator.requireName("name", request.getName()));
        entity.setSymbol(MasterDataValidator.optionalText("symbol", request.getSymbol(), 32));
        entity.setDecimalScale(request.getDecimalScale() == null ? 0
                : MasterDataValidator.optionalInteger("decimalScale", request.getDecimalScale(), 0, 6));
        return entity;
    }

    @Override
    protected String resourceKey() {
        return "uom";
    }

    @Override
    protected Class<UomView> viewType() {
        return UomView.class;
    }

    /**
     * 应用计量单位修改字段。
     *
     * @param entity 当前实体
     * @param request 修改请求
     */
    @Override
    protected void applyFields(Uom entity, UomSaveRequest request) {
        if (request.getCode() != null && !request.getCode().isBlank()) {
            entity.setCode(MasterDataValidator.requireCode("code", request.getCode()));
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(MasterDataValidator.requireName("name", request.getName()));
        }
        if (request.getSymbol() != null) {
            entity.setSymbol(MasterDataValidator.optionalText("symbol", request.getSymbol(), 32));
        }
        if (request.getDecimalScale() != null) {
            entity.setDecimalScale(MasterDataValidator.optionalInteger(
                    "decimalScale", request.getDecimalScale(), 0, 6));
        }
        if (request.getRemark() != null) {
            entity.setRemark(request.getRemark().trim());
        }
    }

    /**
     * 校验计量单位专有字段。
     *
     * @param request 请求对象
     * @param creating 是否为创建操作
     */
    @Override
    protected void validatePayload(UomSaveRequest request, boolean creating) {
        if (request.getName() != null || creating) {
            MasterDataValidator.requireName("name", request.getName());
        }
        MasterDataValidator.optionalText("symbol", request.getSymbol(), 32);
        if (request.getDecimalScale() != null) {
            MasterDataValidator.optionalInteger("decimalScale", request.getDecimalScale(), 0, 6);
        }
    }

    /**
     * 获取计量单位编码。
     *
     * @param request 请求对象
     * @return 编码
     */
    @Override
    protected String codeOf(UomSaveRequest request) {
        return request.getCode();
    }

    /**
     * 返回编码字段名。
     *
     * @return code
     */
    @Override
    protected String codeField() {
        return "code";
    }

    /**
     * 将计量单位实体映射为响应视图。
     *
     * @param entity 计量单位实体
     * @return 计量单位视图
     */
    @Override
    protected UomView toView(Uom entity) {
        UomView view = MasterDataViewSupport.copyBase(entity, new UomView(), entity.getCode(), entity.getName());
        view.setSymbol(entity.getSymbol());
        view.setDecimalScale(entity.getDecimalScale());
        view.setRemark(entity.getRemark());
        return view;
    }

    /**
     * 返回统一资源名称。
     *
     * @return 计量单位
     */
    @Override
    protected String resourceName() {
        return "计量单位";
    }
}
