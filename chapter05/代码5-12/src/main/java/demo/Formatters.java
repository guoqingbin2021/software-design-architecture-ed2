package demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

interface Formatter {
    String format(ExportTable table);
}

final class Formatters {
    private final ObjectMapper mapper = new ObjectMapper();

    Formatter require(String name) {
        if ("JSON".equals(name)) return this::json;
        if ("XML".equals(name)) return this::xml;
        throw new IllegalArgumentException("不支持的导出格式");
    }

    private String json(ExportTable table) {
        try {
            // 逐行序列化，保留金额的整数类型与空字段的 null。
            return mapper.writeValueAsString(table.rows());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("生成 JSON 失败", ex);
        }
    }

    private String xml(ExportTable table) {
        var output = new StringWriter();
        try {
            var writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            try {
                writer.writeStartElement("priceForms");
                for (var row : table.rows()) {
                    writer.writeStartElement("row");
                    for (String column : table.columns()) {
                        writer.writeStartElement(column);
                        Object value = row.get(column);
                        if (value == null) writer.writeAttribute("null", "true");
                        else writer.writeCharacters(value.toString());
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            } finally {
                writer.close();
            }
            return output.toString();
        } catch (XMLStreamException ex) {
            throw new IllegalStateException("生成 XML 失败", ex);
        }
    }
}
