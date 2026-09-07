package com.ailearn.platform.core.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.stocktake.application.StocktakeApplicationService;
import com.ailearn.platform.core.stocktake.controller.StocktakeController;
import com.ailearn.platform.core.stocktake.dto.StocktakePageQuery;
import com.ailearn.platform.core.stocktake.dto.StocktakeView;
import com.ailearn.platform.core.transfer.application.TransferApplicationService;
import com.ailearn.platform.core.transfer.controller.TransferController;
import com.ailearn.platform.core.transfer.dto.TransferPageQuery;
import com.ailearn.platform.core.transfer.dto.TransferView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/** S2 调拨和盘点只读 REST 契约测试。 */
@ExtendWith(MockitoExtension.class)
class S2ReadControllerContractTest {

    private static final UUID TRANSFER_ID = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID STOCKTAKE_ID = UUID.fromString("a2000000-0000-0000-0000-000000000002");

    @Mock
    private TransferApplicationService transferService;

    @Mock
    private StocktakeApplicationService stocktakeService;

    @Captor
    private ArgumentCaptor<TransferPageQuery> transferQueryCaptor;

    @Captor
    private ArgumentCaptor<StocktakePageQuery> stocktakeQueryCaptor;

    private MockMvc transferMvc;
    private MockMvc stocktakeMvc;

    /**
     * 准备两个独立的 MVC 入口。
     * 入参：无；出参：无；流程：仅装配只读 Controller 和 JSON 转换器，不连接数据库。
     */
    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);
        transferMvc = MockMvcBuilders.standaloneSetup(new TransferController(transferService))
                .setMessageConverters(converter)
                .build();
        stocktakeMvc = MockMvcBuilders.standaloneSetup(new StocktakeController(stocktakeService))
                .setMessageConverters(converter)
                .build();
    }

    /**
     * 验证调拨分页使用 page/size 查询参数和 records/totalPages 响应结构。
     * 入参：page=2、size=5；出参：200 分页响应；流程：Controller 绑定查询对象后透传应用端口。
     */
    @Test
    void transferPageBindsPaginationContract() throws Exception {
        when(transferService.page(any(TransferPageQuery.class)))
                .thenReturn(new MasterDataPageResult<>(List.<TransferView>of(), 0, 2, 5));

        transferMvc.perform(get("/api/transfers").queryParam("page", "2").queryParam("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.totalPages").value(0));

        verify(transferService).page(transferQueryCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(2, transferQueryCaptor.getValue().getPage());
        org.junit.jupiter.api.Assertions.assertEquals(5, transferQueryCaptor.getValue().getSize());
    }

    /**
     * 验证调拨详情按 path UUID 调用当前租户应用端口。
     * 入参：调拨单 ID；出参：200 且 data 为空；流程：Controller 不猜测跨租户资源。
     */
    @Test
    void transferDetailDelegatesById() throws Exception {
        when(transferService.find(TRANSFER_ID)).thenReturn(null);

        transferMvc.perform(get("/api/transfers/{id}", TRANSFER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(transferService).find(TRANSFER_ID);
    }

    /**
     * 验证盘点分页同样输出统一分页结构，并保留显式 size。
     * 入参：page=3、size=7；出参：200 分页响应；流程：Controller 绑定查询对象后透传应用端口。
     */
    @Test
    void stocktakePageBindsPaginationContract() throws Exception {
        when(stocktakeService.page(any(StocktakePageQuery.class)))
                .thenReturn(new MasterDataPageResult<>(List.<StocktakeView>of(), 0, 3, 7));

        stocktakeMvc.perform(get("/api/stocktakes").queryParam("page", "3").queryParam("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.totalPages").value(0));

        verify(stocktakeService).page(stocktakeQueryCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(3, stocktakeQueryCaptor.getValue().getPage());
        org.junit.jupiter.api.Assertions.assertEquals(7, stocktakeQueryCaptor.getValue().getSize());
    }

    /**
     * 验证盘点详情使用同一 /api/stocktakes/{id} 资源路径。
     * 入参：盘点单 ID；出参：200 且 data 为空；流程：Controller 仅透传应用端口查询。
     */
    @Test
    void stocktakeDetailDelegatesById() throws Exception {
        when(stocktakeService.find(STOCKTAKE_ID)).thenReturn(null);

        stocktakeMvc.perform(get("/api/stocktakes/{id}", STOCKTAKE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(stocktakeService).find(STOCKTAKE_ID);
    }
}
