package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.domain.entity.Location;
import com.ailearn.platform.core.masterdata.domain.enumtype.LocationType;
import com.ailearn.platform.core.masterdata.domain.port.LocationUsagePort;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.masterdata.domain.port.WarehouseReferencePort;
import com.ailearn.platform.core.masterdata.domain.service.MasterDataValidator;
import com.ailearn.platform.core.masterdata.dto.LocationSaveRequest;
import com.ailearn.platform.core.masterdata.dto.LocationView;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.core.masterdata.domain.model.LocationUsageSnapshot;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 库位应用服务实现。
 */
@Service
public class LocationApplicationServiceImpl
        extends AbstractMasterDataApplicationService<Location, LocationSaveRequest, LocationView>
        implements LocationApplicationService {

    private final LocationUsagePort locationUsagePort;
    private final WarehouseReferencePort warehouseReferencePort;

    @Autowired
    public LocationApplicationServiceImpl(MasterDataRepository<Location> repository,
                                           LocationUsagePort locationUsagePort,
                                           WarehouseReferencePort warehouseReferencePort) {
        this(repository, locationUsagePort, warehouseReferencePort, null, null);
    }

    public LocationApplicationServiceImpl(MasterDataRepository<Location> repository,
                                           LocationUsagePort locationUsagePort,
                                           WarehouseReferencePort warehouseReferencePort,
                                           IdempotencyStorage storage,
                                           ObjectMapper objectMapper) {
        super(repository, storage == null ? new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage() : storage,
                objectMapper == null ? new ObjectMapper() : objectMapper);
        this.locationUsagePort = locationUsagePort;
        this.warehouseReferencePort = warehouseReferencePort;
    }

    /**
     * 分页查询当前租户库位。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    @Override
    @PreAuthorize("hasAuthority('inv:location:view')")
    public MasterDataPageResult<LocationView> page(MasterDataPageQuery query) {
        return super.page(query);
    }

    /**
     * 查询当前租户库位详情。
     *
     * @param id 库位 ID
     * @return 库位详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:location:view')")
    public LocationView detail(UUID id) {
        return super.detail(id);
    }

    /**
     * 创建库位并严格校验标准类型。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:location:manage')")
    public LocationView create(LocationSaveRequest request) {
        return super.create(request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:location:manage')")
    public LocationView create(LocationSaveRequest request, String idempotencyKey) {
        return super.create(request, idempotencyKey);
    }

    /**
     * 修改库位领域字段。
     *
     * @param id 库位 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:location:manage')")
    public LocationView update(UUID id, LocationSaveRequest request) {
        return super.update(id, request);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:location:manage')")
    public LocationView update(UUID id, LocationSaveRequest request, String idempotencyKey) {
        return super.update(id, request, idempotencyKey);
    }

    /**
     * 启用或停用库位；停用时先检查实物和有效预留均为零。
     *
     * @param id 库位 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    @Override
    @PreAuthorize("hasAuthority('inv:location:manage')")
    public LocationView changeStatus(UUID id, StatusChangeRequest request) {
        return super.changeStatus(id, request == null ? null : request.getStatus());
    }

    @Override
    @PreAuthorize("hasAuthority('inv:location:manage')")
    public LocationView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey) {
        return super.changeStatus(id, request == null ? null : request.getStatus(), idempotencyKey);
    }

    /**
     * 逻辑删除库位。
     *
     * @param id 库位 ID
     */
    @Override
    @PreAuthorize("hasAuthority('inv:location:manage')")
    public void delete(UUID id) {
        super.delete(id);
    }

    @Override
    @PreAuthorize("hasAuthority('inv:location:manage')")
    public void delete(UUID id, String idempotencyKey) {
        super.delete(id, idempotencyKey);
    }

    /**
     * 构造库位实体并转换容量字符串。
     *
     * @param request 创建请求
     * @return 已映射领域字段的实体
     */
    @Override
    protected Location newEntity(LocationSaveRequest request) {
        Location entity = new Location();
        entity.setWarehouseId(requireActiveWarehouse(request));
        entity.setCode(MasterDataValidator.requireCode("code", request.getCode()));
        entity.setName(MasterDataValidator.requireName("name", request.getName()));
        entity.setType(LocationType.require(request.getType()).name());
        entity.setCapacity(MasterDataValidator.optionalNonNegativeDecimal("capacity", request.getCapacity()));
        entity.setDescription(MasterDataValidator.optionalText("description", request.getDescription(), 256));
        return entity;
    }

    @Override
    protected String resourceKey() {
        return "location";
    }

    @Override
    protected Class<LocationView> viewType() {
        return LocationView.class;
    }

    /**
     * 应用库位修改字段，并在提交的 type 不为空时重新严格校验。
     *
     * @param entity 当前实体
     * @param request 修改请求
     */
    @Override
    protected void applyFields(Location entity, LocationSaveRequest request) {
        if (request.getWarehouseId() != null) {
            entity.setWarehouseId(requireActiveWarehouse(request));
        }
        if (request.getCode() != null && !request.getCode().isBlank()) {
            entity.setCode(MasterDataValidator.requireCode("code", request.getCode()));
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(MasterDataValidator.requireName("name", request.getName()));
        }
        if (request.getType() != null && !request.getType().isBlank()) {
            entity.setType(LocationType.require(request.getType()).name());
        }
        if (request.getCapacity() != null) {
            entity.setCapacity(MasterDataValidator.optionalNonNegativeDecimal("capacity", request.getCapacity()));
        }
        if (request.getDescription() != null) {
            entity.setDescription(MasterDataValidator.optionalText("description", request.getDescription(), 256));
        }
        if (request.getRemark() != null) {
            entity.setRemark(request.getRemark().trim());
        }
    }

    /**
     * 校验库位专有字段，包括所属仓库、标准库位类型和容量。
     *
     * @param request 请求对象
     * @param creating 是否为创建操作
     */
    @Override
    protected void validatePayload(LocationSaveRequest request, boolean creating) {
        if (request.getWarehouseId() == null && creating) {
            throw MasterDataValidator.validation("warehouseId", "不能为空");
        }
        if (request.getName() != null || creating) {
            MasterDataValidator.requireName("name", request.getName());
        }
        if (request.getType() != null || creating) {
            LocationType.require(request.getType());
        }
        MasterDataValidator.optionalNonNegativeDecimal("capacity", request.getCapacity());
        MasterDataValidator.optionalText("description", request.getDescription(), 256);
    }

    /**
     * 读取创建请求中的所属仓库。
     *
     * @param request 创建请求
     * @return 仓库 ID
     */
    private UUID requireWarehouseId(LocationSaveRequest request) {
        if (request.getWarehouseId() == null) {
            throw MasterDataValidator.validation("warehouseId", "不能为空");
        }
        return request.getWarehouseId();
    }

    /**
     * 校验库位所属仓库属于当前租户且仍处于启用状态。
     *
     * @param request 包含仓库 ID 的库位请求
     * @return 已校验的仓库 ID
     */
    private UUID requireActiveWarehouse(LocationSaveRequest request) {
        UUID warehouseId = requireWarehouseId(request);
        UUID tenantId = TenantContextHolder.requireTenantId();
        if (!warehouseReferencePort.isActiveInTenant(tenantId, warehouseId)) {
            throw new NotFoundException("所属仓库不存在或已停用");
        }
        return warehouseId;
    }

    /**
     * 在库位从启用变为停用前检查实物和有效预留。
     *
     * @param entity 当前库位
     * @param targetStatus 目标状态
     * @param actor 可信操作人
     */
    @Override
    protected void beforeStatusChange(Location entity, String targetStatus, Actor actor) {
        if (!"INACTIVE".equals(targetStatus)
                || !"ACTIVE".equalsIgnoreCase(entity.getStatus())) {
            return;
        }
        LocationUsageSnapshot snapshot = locationUsagePort.getUsage(actor.tenantId(), entity.getId());
        if (snapshot == null) {
            throw new com.ailearn.platform.shared.exception.ServiceUnavailableException(
                    "库位库存使用量查询返回空结果，禁止停用库位");
        }
        LocationUsageSnapshot usage = snapshot.normalized();
        BigDecimal onHand = usage.onHandQty();
        BigDecimal reserved = usage.reservedQty();
        if (onHand.signum() < 0 || reserved.signum() < 0 || reserved.compareTo(onHand) > 0) {
            throw new ConflictException("库位库存使用量不合法，禁止停用");
        }
        if (onHand.signum() != 0 || reserved.signum() != 0) {
            throw new ConflictException("库位仍有实物或有效预留，清空后才能停用");
        }
    }

    /**
     * 获取库位编码。
     *
     * @param request 请求对象
     * @return 库位编码
     */
    @Override
    protected String codeOf(LocationSaveRequest request) {
        return request.getCode();
    }

    /**
     * 返回库位编码字段名。
     *
     * @return code
     */
    @Override
    protected String codeField() {
        return "code";
    }

    /**
     * 将库位实体映射为响应视图。
     *
     * @param entity 库位实体
     * @return 库位视图
     */
    @Override
    protected LocationView toView(Location entity) {
        LocationView view = MasterDataViewSupport.copyBase(entity, new LocationView(), entity.getCode(), entity.getName());
        view.setWarehouseId(entity.getWarehouseId());
        view.setWarehouseName(entity.getWarehouseName());
        view.setType(entity.getType());
        view.setCapacity(MasterDataViewSupport.decimalToString(entity.getCapacity()));
        view.setDescription(entity.getDescription());
        view.setRemark(entity.getRemark());
        return view;
    }

    /**
     * 返回库位资源名称。
     *
     * @return 库位
     */
    @Override
    protected String resourceName() {
        return "库位";
    }
}
