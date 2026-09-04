package com.ailearn.platform.core.masterdata.dto;

/**
 * 主数据写请求的公共字段。
 * <p>
 * 用途：承载六类主数据共用的状态和备注字段；入参由具体资源请求补充编码、名称及领域字段。
 * 出参：无，应用服务读取本对象完成校验和实体映射。
 * 流程：Controller 绑定请求后交给应用服务，应用服务只接受可信上下文中的租户和用户信息。
 * </p>
 */
public abstract class MasterDataSaveRequest {

    private String status;
    private String remark;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
