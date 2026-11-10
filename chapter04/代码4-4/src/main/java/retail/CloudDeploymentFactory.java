package retail;

import java.util.Objects;

public final class CloudDeploymentFactory implements DeploymentFactory {
    private final DeploymentConfig owned =
            new DeploymentConfig("CLOUD", "S01", "CNY_FEN", "memory:cloud");

    public DeploymentConfig createConfig() {
        return owned;
    }

    public Payment createPayment(DeploymentConfig config) {
        requireOwned(config);
        return (orderNo, amountFen) -> {
            if (orderNo == null || orderNo.isBlank() || amountFen <= 0)
                throw new IllegalArgumentException("Invalid order");
            return "CLOUD:" + orderNo + ":" + amountFen;
        };
    }

    public SalesStore createStore(DeploymentConfig config) {
        requireOwned(config);
        return new SalesStore() {
            private int count;

            public synchronized void save(String paymentResult) {
                Objects.requireNonNull(paymentResult, "paymentResult");
                count++;
            }

            public synchronized int count() {
                return count;
            }
        };
    }

    private void requireOwned(DeploymentConfig config) {
        if (config != owned)
            throw new IllegalArgumentException("Config is not owned by this CLOUD factory");
    }
}
