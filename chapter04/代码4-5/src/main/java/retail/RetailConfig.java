package retail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class RetailConfig {
    @Bean
    Payment payment(Environment env) {
        return SimpleFactory.payment(env.getRequiredProperty("payment.channel"));
    }

    @Bean(destroyMethod = "close")
    Printer printer(Environment env) {
        return SimpleFactory.printer(env.getRequiredProperty("printer.model"));
    }

    @Bean
    CheckoutService checkout(Payment payment, Printer printer) {
        return new CheckoutService(payment, printer);
    }
}
