interface PermissionChecker {
    void check(String permission);
}

interface PriceQuery {
    String queryPriceForm(String formId);
}

final class PriceService implements PriceQuery {
    public String queryPriceForm(String formId) {
        if (formId == null || formId.isBlank()) {
            throw new IllegalArgumentException("调价单编号不能为空");
        }
        return "调价单:" + formId;
    }
}

final class PermissionProxy implements PriceQuery {
    private final PriceQuery target;
    private final PermissionChecker checker;

    PermissionProxy(PriceQuery target, PermissionChecker checker) {
        this.target = java.util.Objects.requireNonNull(target);
        this.checker = java.util.Objects.requireNonNull(checker);
    }

    public String queryPriceForm(String formId) {
        checker.check("queryPriceForm"); // 先检查权限，拒绝时抛出异常。
        return target.queryPriceForm(formId);
    }
}
