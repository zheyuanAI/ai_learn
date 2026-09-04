package com.ailearn.platform.core.masterdata.dto;

/**
 * 客户主数据创建/修改请求。
 *
 * @param customerCode   租户内唯一客户编码
 * @param customerName   客户名称
 * @param contactPerson  联系人
 * @param contactPhone   联系电话
 * @param shippingAddress 送货地址
 */
public class CustomerSaveRequest extends MasterDataSaveRequest {

    private String customerCode;
    private String customerName;
    private String contactPerson;
    private String contactPhone;
    private String shippingAddress;

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
