package demo;

import org.springframework.context.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuditMain {
    static void show(PriceQuery service, QueryState state, boolean allowed, String id) {
        state.reset();
        Identity.setIdentity(allowed);
        try {
            System.out.println("返回=" + service.queryPriceForm(id));
        } catch (RuntimeException ex) {
            System.out.println("异常=" + ex.getClass().getSimpleName());
        }
        System.out.println("查询次数=" + state.queryCalls);
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) { // 由容器创建受管代理。
            PriceQuery service = context.getBean(PriceQuery.class);
            QueryState state = context.getBean(QueryState.class);
            System.out.println("一、成功调用");
            show(service, state, true, "P-A01");
            System.out.println("二、授权拒绝");
            show(service, state, false, "P-A01");
            System.out.println("三、业务失败");
            show(service, state, true, "");

        } finally {
            SecurityContextHolder.clearContext();
        } // 清除线程身份。
    }
}
