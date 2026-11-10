import java.util.List;

public class DecoratorMain {
    public static void main(String[] args) {
        var fields = List.of("sku", "name", "priceCents", "phone", "email");
        var request = new ExportRequest("STORE-A", fields);
        var basic = new BasicExporter(new DemoPriceForms());
        Exporter chain =
                new LogDecorator(
                        new FieldFilterDecorator(
                                new MaskDecorator(basic), request.allowedFields()));
        ExportTable table = chain.export(request);
        System.out.println("加工后的两行=" + table.rows());
        System.out.println("按指定顺序选列=" + table.select(List.of("priceCents", "sku")).columns());
        Object original = basic.export(request).rows().get(0).get("phone");
        System.out.println("脱敏后基础表仍保留原值=" + "12345678901".equals(original));
        try {
            table.select(List.of("price"));
            throw new AssertionError("未知字段应被拒绝");
        } catch (IllegalArgumentException expected) {
            System.out.println("无效选列=" + expected.getMessage());
        }
    }
}
