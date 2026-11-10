import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipMain {
    public static void main(String[] args) throws IOException {
        String csv = "sku,name,priceCents\nDEMO-001,教学商品,250\n";
        // 每次写到新的临时目录，避免覆盖已有文件。
        Path path = Files.createTempDirectory("price-forms-").resolve("price-forms.csv.gz");
        try (OutputStream file = Files.newOutputStream(path);
                OutputStream buffer = new BufferedOutputStream(file);
                OutputStream gzip = new GZIPOutputStream(buffer)) { // 压缩后缓冲。
            gzip.write(csv.getBytes(StandardCharsets.UTF_8)); // 编码后压缩。
        } // 离开代码块时按声明的逆序关闭流。
        // 读取刚写出的文件：先解压，再按 UTF-8 解码。
        try (InputStream file = Files.newInputStream(path);
                InputStream gzip = new GZIPInputStream(file);
                Reader decoder = new InputStreamReader(gzip, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(decoder)) {
            String firstLine = reader.readLine();
            System.out.println(firstLine); // 输出：sku,name,priceCents
            StringWriter decoded = new StringWriter();
            if (firstLine != null) {
                decoded.write(firstLine + "\n");
                reader.transferTo(decoded);
            }
            if (!csv.equals(decoded.toString())) {
                throw new IOException("解压后的完整内容与原文不同");
            }
            System.out.println("完整内容校验通过：" + path.toAbsolutePath());
        }
    }
}
