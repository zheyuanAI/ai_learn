package com.ailearn.platform.core.masterdata.dto;

/**
 * 供应商主数据创建/修改请求。
 *
 * @param supplierCode  供应商编码
 * @param supplierName  供应商名称
 * @param contactPerson 联系人
 * @param contactPhone  联系电话
 * @param address       经营地址
 */
public class SupplierSaveRequest extends MasterDataSaveRequest {

    private String supplierCode;
    private String supplierName;
    private String contactPerson;
    private String contactPhone;
    private String address;

    public String getSupplierCode() {
        return supplierCode;
    }

    public void setSupplierCode(String supplierCode) {
        this.supplierCode = supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
