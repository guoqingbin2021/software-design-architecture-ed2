package demo;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.*;

@Configuration
@EnableMethodSecurity(proxyTargetClass = true)
public class BoundaryConfig {
    @Bean
    public QueryState state() {
        return new QueryState();
    }

    @Bean
    public BoundaryService boundary(QueryState state) {
        return new BoundaryService(state);
    }

    @Bean
    public DelegatingQuery entry(BoundaryService service) {
        return new DelegatingQuery(service);
    }
}
