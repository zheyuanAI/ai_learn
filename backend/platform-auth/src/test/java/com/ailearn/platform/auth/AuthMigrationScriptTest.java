package com.ailearn.platform.auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auth 数据库迁移脚本静态校验。
 */
class AuthMigrationScriptTest {

    private static final Pattern UUID_LITERAL = Pattern.compile("'([^']+)'::uuid");
    private static final Pattern BCRYPT_LITERAL =
            Pattern.compile("'(\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53})'");
    private static final String LEGACY_DEMO_PASSWORD_HASH =
            "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";
    private static final String FIXED_DEMO_PASSWORD_HASH =
            "$2a$10$ojmRH0hpaeHIKF5u8/0ZCOjtRHCpfJTonxo7AmJ7sJav6pqeTZ0/2";

    /**
     * 校验 V2 种子脚本中的 UUID 字面量均为 PostgreSQL 可接受的 UUID。
     * 入参：无；出参：无；流程：读取迁移资源，提取显式 UUID 转换并逐个交给 JDK UUID 解析器校验。
     *
     * @throws IOException 读取迁移资源失败时抛出
     */
    @Test
    @DisplayName("V2 种子数据中的 UUID 字面量必须合法")
    void shouldContainOnlyValidUuidLiterals() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/auth/V2__seed_auth_demo_data.sql")) {
            assertNotNull(input, "V2 迁移脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = UUID_LITERAL.matcher(sql);
            int literalCount = 0;

            while (matcher.find()) {
                String literal = matcher.group(1);
                assertDoesNotThrow(() -> UUID.fromString(literal), "非法 UUID: " + literal);
                literalCount++;
            }

            assertTrue(literalCount > 0, "V2 迁移脚本至少应包含一个 UUID 字面量");
        }
    }

    /**
     * 校验 V3 修复迁移提供的 BCrypt 密文确实对应文档约定的 123456。
     * 入参：无；出参：无；流程：读取 V3 迁移资源，确认只针对历史错误密文进行修复，且目标密文匹配明文。
     *
     * @throws IOException 读取迁移资源失败时抛出
     */
    @Test
    @DisplayName("V3 修复后的演示账号密码哈希必须匹配 123456")
    void shouldUseValidDemoPasswordHash() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/auth/V3__repair_demo_password_hash.sql")) {
            assertNotNull(input, "V3 迁移脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = BCRYPT_LITERAL.matcher(sql);
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

            assertTrue(sql.contains(LEGACY_DEMO_PASSWORD_HASH), "V3 必须定位历史错误密文");
            assertTrue(sql.contains(FIXED_DEMO_PASSWORD_HASH), "V3 必须提供修复后的密文");
            assertTrue(passwordEncoder.matches("123456", FIXED_DEMO_PASSWORD_HASH),
                    "V3 修复后的演示账号密码哈希不匹配 123456");
            assertTrue(matcher.find(), "V3 迁移脚本至少应包含一个 BCrypt 密文");
        }
    }

    /**
     * 校验 V5 菜单多租户隔离迁移脚本中的 UUID 字面量均为有效 UUID。
     * 入参：无；出参：无；流程：读取 V5 迁移脚本，提取显式 UUID 转换并逐个交给 JDK UUID 解析器校验。
     *
     * @throws IOException 读取迁移资源失败时抛出
     */
    @Test
    @DisplayName("V5 迁移脚本中的 UUID 字面量必须合法")
    void shouldContainOnlyValidUuidLiteralsInV5() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/auth/V5__menu_tenant_isolation_and_visible_status.sql")) {
            assertNotNull(input, "V5 迁移脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = UUID_LITERAL.matcher(sql);
            int literalCount = 0;

            while (matcher.find()) {
                String literal = matcher.group(1);
                assertDoesNotThrow(() -> UUID.fromString(literal), "非法 UUID: " + literal);
                literalCount++;
            }

            assertTrue(literalCount > 0, "V5 迁移脚本至少应包含一个 UUID 字面量");
        }
    }

}
