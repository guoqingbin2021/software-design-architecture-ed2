package retail;

import java.util.Properties;
import java.io.FileInputStream;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** 从模块工作目录读取 store.properties；与教材的启动装配顺序相同。 */
public final class RetailApp {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (var in = new FileInputStream("store.properties")) {
            properties.load(in);
        }
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new PropertiesPropertySource("store", properties));
            context.register(RetailConfig.class);
            context.refresh();
            CheckoutService checkout = context.getBean(CheckoutService.class);
            System.out.println(checkout.checkout("O001", 4900L));
        }
    }
}
