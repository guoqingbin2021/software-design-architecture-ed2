package demo;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.*;

@Configuration
@EnableMethodSecurity(proxyTargetClass = true) // 启用方法授权。
public class AppConfig {
    @Bean
    public QueryState state() {
        return new QueryState();
    }

    @Bean
    public PriceQuery priceQuery(QueryState state) {
        return new SecuredPriceService(state);
    }
}
