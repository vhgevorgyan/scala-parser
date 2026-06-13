package scala.parser.lsp;

import java.util.HashMap;
import java.util.Map;

public class DocumentManager {

    private final Map<String, String> documents = new HashMap<>();

    public void open(String uri, String text) {
        documents.put(uri, text);
    }

    public void change(String uri, String text) {
        documents.put(uri, text);
    }

    public void close(String uri) {
        documents.remove(uri);
    }

    public String get(String uri) {
        return documents.get(uri);
    }

    public Map<String, String> all() {
        return Map.copyOf(documents);
    }
}
