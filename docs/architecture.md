# Architecture — Scala Parser Language Server

## Overview

```mermaid
flowchart TB
    subgraph IDE["VS Code Extension (TypeScript)"]
        EXT["extension.ts<br/>LSP Client<br/>vscode-languageclient"]
        TM["TextMate Grammar<br/>syntaxes/scala-parser.tmLanguage.json"]
        LC["Language Config<br/>language-configuration.json"]
    end

    subgraph LSP["LSP Server (Java / LSP4J)"]
        LS["MyLanguageServer<br/>Capabilities"]
        TDS["MyTextDocumentService<br/>didOpen / didChange / didSave<br/>Completion / Hover / GotoDef<br/>References / Rename / CodeActions<br/>DocumentSymbol / SemanticTokens"]
        WS["MyWorkspaceService"]
        DM["DocumentManager<br/>URI -> text store"]
    end

    subgraph PIPELINE["CompilerPipeline"]
        TOK["Tokenizer<br/>Source -> Token[]"]
        PAR["Parser<br/>Token[] -> ProgramNode"]
        TC["TypeChecker<br/>HM Algorithm W<br/>let-polymorphism<br/>ADT registration<br/>Exhaustiveness check"]
        EVAL["Evaluator<br/>Tree-walking interpreter<br/>Closures / ConstructorBuilder"]
    end

    subgraph AST["Abstract Syntax Tree"]
        LIT["LiteralNode / BoolNode / StringLiteralNode"]
        ID["IdentifierNode"]
        BIN["BinaryExprNode / UnaryExprNode"]
        IF["IfNode"]
        LAM["LambdaNode"]
        APP["ApplicationNode"]
        LET["LetStatementNode"]
        DATA["DataDeclNode"]
        CASE["CaseNode / CaseBranch"]
    end

    subgraph TYPES["Type System"]
        TNUM["TypeNumber"]
        TBOOL["TypeBool"]
        TSTR["TypeString"]
        TFUN["TypeFun (a -> b)"]
        TVAR["TypeVar (t0, t1, ...)"]
        TCTOR["TypeConstructor (Maybe a)"]
        SCHEME["TypeScheme (forall a. ...)"]
    end

    subgraph VALUES["Runtime Values"]
        VNUM["NumberValue"]
        VBOOL["BoolValue"]
        VSTR["StringValue"]
        CLOSURE["ClosureValue"]
        BUILTIN["BuiltinValue"]
        CTORVAL["ConstructorValue"]
        CTORBLD["ConstructorBuilder"]
    end

    EXT -- "stdin/stdout JSON-RPC" --> LS
    LS --> TDS
    LS --> WS
    TDS --> DM
    TDS --> PIPELINE

    TOK --> PAR
    PAR --> TC
    TC --> EVAL

    PAR -.-> AST
    TC -.-> TYPES
    EVAL -.-> VALUES

    subgraph SCOPE["Scope & Semantics"]
        SA["ScopeAnalyzer<br/>findDefinition / collectReferences"]
        STP["SemanticTokenProvider<br/>variable / keyword / operator / number / string"]
        HP["HoverProvider<br/>inferred type display"]
    end

    TDS --> SCOPE
```

## Data Flow

```mermaid
sequenceDiagram
    participant User as User
    participant VSC as VS Code
    participant LS as LSP Server
    participant Pipe as CompilerPipeline
    participant TC as TypeChecker
    participant Eval as Evaluator

    User->>VSC: Type code in .sp file
    VSC->>LS: didChange(text)
    LS->>Pipe: analyze(text)
    Pipe->>Pipe: Tokenizer → Parser → AST
    Pipe->>TC: check(AST)
    TC-->>Pipe: inferredTypes + errors
    Pipe->>Eval: evalProgram(AST)
    Eval-->>Pipe: Value + errors
    Pipe-->>LS: diagnostics[]
    LS-->>VSC: publishDiagnostics

    User->>VSC: Hover at position
    VSC->>LS: hover(textPosition)
    LS->>TC: lookup inferred type
    TC-->>LS: Type
    LS-->>VSC: Hover(type display)

    User->>VSC: Go to Definition
    VSC->>LS: definition(position)
    LS->>SA: findDefinition(AST, position)
    SA-->>LS: Location
    LS-->>VSC: location
```

## Component Dependencies

```mermaid
flowchart LR
    subgraph MAIN["Entry Points"]
        ParserApplication --> LanguageServerLauncher
        REPL --> CompilerPipeline
        Benchmarks --> CompilerPipeline
    end

    LanguageServerLauncher --> MyLanguageServer
    MyLanguageServer --> MyTextDocumentService
    MyLanguageServer --> MyWorkspaceService
    MyTextDocumentService --> DocumentManager
    MyTextDocumentService --> CompilerPipeline
    MyTextDocumentService --> ScopeAnalyzer
    MyTextDocumentService --> HoverProvider
    MyTextDocumentService --> SemanticTokenProvider

    CompilerPipeline --> Tokenizer
    CompilerPipeline --> Parser
    CompilerPipeline --> TypeChecker
    CompilerPipeline --> Evaluator

    TypeChecker --> TypeErrorException
    TypeChecker --> Type
    TypeChecker --> TypeScheme
    TypeChecker --> DataDeclNode

    Evaluator --> Env
    Evaluator --> Value
    Evaluator --> ConstructorBuilder

    Parser --> Token
    Parser --> ProgramNode
    Parser --> ParserError
    Parser --> ParserResult
```

## Package Structure

```
scala.parser
├── ParserApplication.java       -- LSP entry point
├── REPL.java                    -- Interactive REPL
├── Benchmarks.java              -- Performance benchmarks
├── core
│   ├── ParserError.java         -- Error record (message, line, col, length)
│   ├── ParserResult.java        -- AST + error list container
│   ├── ast/                     -- 16 AST node types
│   ├── lexer/                   -- Tokenizer + Token + TokenType
│   ├── parser/                  -- Recursive-descent Parser
│   ├── types/                   -- Type hierarchy + TypeChecker + TypeErrorException
│   ├── interpreter/             -- Evaluator + Values + Env
│   └── semantic/                -- ScopeAnalyzer + SemanticTokenProvider
│   └── hover/                   -- HoverProvider
└── lsp/                         -- LSP4J implementations
    ├── LanguageServerLauncher.java
    ├── MyLanguageServer.java
    ├── MyTextDocumentService.java
    ├── MyWorkspaceService.java
    ├── DocumentManager.java
    └── CompilerPipeline.java
```
