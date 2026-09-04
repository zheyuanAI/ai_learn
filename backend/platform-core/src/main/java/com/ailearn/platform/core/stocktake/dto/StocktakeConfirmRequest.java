package com.ailearn.platform.core.stocktake.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 盘点确认请求。
 */
public class StocktakeConfirmRequest {

    private List<StocktakeCountLineRequest> lines = new ArrayList<>();

    public List<StocktakeCountLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<StocktakeCountLineRequest> lines) {
        this.lines = lines == null ? new ArrayList<>() : lines;
    }
}
