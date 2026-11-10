package demo;

interface Exporter {
    ExportTable export(ExportRequest request); // 基础导出和装饰器共用此接口。
}

final class BasicExporter implements Exporter {
    private final PriceFormRepository repository;

    BasicExporter(PriceFormRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    public ExportTable export(ExportRequest request) {
        return ExportTable.from(repository.findByStore(request.storeId()));
    }
}

abstract class ExporterDecorator implements Exporter {
    protected final Exporter inner;

    ExporterDecorator(Exporter inner) {
        this.inner = java.util.Objects.requireNonNull(inner);
    }

    public ExportTable export(ExportRequest request) {
        return inner.export(request); // 把同一个请求转交内层。
    }
}

final class MaskDecorator extends ExporterDecorator {
    MaskDecorator(Exporter inner) {
        super(inner);
    }

    public ExportTable export(ExportRequest request) {
        return inner.export(request).mask("phone", "email"); // 返回后脱敏。
    }
}

final class FieldFilterDecorator extends ExporterDecorator {
    private final java.util.List<String> allowedFields;

    FieldFilterDecorator(Exporter inner, java.util.List<String> allowedFields) {
        super(inner);
        this.allowedFields = java.util.List.copyOf(allowedFields);
    }

    public ExportTable export(ExportRequest request) {
        return inner.export(request).select(allowedFields); // 按清单选列。
    }
}

final class LogDecorator extends ExporterDecorator {
    LogDecorator(Exporter inner) {
        super(inner);
    }

    public ExportTable export(ExportRequest request) {
        ExportTable table = inner.export(request);
        System.out.printf("表格加工完成：columns=%s, rows=%d%n", table.columns(), table.rows().size());
        return table; // 日志记录后原样返回表格。
    }
}
