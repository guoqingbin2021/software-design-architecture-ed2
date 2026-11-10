package demo;

import java.util.ArrayList;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class ExportMain {
    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean
        DemoPriceForms repository() {
            return new DemoPriceForms();
        }

        @Bean
        ExportService exportService(DemoPriceForms repository) {
            return new ExportService(
                    new CurrentContext(),
                    new Scope(),
                    new FieldPolicy(),
                    new Chains(repository),
                    new Formatters());
        }
    }

    private static void signIn(String name, boolean canExport, boolean finance) {
        // 用服务端已认证的示例身份演示方法权限；此方法仅由演示入口调用。
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        if (canExport) authorities.add(new SimpleGrantedAuthority("exportPriceForm"));
        if (finance) authorities.add(new SimpleGrantedAuthority("ROLE_FINANCE"));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(name, "unused", authorities));
        SecurityContextHolder.setContext(context);
    }

    private static void showDenial(
            String label, ExportService service, DemoPriceForms repository, ExportCommand command) {
        int before = repository.queryCalls();
        try {
            service.export(command);
            throw new AssertionError("预期拒绝：" + label);
        } catch (AccessDeniedException ex) {
            int calls = repository.queryCalls() - before;
            if (calls != 0) throw new AssertionError("拒绝路径仍访问了仓储");
            System.out.println(label + "：已拒绝，仓储查询 " + calls + " 次");
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var service = context.getBean(ExportService.class);
            var repository = context.getBean(DemoPriceForms.class);
            var json = new ExportCommand("STORE-A", "JSON");
            var xml = new ExportCommand("STORE-A", "XML");

            signIn("manager-a", true, false);
            System.out.println("店长 JSON=" + service.export(json));
            showDenial("跨店请求", service, repository, new ExportCommand("STORE-B", "JSON"));

            signIn("finance-a", true, true);
            System.out.println("财务 JSON=" + service.export(json));
            System.out.println("财务 XML=" + service.export(xml));

            signIn("manager-a", false, false);
            showDenial("无导出权限", service, repository, json);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
