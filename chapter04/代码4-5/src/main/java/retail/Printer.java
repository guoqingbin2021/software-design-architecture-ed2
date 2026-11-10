package retail;

public interface Printer extends AutoCloseable {
    void print(String content);

    void close();
}
