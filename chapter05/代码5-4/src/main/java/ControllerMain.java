import java.util.List;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ControllerMain {
    public static void main(String[] args) throws Exception {
        // 模拟门店A调用者把请求参数改为门店B。使用 MockMvc 在进程内发请求。
        StorePriceQuery repository =
                storeId ->
                        switch (storeId) {
                            case "STORE-A" -> List.of(new PriceForm("DEMO-001", "STORE-A", 250));
                            case "STORE-B" -> List.of(new PriceForm("DEMO-003", "STORE-B", 360));
                            default -> List.of();
                        };
        var mvc = MockMvcBuilders.standaloneSetup(new PriceFormController(repository)).build();
        var response =
                mvc.perform(get("/priceForms").param("storeId", "STORE-B"))
                        .andReturn()
                        .getResponse();
        System.out.println("HTTP " + response.getStatus());
        System.out.println("未经授权返回的门店B数据=" + response.getContentAsString());
        if (response.getStatus() != 200 || !response.getContentAsString().contains("DEMO-003")) {
            throw new AssertionError("预期反例会暴露门店B的数据");
        }
    }
}
