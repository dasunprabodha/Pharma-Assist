package lk.sliit.prescription.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.SdkNumber;
import software.amazon.awssdk.core.document.Document;

/** Converts between Jackson JSON trees and the AWS SDK's {@link Document} (used by Converse tool use). */
final class Documents {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Documents() {
    }

    static Document fromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return Document.fromNull();
        }
        if (node.isObject()) {
            Map<String, Document> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> map.put(e.getKey(), fromJson(e.getValue())));
            return Document.fromMap(map);
        }
        if (node.isArray()) {
            List<Document> list = new ArrayList<>();
            node.forEach(child -> list.add(fromJson(child)));
            return Document.fromList(list);
        }
        if (node.isBoolean()) {
            return Document.fromBoolean(node.booleanValue());
        }
        if (node.isNumber()) {
            return Document.fromNumber(SdkNumber.fromString(node.asText()));
        }
        return Document.fromString(node.asText());
    }

    static JsonNode toJson(Document doc) {
        if (doc == null || doc.isNull()) {
            return NODES.nullNode();
        }
        if (doc.isMap()) {
            ObjectNode obj = NODES.objectNode();
            doc.asMap().forEach((k, v) -> obj.set(k, toJson(v)));
            return obj;
        }
        if (doc.isList()) {
            ArrayNode arr = NODES.arrayNode();
            doc.asList().forEach(v -> arr.add(toJson(v)));
            return arr;
        }
        if (doc.isBoolean()) {
            return NODES.booleanNode(doc.asBoolean());
        }
        if (doc.isNumber()) {
            return NODES.numberNode(doc.asNumber().bigDecimalValue());
        }
        return NODES.textNode(doc.asString());
    }
}
