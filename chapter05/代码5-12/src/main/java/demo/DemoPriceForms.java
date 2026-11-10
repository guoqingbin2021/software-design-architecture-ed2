package demo;

import java.util.List;

record PriceForm(
        String sku,
        String name,
        int priceCents,
        int costCents,
        String supplier,
        String phone,
        String email) {}

interface PriceFormRepository {
    List<PriceForm> findByStore(String storeId);
}

final class DemoPriceForms implements PriceFormRepository {
    private static final List<PriceForm> STORE_A =
            List.of(
                    new PriceForm(
                            "DEMO-001",
                            "教学商品",
                            250,
                            120,
                            "SUP-DEMO",
                            "12345678901",
                            "alice@example.invalid"),
                    new PriceForm("DEMO-002", "演示商品, A&B", 400, 210, "SUP-DEMO", null, null));
    private static final List<PriceForm> STORE_B =
            List.of(new PriceForm("DEMO-003", "门店B商品", 360, 170, "SUP-B", null, null));

    private int queryCalls;

    @Override
    public List<PriceForm> findByStore(String storeId) {
        queryCalls++;
        return switch (storeId) {
            case "STORE-A" -> STORE_A;
            case "STORE-B" -> STORE_B;
            default -> List.of();
        };
    }

    int queryCalls() {
        return queryCalls;
    }
}
