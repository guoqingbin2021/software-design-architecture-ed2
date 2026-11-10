package demo;

import java.util.function.Supplier;
import org.springframework.context.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;

public class BoundaryMain {
    static void show(String label, QueryState state, Supplier<String> call) {
        state.reset();
        String result;
        try {
            result = call.get();
        } catch (Exception ex) {
            result = ex.getClass().getSimpleName();
        }
        System.out.printf("%s：%s，业务查询%d次%n", label, result, state.queryCalls);
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(BoundaryConfig.class)) {
            var service = context.getBean(BoundaryService.class);
            var entry = context.getBean(DelegatingQuery.class);
            var state = context.getBean(QueryState.class);
            Identity.setIdentity(false);
            show("外部直接查询", state, service::queryInternal);
            show("this内部调用", state, service::throughThis);
            show("经协作服务查询", state, entry::query);
            show("final方法", state, service::finalQuery);
            show("private方法的内部调用", state, service::throughPrivate);
            Identity.setIdentity(true);
            show("有查询权限的外部查询", state, service::queryInternal);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
