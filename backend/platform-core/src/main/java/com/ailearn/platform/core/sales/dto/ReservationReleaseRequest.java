package com.ailearn.platform.core.sales.dto;

import java.util.List;

/** 异常释放未拣预留请求。 */
public class ReservationReleaseRequest {
    private List<ReservationReleaseLineRequest> releaseLines;

    public List<ReservationReleaseLineRequest> getReleaseLines() { return releaseLines; }
    public void setReleaseLines(List<ReservationReleaseLineRequest> value) { this.releaseLines = value; }
}
