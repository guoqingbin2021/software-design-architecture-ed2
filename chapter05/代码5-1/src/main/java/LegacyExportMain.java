public class LegacyExportMain {
    public static void main(String[] args) {
        var service = new PriceExportService(new DemoPriceForms());
        System.out.println(
                "STORE-A（财务编号，原始拼接结果）=" + service.export("STORE-A", "finance-demo", "JSON"));
        System.out.println("STORE-A（店长编号，原始拼接结果）=" + service.export("STORE-A", "manager-a", "XML"));
        System.out.println(
                "同一店长请求 STORE-B 仍取得数据=" + service.export("STORE-B", "manager-a", "JSON"));
    }
}
