package retail;

public final class DeploymentMain {
    private static int checks;

    public static void main(String[] args) {
        DeploymentFactory local = new LocalDeploymentFactory();
        DeploymentFactory cloud = new CloudDeploymentFactory();
        exercise(local, "LOCAL");
        exercise(cloud, "CLOUD");
        expectRejected(() -> local.createPayment(cloud.createConfig()), "云端配置不能创建本地支付");
        expectRejected(() -> local.createStore(cloud.createConfig()), "云端配置不能创建本地存储");
        expectRejected(() -> cloud.createPayment(local.createConfig()), "本地配置不能创建云端支付");
        expectRejected(() -> cloud.createStore(local.createConfig()), "本地配置不能创建云端存储");
        System.out.println("PASS: " + checks + " checks; all products use memory mocks.");
    }

    private static void exercise(DeploymentFactory factory, String mode) {
        DeploymentConfig config = factory.createConfig();
        check(config == factory.createConfig(), "工厂返回其持有的同一个配置");
        check(mode.equals(config.mode()), "部署类型正确");
        check("S01".equals(config.storeId()) && "CNY_FEN".equals(config.currency()), "门店与金额单位一致");
        Payment payment = factory.createPayment(config);
        SalesStore store = factory.createStore(config);
        String result = payment.pay("O001", 4900L);
        check((mode + ":O001:4900").equals(result), "同族支付按分传递金额");
        check(store.count() == 0, "初始存储为空");
        store.save(result);
        check(store.count() == 1, "同族存储计数增加");
        DeploymentConfig copy =
                new DeploymentConfig(
                        config.mode(), config.storeId(), config.currency(), config.endpoint());
        check(config.equals(copy) && config != copy, "另一个配置字段值相同但引用不同");
        expectRejected(() -> factory.createPayment(copy), "同值不同引用不能创建支付");
        expectRejected(() -> factory.createStore(copy), "同值不同引用不能创建存储");
        expectRejected(() -> factory.createPayment(null), "空配置不能创建支付");
        expectRejected(() -> factory.createStore(null), "空配置不能创建存储");
        System.out.println(result + "; stored=" + store.count());
    }

    private static void expectRejected(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            check(true, label);
            return;
        }
        throw new AssertionError(label);
    }

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
}
