package demo;

import org.springframework.security.access.prepost.PreAuthorize;

public class ExportService {
    private final CurrentContext context;
    private final Scope scope;
    private final FieldPolicy fieldPolicy;
    private final Chains chains;
    private final Formatters formatters;

    public ExportService(
            CurrentContext context,
            Scope scope,
            FieldPolicy fieldPolicy,
            Chains chains,
            Formatters formatters) {
        this.context = context;
        this.scope = scope;
        this.fieldPolicy = fieldPolicy;
        this.chains = chains;
        this.formatters = formatters;
    }

    @PreAuthorize("hasAuthority('exportPriceForm')")
    public String export(ExportCommand command) { // 查询前核验门店。
        Actor actor = context.currentActor(); // 从服务器保存的身份中取当前操作者。
        String storeId = scope.requireAllowedStore(actor, command.storeId());
        java.util.List<String> fields =
                java.util.List.copyOf(fieldPolicy.allowedFields(actor)); // 保留授权列序。
        ExportRequest trusted = new ExportRequest(storeId, fields);
        Exporter exporter = chains.forFields(fields); // 装配加工链。
        ExportTable table = exporter.export(trusted); // 加工后再按格式转换。
        return formatters.require(command.format()).format(table);
    }
}
