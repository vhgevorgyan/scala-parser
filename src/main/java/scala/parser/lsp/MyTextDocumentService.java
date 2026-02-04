package scala.parser.lsp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

public class MyTextDocumentService implements TextDocumentService {

    private final Map<String, String> documents = new HashMap<>();
    private final CompilerPipeline compiler = new CompilerPipeline();

    private LanguageClient client;

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var uri = params.getTextDocument().getUri();
        var text = params.getTextDocument().getText();
        documents.put(uri, text);
        var diag = new Diagnostic();
        diag.setMessage("Test: Server running");
        diag.setSeverity(DiagnosticSeverity.Warning);
        diag.setRange(new Range(new Position(0, 0), new Position(0, 1)));
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of(diag)));
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        var item = new CompletionItem("test");
        var completionList = new CompletionList(List.of(item));
        return CompletableFuture.completedFuture(Either.forRight(completionList));
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        var hover = new Hover(List.of(Either.forLeft("Hover works")));
        return completedFuture(hover);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var uri = params.getTextDocument().getUri();
        var text = params.getContentChanges().get(0).getText();
        documents.put(uri, text);
        var diagnostics = compiler.analyze(text);
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
