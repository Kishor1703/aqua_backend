package com.aquaalert.dto.resident;

import java.util.List;

public record CsvImportResult(
        int totalRows,
        int successCount,
        int failureCount,
        List<RowError> errors
) {
    public record RowError(int rowNumber, String reason) {}
}
