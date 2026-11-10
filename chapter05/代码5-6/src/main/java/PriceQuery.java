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
