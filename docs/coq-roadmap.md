# Coq Formalization Roadmap

Goal: Extend `LambdaCore.v` with Gradual Typing (TypeDynamic), Fixpoint/MutualRecursion,
Pattern Matching (Case/Guards), and Blocks — all with fully proven soundness.

## Phase 1: Gradual Typing (TypeDynamic) — ✅ DONE

- `TDynamic` added to `ty`, `prec` relation with `PDynTop`, `PRefl`, `PFun`, `PTup`, `PConst`
- `TAscribe` typing rule uses `prec t t'` (NOT `consistent` — differs from original plan)
- `TSub` (subsumption) added as 17th constructor of `has_type`
- `ERuntimeError` added to `exp`, `value` (`VRuntimeError`), `has_type` (`TRuntimeError`), `fv`, `subst`
- `SBinOpError`, `SUnaryOpError` step rules for runtime type errors
- Canonical forms rewritten with `induction Ht` (TSub case: `exact IHHt1`)
- `context_monotonicity` lemma for type weakening under precision
- `substitution_lemma` updated for TSub
- Progress: TBinOp/TUnaryOp handle `None` via `SBinOpError`/`SUnaryOpError`
- Preservation: all 7 previously `admit` cases closed via TSub + `context_monotonicity`;
  SBinOp/SUnaryOp use `PRefl || PDynTop` for dynamic type cases
- Big-step semantics extended with `BBinOpError`, `BUnaryOpError`, `BRuntimeError`
- `big_step_implies_steps` proven (forward direction of Theorem 6)
- ~150 lines added, 0 admits

### Remaining: Gradual Guarantee (1.6)
- Static and dynamic gradual guarantees not yet formalized

## Phase 2: SmallStepReducer Features — ~800 lines (in progress)

### 2.1 FixpointNode (EFix) — ✅ DONE
- `EFix` added to `exp`, `fv`, `subst`
- `SFix`: `fix (λx. e) → [x ↦ fix (λx. e)] e`
- `SFixExpr`: step the body of a fixpoint
- `TFix`: `has_type Gamma e (TFun t t) → has_type Gamma (EFix e) t`
- `BFix` in big-step semantics
- Progress: uses `canonical_forms_lambda` to show lambda body, then `SFix`
- Preservation: `SFix` via `substitution_lemma` + `TFix`; `SFixExpr` via TSub + TFix
- `context_monotonicity`, `substitution_lemma`, `big_step_value`, `big_step_implies_steps` extended
- ~40 lines added, 0 admits expected

### 2.2 Pattern Matching (ECase)
- Add `branch` type: `EBranch pattern exp (option exp)` (pattern, body, optional guard)
- Add patterns: `EPCtor string (list string)` (constructor with var bindings), `EPWild`, `EPLit` (literal), `EPVar string`
- Add constructor values: `VCtor string (list exp)`
- Add match reduction rules: SCaseCtor, SCaseWild, SCaseLit (match), SCaseSkip (no match)
- Add exhaustiveness requirement
- Typing rules: TCase requires branches cover all constructors
- Progress + Preservation proofs

### 2.3 Blocks (EBlock) — ✅ DONE
- `BLet : string -> bool -> exp` + `BExpr : exp -> block_stmt`
- `EBlock : list block_stmt -> exp` added to mutual inductive with `ty`
- 6 step rules: SBlockEmpty, SBlockExprVal, SBlockExprSkip, SBlockExprStep, SBlockLetVal, SBlockLetStep
- `type_block` inductive with 4 rules (TBlockEmpty, TBlockExprLast, TBlockExprCons, TBlockLet)
- `TBlock` added to `has_type` as 19th constructor
- 4 big-step rules: BBlockEmpty, BBlockExprLast, BBlockExprCons, BBlockLet
- All proofs extended: progress_block+progress (mutual), preservation, context_monotonicity_block+context_monotonicity (mutual), substitution_lemma_block+substitution_lemma, big_step_value, big_step_implies_steps
- Helper lemmas: subst_blocks_nil_inv, context_weakening, subst_val_for_let_preserves_type, subst_val_for_let definition
- ~280 lines added

### 2.4 Mutual Recursion
- Add `MutualLet` to block statements
- Reduction: cross-substitution of all names before fixpoint wrapping
- Typing: mutual let-rec type rule

## Phase 3: Guards + As-Patterns — ~300 lines

### 3.1 Guard Conditions
- Extend branch guard from `option exp` with concrete reduction
- `SCaseGuardTrue`: branch matches if pattern matches AND guard is true
- `SCaseGuardFalse`: branch matches pattern but guard false → skip
- Typing: guard must be Bool; branch variables in scope for guard

### 3.2 As-Patterns
- `EPAsName string pattern`: `x @ P` binds `x` to the matched value
- Substitution: substitute `x` in body
- Typing: `x` gets the constructor type

## Effort Estimate

| Phase | Lines | Proof Complexity | Risk |
|-------|-------|-----------------|------|
| 1.1–1.4 | 200 | Low (mechanical adaptation) | Low |
| 1.5 | 50 | Low | Low |
| 1.6 | ~150 | Medium (new theorem) | Medium |
| 2.1 | 100 | Low (standard fixpoint) | Low |
| 2.2 | 500 | High (many cases) | High |
| 2.3 | 100 | Medium | Medium |
| 2.4 | 100 | Medium | Medium |
| 3.1 | 200 | Medium | Medium |
| 3.2 | 100 | Low | Low |

Total: ~1500 new lines of Coq, all fully proved.

## Verification

Every phase: `Coq.Script LambdaCore.v` must compile with 0 errors, 0 admitted.
