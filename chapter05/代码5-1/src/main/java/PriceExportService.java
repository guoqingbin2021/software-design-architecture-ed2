import java.util.List;

final class PriceExportService {
    private final PriceFormRepository repository;

    PriceExportService(PriceFormRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    // 反例节选：请求参数直接进入查询，多项内容加工混在一起。
    public String export(String storeId, String operatorId, String format) {
        List<PriceForm> forms = repository.findByStore(storeId);
        StringBuilder out = new StringBuilder();
        for (PriceForm f : forms) {
            out.append(f.sku()).append(',').append(f.priceCents());
            if (userHasRole(operatorId, "FINANCE")) // 拼接时决定可见字段。
            out.append(',').append(f.costCents());
            out.append(',').append(mask(f.phone()));
        }
        return "XML".equals(format) ? toXml(out) : toJson(out);
    }

    private boolean userHasRole(String operatorId, String role) {
        // 用内存中的示例账号代替用户目录。反例仍然信任调用方提供的编号。
        return "finance-demo".equals(operatorId) && "FINANCE".equals(role);
    }

    private String mask(String phone) {
        String masked = ContactMask.phone(phone);
        return masked == null ? "" : masked;
    }

    // 字段名与行边界已经丢失，这里只能把整段原始文本装入目标格式。
    private String toXml(StringBuilder text) {
        String escaped =
                text.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<raw>" + escaped + "</raw>";
    }

    private String toJson(StringBuilder text) {
        return "{\"raw\":\"" + text + "\"}";
    }
}
