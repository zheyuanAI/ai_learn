package com.ailearn.platform.core.purchasing.putaway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.purchasing.putaway.infrastructure.PostgresPutawayTaskRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** 验证上架列表的大页码偏移只绑定长整型参数，不连接开发数据库。 */
class PostgresPutawayTaskPaginationTest {

    /** 无状态过滤时乘积超过 int 上限不得回绕为第一页。 */
    @Test
    void hugePageWithoutStatusKeepsLongOffset() throws Exception {
        assertOffset(null, 4, 3, 4_294_967_296L);
    }

    /** 有状态过滤时乘积超过 int 上限不得变成负数。 */
    @Test
    void hugePageWithStatusKeepsLongOffset() throws Exception {
        assertOffset("Pending", 2, 4, 2_147_483_648L);
    }

    /** 用 JDBC 替身捕获 SQL 参数；入参为状态、页大小和期望偏移，出参为空。 */
    private void assertOffset(String status, int size, int offsetParameter, long expected) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        new PostgresPutawayTaskRepository(dataSource)
                .findPage(UUID.randomUUID(), status, 1_073_741_825, size);

        Object offset = mockingDetails(statement).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().startsWith("set")
                        && invocation.getArguments().length >= 2
                        && Integer.valueOf(offsetParameter).equals(invocation.getArguments()[0]))
                .map(invocation -> invocation.getArguments()[1])
                .findFirst().orElseThrow(() -> new AssertionError("未绑定上架分页偏移"));
        assertInstanceOf(Long.class, offset);
        assertEquals(expected, offset);
    }
}
