package retail;

public interface DeploymentFactory {
    DeploymentConfig createConfig();

    Payment createPayment(DeploymentConfig config);

    SalesStore createStore(DeploymentConfig config);
}
