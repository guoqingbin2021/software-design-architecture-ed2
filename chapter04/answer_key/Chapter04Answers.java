import java.util.*;
import java.util.function.Supplier;

/** 习题4-8至4-10。设备与支付均为内存假对象。 */
public class Chapter04Answers {
    public interface Pay {
        String pay(String order, long amount);
    }

    /** 工厂方法：公共结算流程通过子类创建支付对象。 */
    public abstract static class Checkout {
        protected abstract Pay createPay();
        public final String checkout(String order, long amount) {
            if (order == null || order.isBlank() || amount < 0)
                throw new IllegalArgumentException("order/amount");
            return createPay().pay(order, amount);
        }
    }

    public static class AliCheckout extends Checkout {
        protected Pay createPay() { return new AliPay(); }
    }

    public static class WechatCheckout extends Checkout {
        protected Pay createPay() { return new WechatPay(); }
    }

    public static class AliPay implements Pay {
        public String pay(String order, long amount) { return "ALI:" + order + ":" + amount; }
    }
    public static class WechatPay implements Pay {
        public String pay(String order, long amount) { return "WECHAT:" + order + ":" + amount; }
    }
    private static final Map<String, Supplier<Checkout>> creators = Map.of(
            "ALI", AliCheckout::new, "WECHAT", WechatCheckout::new);
    public static Checkout checkoutFor(String id) {
        Supplier<Checkout> creator = creators.get(id);
        if (creator == null) throw new IllegalArgumentException("unknown channel");
        return creator.get();
    }

    public static Pay factory(String id) {
        if (!Set.of("ALI", "WECHAT").contains(Objects.requireNonNull(id)))
            throw new IllegalArgumentException("unknown channel");
        return (order, amount) -> {
            if (order == null || order.isBlank() || amount < 0)
                throw new IllegalArgumentException();
            return id + ":" + order + ":" + amount;
        };
    }

    public record Key(String store, String device, String model) {
        public Key {
            if (store == null
                    || store.isBlank()
                    || device == null
                    || device.isBlank()
                    || model == null
                    || model.isBlank()) throw new IllegalArgumentException("key");
        }
    }

    public static class Device implements AutoCloseable {
        int closes;

        public void close() {
            if (closes == 0) closes++;
        }
    }

    public static class DevicePool implements AutoCloseable {
        final Map<Key, Device> devices = new HashMap<>();
        boolean closed;

        public synchronized Device borrow(Key key) {
            if (closed) throw new IllegalStateException("pool closed");
            return devices.computeIfAbsent(Objects.requireNonNull(key), k -> new Device());
        }

        public synchronized void close() {
            if (closed) return;
            closed = true;
            devices.values().forEach(Device::close);
        }
    }

    static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }

    public static void main(String[] args) {
        check(checkoutFor("ALI").checkout("O001", 4900).equals("ALI:O001:4900"));
        check(checkoutFor("WECHAT").checkout("O001", 4900).equals("WECHAT:O001:4900"));
        try { checkoutFor("OTHER"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
        check(factory("ALI").pay("O001", 4900).equals("ALI:O001:4900"));
        check(factory("WECHAT").pay("O001", 4900).equals("WECHAT:O001:4900"));
        try {
            factory("OTHER");
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
        DevicePool p = new DevicePool();
        Device a = p.borrow(new Key("S01", "P1", "A"));
        check(a == p.borrow(new Key(new String("S01"), new String("P1"), new String("A"))));
        Device b = p.borrow(new Key("S01", "P1", "B")), c = p.borrow(new Key("S02", "P1", "A"));
        check(a != b && a != c && p.devices.size() == 3);
        p.close();
        p.close();
        check(a.closes == 1 && b.closes == 1 && c.closes == 1);
        try {
            p.borrow(new Key("S01", "P1", "A"));
            throw new AssertionError();
        } catch (IllegalStateException expected) {
        }
        System.out.println("4-8..4-10 PASS: devices=3 closes=3 value-key-reuse=true");
    }
}
