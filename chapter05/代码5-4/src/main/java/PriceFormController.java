import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/priceForms")
public class PriceFormController {
    private final StorePriceQuery service;

    public PriceFormController(StorePriceQuery service) {
        this.service = service;
    }

    @GetMapping
    public List<PriceForm> query(@RequestParam("storeId") String storeId) { // 门店编号来自请求。
        return service.findAllByStore(storeId); // 反例：未经授权与范围核验。
    }
}

record PriceForm(String sku, String storeId, int priceCents) {}

interface StorePriceQuery {
    List<PriceForm> findAllByStore(String storeId);
}
