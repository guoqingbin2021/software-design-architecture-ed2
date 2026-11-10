import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

public class CglibMain {
    static OpenPriceService create(PermissionChecker checker) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(OpenPriceService.class); // 指定代理的父类。
        enhancer.setCallback(
                (MethodInterceptor)
                        (obj, method, args, mp) -> {
                            if (method.getName().equals("queryPriceForm")
                                    && java.util.Arrays.equals(
                                            method.getParameterTypes(),
                                            new Class<?>[] {String.class})) {
                                checker.check("queryPriceForm"); // 只对签名匹配的查询做检查。
                            }
                            return mp.invokeSuper(obj, args); // 执行父类的方法实现。
                        });
        return (OpenPriceService) enhancer.create();
    }

    public static void main(String[] args) {
        OpenPriceService allowed = create(permission -> {});
        System.out.println("授权调用=" + allowed.queryPriceForm("P-A01"));
        OpenPriceService denied =
                create(
                        permission -> {
                            throw new SecurityException("缺少权限 " + permission);
                        });
        try {
            denied.queryPriceForm("P-A01");
            throw new AssertionError("缺少权限时应拒绝");
        } catch (SecurityException expected) {
            System.out.println("拒绝调用=" + expected.getMessage());
        }
    }
}

interface PermissionChecker {
    void check(String permission);
}
