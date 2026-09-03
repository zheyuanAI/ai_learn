package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.context.UserContext;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * 自定义下游用户安全认证 Token。
 * <p>
 * 用于在微服务接收网关透传的身份 Header 后，注入 Spring Security 上下文。
 * </p>
 */
public class UserAuthenticationToken extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    /**
     * 当前经过认证的用户安全上下文
     */
    private final UserContext principal;

    /**
     * 构造已认证的 UserAuthenticationToken。
     *
     * @param principal   用户上下文实体
     * @param authorities 授予的权限集合
     */
    public UserAuthenticationToken(UserContext principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public UserContext getPrincipal() {
        return this.principal;
    }

    @Override
    public String getName() {
        return principal != null ? principal.getUsername() : "";
    }
}
