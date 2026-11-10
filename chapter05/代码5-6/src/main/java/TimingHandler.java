import java.lang.reflect.*;

final class TimingHandler implements InvocationHandler {
    private final Object target;

    TimingHandler(Object target) {
        this.target = java.util.Objects.requireNonNull(target);
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) { // 对象方法不计时。
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "Proxy(" + target.getClass().getName() + ")";
                default -> throw new UnsupportedOperationException();
            };
        }
        long start = System.nanoTime();
        try {
            return method.invoke(target, args); // 通过反射调用实际服务。
        } catch (InvocationTargetException ex) {
            throw ex.getCause(); // 解包并抛出目标方法的原异常。
        } finally { // 正常返回和异常退出都记录耗时。
            System.out.println(method.getName() + "耗时(ns):" + (System.nanoTime() - start));
        }
    }
}

final class ProxyClient {
    static PriceQuery create() {
        return (PriceQuery)
                Proxy.newProxyInstance(
                        PriceQuery.class.getClassLoader(),
                        new Class<?>[] {PriceQuery.class}, // 指定代理实现的查询接口。
                        new TimingHandler(new PriceService()));
    }
}
