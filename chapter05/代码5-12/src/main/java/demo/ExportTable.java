package demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record ExportTable(List<String> columns, List<Map<String, Object>> rows) {
    ExportTable {
        columns = List.copyOf(columns); // 固定列名，随后复制各行并设为只读。
        rows =
                rows.stream()
                        .map(
                                row ->
                                        Collections.unmodifiableMap(
                                                new LinkedHashMap<String, Object>(row)))
                        .toList();
    }

    ExportTable select(List<String> allowed) {
        List<String> selected = List.copyOf(allowed);
        if (!columns.containsAll(selected)) // 遍历前检查列名。
        throw new IllegalArgumentException("未知字段");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) { // 按字段清单的顺序重建每行。
            Map<String, Object> copy = new LinkedHashMap<>();
            for (String column : selected) copy.put(column, row.get(column));
            result.add(copy);
        }
        return new ExportTable(selected, result);
    }

    private static final List<String> BASE_COLUMNS =
            List.of("sku", "name", "priceCents", "costCents", "supplier", "phone", "email");

    static ExportTable from(List<PriceForm> forms) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PriceForm form : forms) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sku", form.sku());
            row.put("name", form.name());
            row.put("priceCents", form.priceCents());
            row.put("costCents", form.costCents());
            row.put("supplier", form.supplier());
            row.put("phone", form.phone());
            row.put("email", form.email());
            result.add(row);
        }
        return new ExportTable(BASE_COLUMNS, result);
    }

    ExportTable mask(String phoneColumn, String emailColumn) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            if (copy.get(phoneColumn) instanceof String phone) {
                copy.put(phoneColumn, ContactMask.phone(phone));
            }
            if (copy.get(emailColumn) instanceof String email) {
                copy.put(emailColumn, ContactMask.email(email));
            }
            result.add(copy);
        }
        return new ExportTable(columns, result);
    }
}
