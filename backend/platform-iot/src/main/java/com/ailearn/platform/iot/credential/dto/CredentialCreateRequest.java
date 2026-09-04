package com.ailearn.platform.iot.credential.dto;

/** 凭证创建请求；一期不接受客户端自带 secret，平台始终随机生成。 */
public record CredentialCreateRequest() {
}
