package com.ailearn.platform.iot.credential.domain;

/** 设备凭证生命周期。 */
public enum CredentialStatus {
    PendingProvision,
    Active,
    ProvisionFailed,
    Revoked
}
