package scala.parser.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class MyLanguageServer implements LanguageServer, LanguageClientAware {

    private final MyTextDocumentService textService = new MyTextDocumentService();
    private final MyWorkspaceService workspaceService = new MyWorkspaceService();
    private LanguageClient client;

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        textService.setClient(client);
        client.logMessage(new MessageParams(MessageType.Info, "Language server started"));
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        var capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setHoverProvider(true);
        capabilities.setCompletionProvider(new CompletionOptions());

        // Semantic Tokens
        var legend = new SemanticTokensLegend(
                List.of("variable", "number", "operator", "keyword"),
                List.of()
        );

        var semanticOptions = new SemanticTokensWithRegistrationOptions();
        semanticOptions.setLegend(legend);
        semanticOptions.setFull(true);

        capabilities.setSemanticTokensProvider(semanticOptions);

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }
}
