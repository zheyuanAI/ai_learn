package com.ailearn.platform.core.masterdata.dto;

/**
 * 仓库主数据创建/修改请求。
 *
 * @param code    租户内唯一仓库编码
 * @param name    仓库名称
 * @param type    仓库类型，如 RAW_MATERIAL、FINISHED_GOODS
 * @param manager 负责人
 * @param contact 联系方式
 * @param address 地址
 */
public class WarehouseSaveRequest extends MasterDataSaveRequest {

    private String code;
    private String name;
    private String type;
    private String manager;
    private String contact;
    private String address;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getManager() {
        return manager;
    }

    public void setManager(String manager) {
        this.manager = manager;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
