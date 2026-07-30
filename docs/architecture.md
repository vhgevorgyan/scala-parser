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
        TC["TypeChecker<br/>HM Algorithm W<br/>let-polymorphism<br/>ADT registration<br/>Exhaustiveness check<br/>Gradual typing (TypeDynamic)"]
        EVAL["Evaluator<br/>Tree-walking interpreter<br/>Closures / ConstructorBuilder<br/>Runtime type checks on ascription"]
        RED["SmallStepReducer<br/>step-by-step reduction<br/>FixpointNode / MutualRecursion<br/>Builtins (succ/pred/iszero/print)"]
    end

    subgraph AST["Abstract Syntax Tree (23 types)"]
        LIT["LiteralNode / BoolNode / CharLiteralNode / StringLiteralNode / UnitLiteralNode"]
        ID["IdentifierNode"]
        BIN["BinaryExprNode / UnaryExprNode"]
        IF["IfNode"]
        WHILE["WhileNode"]
        LAM["LambdaNode"]
        APP["ApplicationNode"]
        LET["LetStatementNode / MutualLetNode"]
        ENUM["EnumDeclNode / TypeAliasNode"]
        CASE["CaseNode / CaseBranch"]
        BLOCK["BlockNode"]
        ASC["AscriptionNode"]
        TUP["TupleNode"]
        FIX["FixpointNode"]
    end

    subgraph TYPES["Type System (10 types)"]
        TNUM["TypeNumber"]
        TBOOL["TypeBool"]
        TCHAR["TypeChar"]
        TSTR["TypeString"]
        TUNIT["TypeUnit"]
        TFUN["TypeFun (A => B)"]
        TVAR["TypeVar (t0, t1, ...)"]
        TCTOR["TypeConstructor (Maybe a)"]
        SCHEME["TypeScheme (forall a. ...)"]
        TDYN["TypeDynamic (gradual ?)"]
        TTUP["TypeTuple"]
    end

    subgraph VALUES["Runtime Values"]
        VNUM["NumberValue"]
        VBOOL["BoolValue"]
        VCHAR["CharValue"]
        VSTR["StringValue"]
        VUNIT["UnitValue"]
        VCLOSURE["ClosureValue"]
        VBUILTIN["BuiltinValue"]
        VCTORVAL["ConstructorValue"]
        VCTORBLD["ConstructorBuilder"]
        VTUPLE["TupleValue"]
    end

    EXT -- "stdin/stdout JSON-RPC" --> LS
    LS --> TDS
    LS --> WS
    TDS --> DM
    TDS --> PIPELINE

    TOK --> PAR
    PAR --> TC
    TC --> EVAL
    TC --> RED

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
        MutationAnalysis --> CompilerPipeline
        TypeErrorDiagnosisBenchmark --> CompilerPipeline
        BenchmarkSuite --> CompilerPipeline
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
    TypeChecker --> EnumDeclNode

    Evaluator --> Env
    Evaluator --> Value
    Evaluator --> ConstructorBuilder

    Parser --> Token
    Parser --> ProgramNode
    Parser --> ParserError
    Parser --> ParserResult

    subgraph REDUCTION["Small-Step Reduction"]
        SR[SmallStepReducer] --> Substitution
        SR --> FixpointNode
        SR -->|Runtime type checks| IfNode
    end
```

## Package Structure

```
scala.parser
├── ParserApplication.java              -- LSP entry point
├── REPL.java                           -- Interactive REPL
├── Benchmarks.java                     -- Performance benchmarks
├── MutationAnalysis.java               -- 12-op mutation analysis (37.2% detection)
├── TypeErrorDiagnosisBenchmark.java    -- 18 curated type-error programs
├── BenchmarkSuite.java                 -- 8 analysis modules (mutation matrix, scalability, error recovery, coverage, step-distribution, parser recovery, empirical matrix, runtime-breakdown); CLI: --runs N, --csv
├── core
│   ├── ParserError.java                -- Error record (message, line, col, length)
│   ├── ParserResult.java               -- AST + error list container
│   ├── ast/                            -- 23 AST node types (incl. WhileNode, CharLiteralNode, FixpointNode, TupleNode, MutualLetNode)
│   ├── lexer/                          -- Tokenizer + Token + TokenType (WHILE, CHAR, PACKAGE, IMPORT, SEALED, DOT, AT)
│   ├── parser/                         -- Recursive-descent Parser (while, char, multi-param, package/import/sealed/annotation ignoring)
│   ├── types/                          -- 10 type kinds + HM TypeChecker + TypeErrorException (incl. TypeChar, TypeDynamic)
│   ├── interpreter/                    -- Evaluator + 10 Value types + Env (incl. CharValue, TupleValue)
│   ├── reduction/                      -- SmallStepReducer + Substitution (step, reduce, beta-reduction, let-substitution)
│   ├── semantic/                       -- ScopeAnalyzer + SemanticTokenProvider
│   └── hover/                          -- HoverProvider
├── test/java/scala/parser/core/reduction/
│   ├── SmallStepPBT.java               -- 26 PBT properties (TypeDynamic, FixpointNode, TypeAliasNode, EnumDeclNode in generators; containsTypeDynamic/containsFixpoint guards)
│   ├── CoverageGuidedPBT.java          -- 8 targeted properties for hard-to-detect mutations (swap, let, undefined, inkl. chained/complex)
│   └── TypeSoundnessFormalTest.java    -- ~60+ formal tests (builtins, gradual typing, regression)
└── lsp/                                -- LSP4J implementations
    ├── LanguageServerLauncher.java
    ├── MyLanguageServer.java
    ├── MyTextDocumentService.java
    ├── MyWorkspaceService.java
    ├── DocumentManager.java
    └── CompilerPipeline.java
```
