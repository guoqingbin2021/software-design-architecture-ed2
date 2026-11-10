package retail;

@FunctionalInterface
public interface Payment {
    String pay(String orderNo, long amountFen);
}
