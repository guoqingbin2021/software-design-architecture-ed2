package retail;

/** 由创建或装配方负责关闭；仅在内存中记录，不连接真实打印设备。 */
public interface Printer extends AutoCloseable {
    void print(String receipt);

    @Override
    void close();
}
