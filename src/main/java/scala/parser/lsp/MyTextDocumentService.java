package scala.parser.lsp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import scala.parser.core.ast.ASTNode;
import scala.parser.core.ast.AstFinder;
import scala.parser.core.hover.HoverProvider;
import scala.parser.core.semantic.SemanticTokenProvider;

public class MyTextDocumentService implements TextDocumentService {

    private final Map<String, String> documents = new HashMap<>();
    private final CompilerPipeline compiler = new CompilerPipeline();

    private LanguageClient client;

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getTextDocument().getText();
        documents.put(uri, text);

        var result = compiler.analyze(text);

        var diagnostics = result.parserResult().errors.stream().map(err -> {
            Diagnostic diag = new Diagnostic();
            diag.setMessage(err.message);
            diag.setSeverity(DiagnosticSeverity.Error);
            diag.setRange(new Range(
                    new Position(err.line, err.column),
                    new Position(err.line, err.column + 1)
            ));
            return diag;
        }).toList();

        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getContentChanges().get(0).getText();
        documents.put(uri, text);

        var result = compiler.analyze(text);

        var diagnostics = result.parserResult().errors.stream().map(err -> {
            Diagnostic diag = new Diagnostic();
            diag.setMessage(err.message);
            diag.setSeverity(DiagnosticSeverity.Error);
            diag.setRange(new Range(
                    new Position(err.line, err.column),
                    new Position(err.line, err.column + 1)
            ));
            return diag;
        }).toList();

        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        CompletionItem item = new CompletionItem("test");
        CompletionList list = new CompletionList(List.of(item));
        return CompletableFuture.completedFuture(Either.forRight(list));
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String doc = documents.get(uri);
            if (doc == null) {
                return null;
            }

            int line = params.getPosition().getLine();
            int column = params.getPosition().getCharacter();

            var result = compiler.analyze(doc);
            ASTNode ast = result.parserResult().ast;
            if (ast == null) {
                return null;
            }

            ASTNode node = AstFinder.findNodeAt(ast, line, column);
            String text = HoverProvider.getHoverText(node);
            if (text == null) {
                return null;
            }

            MarkupContent content = new MarkupContent("plaintext", text);
            return new Hover(content);
        });
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String doc = documents.get(uri);
            if (doc == null) {
                return new SemanticTokens(List.of());
            }

            var result = compiler.analyze(doc);
            var ast = result.parserResult().ast;
            if (ast == null) {
                return new SemanticTokens(List.of());
            }

            var tokens = SemanticTokenProvider.collect(ast);

            List<Integer> data = new ArrayList<>();
            int lastLine = 0;
            int lastColumn = 0;

            for (var t : tokens) {
                int deltaLine = t.line - lastLine;
                int deltaStart = deltaLine == 0 ? t.column - lastColumn : t.column;

                data.add(deltaLine);
                data.add(deltaStart);
                data.add(t.length);
                data.add(tokenTypeIndex(t.type));
                data.add(0); // modifiers

                lastLine = t.line;
                lastColumn = t.column;
            }

            return new SemanticTokens(data);
        });
    }

    private int tokenTypeIndex(String type) {
        return switch (type) {
            case "variable" ->
                0;
            case "number" ->
                1;
            case "operator" ->
                2;
            case "keyword" ->
                3;
            default ->
                0;
        };
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.remove(uri);
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // optional
    }
}
