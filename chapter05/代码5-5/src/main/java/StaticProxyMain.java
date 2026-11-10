public class StaticProxyMain {
    public static void main(String[] args) {
        var actual = new PriceService();
        PriceQuery allowed = new PermissionProxy(actual, permission -> {});
        System.out.println("授权调用=" + allowed.queryPriceForm("P-A01"));
        PriceQuery denied =
                new PermissionProxy(
                        actual,
                        permission -> {
                            throw new SecurityException("缺少权限 " + permission);
                        });
        try {
            denied.queryPriceForm("");
            throw new AssertionError("无权限时应在进入实际服务前拒绝");
        } catch (SecurityException expected) {
            System.out.println("拒绝调用=" + expected.getMessage());
        }
    }
}
