package demo;

import java.util.List;

final class Chains {
    private final Exporter basic;

    Chains(PriceFormRepository repository) {
        this.basic = new BasicExporter(repository);
    }

    Exporter forFields(List<String> fields) {
        return new LogDecorator(new FieldFilterDecorator(new MaskDecorator(basic), fields));
    }
}
