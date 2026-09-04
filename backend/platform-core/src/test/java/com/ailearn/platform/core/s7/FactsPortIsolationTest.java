package com.ailearn.platform.core.s7;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止 S7 偷越领域边界直连 Mapper、表名或其他模块基础设施。 */
class FactsPortIsolationTest {
    @Test
    void shouldExposeOnlyFactsPortsAndHaveNoDirectQueryInfrastructure() throws IOException {
        Path root = Path.of("src/main/java/com/ailearn/platform/core");
        List<String> sourceFiles = Files.walk(root)
                .filter(path -> path.toString().contains("\\traceability\\")
                        || path.toString().contains("\\gis\\")
                        || path.toString().contains("\\dashboard\\"))
                .filter(path -> path.toString().endsWith(".java"))
                .map(this::read)
                .map(this::stripComments)
                .toList();
        assertTrue(sourceFiles.stream().anyMatch(source -> source.contains("interface InventoryFactsQuery")));
        assertTrue(sourceFiles.stream().anyMatch(source -> source.contains("interface IotFactsPort")));
        // ObjectMapper 属于接口序列化基础设施，不是领域持久化 Mapper；只拦截真正的持久化注解/基类。
        assertFalse(sourceFiles.stream().anyMatch(source -> source.contains("@Mapper")
                || source.contains("BaseMapper")
                || source.contains("SqlSession")
                || source.contains(".xml")));
        assertFalse(sourceFiles.stream().anyMatch(source -> source.contains("@Select")));
        // 领域实体类型名可以出现 purchase_order/sales_order；这里只禁止 S7 源码直接拼接这些表的 SQL。
        assertFalse(sourceFiles.stream().anyMatch(source -> source.matches(
                "(?is).*\\b(?:from|join|update|delete\\s+from|insert\\s+into)\\s+(?:inv_|sales_|purchase_|iot_)[a-z0-9_]*.*")));
    }

    private String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(path.toString(), exception);
        }
    }

    private String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
