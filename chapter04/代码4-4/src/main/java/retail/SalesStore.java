package retail;

/** 教学用内存计数器，不连接数据库。 */
public interface SalesStore {
    void save(String paymentResult);

    int count();
}
