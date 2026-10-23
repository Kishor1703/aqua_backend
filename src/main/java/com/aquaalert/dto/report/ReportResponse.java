package com.aquaalert.dto.report;

import java.util.List;

public record ReportResponse(
        ReportSummaryResponse summary,
        List<ReportPointResponse> breakdown
) {}
