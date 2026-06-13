package scala.parser.lsp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import scala.parser.core.ParserResult;
import scala.parser.core.ast.ASTNode;
import scala.parser.core.ast.AstFinder;
import scala.parser.core.ast.IdentifierNode;
import scala.parser.core.ast.LetStatementNode;
import scala.parser.core.ast.ProgramNode;
import scala.parser.core.hover.HoverProvider;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.TokenType;
import scala.parser.core.lexer.Tokenizer;
import scala.parser.core.semantic.ScopeAnalyzer;
import scala.parser.core.semantic.SemanticTokenProvider;

public class MyTextDocumentService implements TextDocumentService {

    private final DocumentManager documents;
    private final CompilerPipeline compiler = new CompilerPipeline();

    public MyTextDocumentService(DocumentManager documents) {
        this.documents = documents;
    }

    private LanguageClient client;

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getTextDocument().getText();
        documents.open(uri, text);
        publishDiagnostics(uri, text);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getContentChanges().get(0).getText();
        documents.change(uri, text);
        publishDiagnostics(uri, text);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.close(uri);
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = documents.get(uri);
        if (text != null) {
            publishDiagnostics(uri, text);
        }
    }

    private void publishDiagnostics(String uri, String text) {
        var result = compiler.analyze(text);
        var diagnostics = result.parserResult().errors.stream().map(err -> {
            Diagnostic diag = new Diagnostic();
            diag.setMessage(err.message);
            diag.setSeverity(DiagnosticSeverity.Error);
            int length = Math.max(err.length, 1);
            diag.setRange(new Range(
                    new Position(err.line, err.column),
                    new Position(err.line, err.column + length)
            ));
            return diag;
        }).toList();
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    // ---------------------------------------------------------
    // Completion
    // ---------------------------------------------------------
    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String doc = documents.get(uri);
            if (doc == null) return Either.forRight(new CompletionList(List.of()));

            int line = params.getPosition().getLine();
            int column = params.getPosition().getCharacter();

            String prefix = getPrefix(doc, line, column);
            Set<String> suggestions = new HashSet<>();

            // Keywords
            String[] keywords = {"let", "if", "then", "else", "true", "false"};
            for (String kw : keywords) {
                if (kw.startsWith(prefix)) suggestions.add(kw);
            }

            // Builtins
            String[] builtins = {"succ", "pred", "iszero", "print"};
            for (String b : builtins) {
                if (b.startsWith(prefix)) suggestions.add(b);
            }

            // In-scope identifiers from let bindings
            var result = compiler.analyze(doc);
            ProgramNode ast = result.parserResult().ast;
            if (ast != null) {
                for (var stmt : ast.statements) {
                    if (stmt instanceof LetStatementNode let) {
                        if (let.name.startsWith(prefix)) suggestions.add(let.name);
                    }
                }
            }

            List<CompletionItem> items = suggestions.stream()
                    .sorted()
                    .map(CompletionItem::new)
                    .toList();
            return Either.forRight(new CompletionList(items));
        });
    }

    private String getPrefix(String doc, int line, int column) {
        String[] lines = doc.split("\n", -1);
        if (line < 0 || line >= lines.length) return "";
        String textLine = lines[line];
        int start = Math.min(column, textLine.length());
        int end = start;
        while (start > 0 && Character.isLetterOrDigit(textLine.charAt(start - 1))) {
            start--;
        }
        return textLine.substring(start, end);
    }

    // ---------------------------------------------------------
    // Hover
    // ---------------------------------------------------------
    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String doc = documents.get(uri);
            if (doc == null) return null;

            int line = params.getPosition().getLine();
            int column = params.getPosition().getCharacter();

            var result = compiler.analyze(doc);
            ASTNode ast = result.parserResult().ast;
            if (ast == null) return null;

            ASTNode node = AstFinder.findNodeAt(ast, line, column);
            String text = HoverProvider.getHoverText(node, result.inferredTypes());
            if (text == null) return null;

            MarkupContent content = new MarkupContent("plaintext", text);
            return new Hover(content);
        });
    }

    // ---------------------------------------------------------
    // Go-to-Definition
    // ---------------------------------------------------------
    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            org.eclipse.lsp4j.DefinitionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String doc = documents.get(uri);
            if (doc == null) return Either.forLeft(List.of());

            int line = params.getPosition().getLine();
            int column = params.getPosition().getCharacter();

            var result = compiler.analyze(doc);
            ASTNode ast = result.parserResult().ast;
            if (ast == null) return Either.forLeft(List.of());

            ASTNode node = AstFinder.findNodeAt(ast, line, column);
            if (!(node instanceof IdentifierNode id)) {
                return Either.forLeft(List.of());
            }

            ScopeAnalyzer.Definition def = ScopeAnalyzer.findDefinition(ast, id);
            if (def == null) return Either.forLeft(List.of());

            Location loc = new Location(uri,
                    new Range(new Position(def.line(), def.column()),
                            new Position(def.line(), def.column() + def.name().length())));
            return Either.forLeft(List.of(loc));
        });
    }

    // ---------------------------------------------------------
    // Find References
    // ---------------------------------------------------------
    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String doc = documents.get(uri);
            if (doc == null) return List.of();

            int line = params.getPosition().getLine();
            int column = params.getPosition().getCharacter();

            var result = compiler.analyze(doc);
            ASTNode ast = result.parserResult().ast;
            if (ast == null) return List.of();

            ASTNode node = AstFinder.findNodeAt(ast, line, column);
            if (!(node instanceof IdentifierNode id)) return List.of();

            var refs = ScopeAnalyzer.collectReferences(ast, id.name);
            return refs.stream()
                    .map(r -> new Location(uri,
                            new Range(new Position(r.line(), r.column()),
                                    new Position(r.line(), r.column() + r.length()))))
                    .toList();
        });
    }

    // ---------------------------------------------------------
    // Document Symbols
    // ---------------------------------------------------------
    @Override
    public CompletableFuture<List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String doc = documents.get(uri);
            if (doc == null) return List.of();

            var result = compiler.analyze(doc);
            ASTNode ast = result.parserResult().ast;
            if (ast == null) return List.of();

            var defs = ScopeAnalyzer.collectDefinitions(ast);
            List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>> symbols = new ArrayList<>();
            for (var d : defs) {
                var si = new org.eclipse.lsp4j.SymbolInformation(
                        d.name(), SymbolKind.Variable,
                        new org.eclipse.lsp4j.Location(uri,
                                new Range(new Position(d.line(), d.column()),
                                        new Position(d.line(), d.column() + d.name().length()))));
                symbols.add(Either.forLeft(si));
            }
            return symbols;
        });
    }

    // ---------------------------------------------------------
    // Rename
    // ---------------------------------------------------------
    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String doc = documents.get(uri);
            if (doc == null) return new WorkspaceEdit(Map.of());

            int line = params.getPosition().getLine();
            int column = params.getPosition().getCharacter();
            String newName = params.getNewName();

            var result = compiler.analyze(doc);
            ASTNode ast = result.parserResult().ast;
            if (ast == null) return new WorkspaceEdit(Map.of());

            ASTNode node = AstFinder.findNodeAt(ast, line, column);
            if (!(node instanceof IdentifierNode id)) return new WorkspaceEdit(Map.of());

            var refs = ScopeAnalyzer.collectReferences(ast, id.name);
            List<TextEdit> edits = refs.stream()
                    .map(r -> new TextEdit(
                            new Range(new Position(r.line(), r.column()),
                                    new Position(r.line(), r.column() + r.length())),
                            newName))
                    .toList();

            var changes = new HashMap<String, List<TextEdit>>();
            changes.put(uri, edits);
            return new WorkspaceEdit(changes);
        });
    }

    // ---------------------------------------------------------
    // Code Actions
    // ---------------------------------------------------------
    @Override
    public CompletableFuture<List<Either<org.eclipse.lsp4j.Command, CodeAction>>> codeAction(CodeActionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            List<Either<org.eclipse.lsp4j.Command, CodeAction>> actions = new ArrayList<>();
            for (Diagnostic d : params.getContext().getDiagnostics()) {
                String msg = d.getMessage();

                if (msg.contains("Undefined variable")) {
                    String name = msg.replace("Undefined variable '", "").replace("'", "");
                    CodeAction defineLet = new CodeAction("Define '" + name + "' with let");
                    defineLet.setKind("quickfix");
                    defineLet.setDiagnostics(List.of(d));
                    Position pos = d.getRange().getStart();
                    var edit = new TextEdit(
                            new Range(new Position(0, 0), new Position(0, 0)),
                            "let " + name + " = 0\n");
                    var changes = new HashMap<String, List<TextEdit>>();
                    changes.put(params.getTextDocument().getUri(), List.of(edit));
                    defineLet.setEdit(new WorkspaceEdit(changes));
                    actions.add(Either.forRight(defineLet));
                }

                if (msg.contains("Expected identifier after 'let'")) {
                    CodeAction addIdent = new CodeAction("Add identifier after 'let'");
                    addIdent.setKind("quickfix");
                    addIdent.setDiagnostics(List.of(d));
                    Position pos = d.getRange().getStart();
                    var edit = new TextEdit(
                            new Range(new Position(pos.getLine(), pos.getCharacter()),
                                    new Position(pos.getLine(), pos.getCharacter())),
                            "x ");
                    var changes = new HashMap<String, List<TextEdit>>();
                    changes.put(params.getTextDocument().getUri(), List.of(edit));
                    addIdent.setEdit(new WorkspaceEdit(changes));
                    actions.add(Either.forRight(addIdent));
                }
            }
            return actions;
        });
    }

    // ---------------------------------------------------------
    // Semantic Tokens
    // ---------------------------------------------------------
    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String doc = documents.get(uri);
            if (doc == null) return new SemanticTokens(List.of());

            var result = compiler.analyze(doc);
            var ast = result.parserResult().ast;
            if (ast == null) return new SemanticTokens(List.of());

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
                data.add(0);

                lastLine = t.line;
                lastColumn = t.column;
            }

            return new SemanticTokens(data);
        });
    }

    private int tokenTypeIndex(String type) {
        return switch (type) {
            case "variable" -> 0;
            case "number" -> 1;
            case "operator" -> 2;
            case "keyword" -> 3;
            case "string" -> 4;
            default -> 0;
        };
    }
}
