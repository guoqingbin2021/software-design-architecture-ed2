package demo;

import org.springframework.security.access.prepost.PreAuthorize;

public class BoundaryService {
    private final QueryState state;

    public BoundaryService(QueryState state) {
        this.state = state;
    }

    public String throughThis() {
        return this.queryInternal();
    } // 绕过代理

    @PreAuthorize("hasAuthority('queryPriceForm')")
    public String queryInternal() {
        state.queryCalls++;
        return "P-A01";
    }

    @PreAuthorize("hasAuthority('queryPriceForm')")
    public final String finalQuery() {
        return "final";
    } // 不可覆盖。

    @PreAuthorize("hasAuthority('queryPriceForm')")
    private String privateQuery() {
        return "private";
    } // 私有方法不可覆盖。

    public String throughPrivate() {
        return privateQuery();
    }
}
