package retail;

public final class LazyConfig {
    private static LazyConfig instance;

    private LazyConfig() {}

    public static synchronized LazyConfig getInstance() {
        if (instance == null) instance = new LazyConfig();
        return instance;
    }
}
