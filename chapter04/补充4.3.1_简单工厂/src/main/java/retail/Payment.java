package retail;

/** 教材的教学模拟契约：正常返回表示模拟成功，金额以分为单位。 */
@FunctionalInterface
public interface Payment {
    String pay(String orderNo, long amountFen);
}
