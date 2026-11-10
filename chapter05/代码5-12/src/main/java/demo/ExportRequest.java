package demo;

import java.util.List;
import java.util.Objects;

record ExportRequest(String storeId, List<String> allowedFields) {
    ExportRequest {
        Objects.requireNonNull(storeId);
        allowedFields = List.copyOf(allowedFields);
    }
}
