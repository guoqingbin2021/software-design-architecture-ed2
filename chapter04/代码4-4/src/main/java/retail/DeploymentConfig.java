package retail;

import java.util.Objects;

/** 两个产品族共用的不可变配置类型；工厂归属另按引用身份检查。 */
public record DeploymentConfig(String mode, String storeId, String currency, String endpoint) {
    public DeploymentConfig {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(storeId);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(endpoint);
    }
}
