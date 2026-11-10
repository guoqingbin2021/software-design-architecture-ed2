package retail;

public final class DclConfig {
    private static volatile DclConfig instance;

    private DclConfig() {}

    public static DclConfig getInstance() {
        DclConfig result = instance;
        if (result == null) {
            synchronized (DclConfig.class) {
                result = instance;
                if (result == null) {
                    result = new DclConfig();
                    instance = result;
                }
            }
        }
        return result;
    }
}
