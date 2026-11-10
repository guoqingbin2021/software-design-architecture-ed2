package demo;

import org.springframework.security.access.prepost.PreAuthorize;

public class SecuredPriceService implements PriceQuery {
    private final QueryState state;

    public SecuredPriceService(QueryState state) {
        this.state = state;
    }

    @PreAuthorize("hasAuthority('queryPriceForm')") // 经代理先查权限。
    public String queryPriceForm(String formId) {
        state.queryCalls++; // 进入目标方法后才计数。
        if (formId == null || formId.isBlank()) throw new IllegalArgumentException("调价单编号不能为空");
        return "调价单:" + formId;
    }
}
