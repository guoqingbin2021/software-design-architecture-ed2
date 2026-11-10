public class DynamicProxyMain {
    public static void main(String[] args) {
        PriceQuery proxy = ProxyClient.create();
        System.out.println(proxy.queryPriceForm("P-A01"));
        try {
            proxy.queryPriceForm("");
            throw new AssertionError("代理应传播实际服务的参数异常");
        } catch (IllegalArgumentException expected) {
            System.out.println("原异常=" + expected.getMessage());
        }
        System.out.println("对象方法=" + proxy + "，自比较=" + proxy.equals(proxy));
    }
}
