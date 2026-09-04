package com.ailearn.platform.core.masterdata.dto;

/**
 * 主数据状态变更请求。
 *
 * @param status 目标状态，应用服务只接受 ACTIVE/INACTIVE 及兼容别名
 */
public class StatusChangeRequest {

    private String status;

    public StatusChangeRequest() {
    }

    public StatusChangeRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
