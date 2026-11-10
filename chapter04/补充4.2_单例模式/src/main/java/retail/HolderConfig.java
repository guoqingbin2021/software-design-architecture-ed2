package retail;

public final class HolderConfig {
    private HolderConfig() {}

    private static class Holder {
        private static final HolderConfig INSTANCE = new HolderConfig();
    }

    public static HolderConfig getInstance() {
        return Holder.INSTANCE;
    }
}
