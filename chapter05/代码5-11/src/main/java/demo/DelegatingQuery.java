package demo;

public class DelegatingQuery {
    private final BoundaryService service;

    public DelegatingQuery(BoundaryService service) {
        this.service = service;
    }

    public String query() {
        return service.queryInternal();
    } // 经代理调用
}
