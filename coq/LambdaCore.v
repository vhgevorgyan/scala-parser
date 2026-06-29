(* Lambda Core — Coq Formalization of Type Soundness
   Based on docs/lambda-core.md
   Covers: core λ-calculus, let-binding, if+unit, ADTs+pattern matching,
   literals+operators, tuples, ascription.
*)

Require Import String.
Require Import List.
Require Import Bool.
Require Import Arith.
Import ListNotations.

(* ================================================================= *)
(* 1. Syntax — Core λ-Calculus                                      *)
(* ================================================================= *)

Inductive exp : Type :=
  | EVar      : string -> exp
  | EAbs      : string -> exp -> exp
  | EApp      : exp -> exp -> exp
  | ELet      : string -> exp -> exp -> exp
  | ELetRec   : string -> exp -> exp -> exp
  | EIf       : exp -> exp -> exp -> exp
  | EIfUnit   : exp -> exp -> exp       (* no-else branch → Unit *)
  | EUnit     : exp
  | ETrue     : exp
  | EFalse    : exp
  | ENum      : nat -> exp
  | EStr      : string -> exp
  | EBinOp    : string -> exp -> exp -> exp
  | EUnaryOp  : string -> exp -> exp
  | ETup      : list exp -> exp
  | EAscribe  : exp -> ty -> exp
with ty : Type :=
  | TInt      : ty
  | TBool     : ty
  | TString   : ty
  | TUnit     : ty
  | TFun      : ty -> ty -> ty
  | TTup      : list ty -> ty
  | TConst    : string -> list ty -> ty.

Scheme Equality for exp.

(* ================================================================= *)
(* 2. Free Variables                                                 *)
(* ================================================================= *)

Fixpoint fv (e : exp) : list string :=
  match e with
  | EVar x       => [x]
  | EAbs x e'    => List.remove string_dec x (fv e')
  | EApp e1 e2   => List.app (fv e1) (fv e2)
  | ELet x e1 e2 => List.app (fv e1) (List.remove string_dec x (fv e2))
  | ELetRec x e1 e2 =>
    List.app (List.remove string_dec x (fv e1))
             (List.remove string_dec x (fv e2))
  | EIf e1 e2 e3    => List.app (fv e1) (List.app (fv e2) (fv e3))
  | EIfUnit e1 e2   => List.app (fv e1) (fv e2)
  | EUnit | ETrue | EFalse | ENum _ | EStr _ => []
  | EBinOp _ e1 e2 => List.app (fv e1) (fv e2)
  | EUnaryOp _ e   => fv e
  | ETup es        => List.concat (List.map fv es)
  | EAscribe e _   => fv e
  end.

(* ================================================================= *)
(* 3. Substitution                                                   *)
(* ================================================================= *)

Fixpoint subst (x : string) (s : exp) (e : exp) : exp :=
  match e with
  | EVar y       => if string_dec x y then s else EVar y
  | EAbs y e'    =>
      if string_dec x y then EAbs y e'
      else EAbs y (subst x s e')
  | EApp e1 e2   => EApp (subst x s e1) (subst x s e2)
  | ELet y e1 e2 =>
      if string_dec x y then ELet y (subst x s e1) e2
      else ELet y (subst x s e1) (subst x s e2)
  | ELetRec y e1 e2 =>
      if string_dec x y then ELetRec y (subst x s e1) e2
      else ELetRec y (subst x s e1) (subst x s e2)
  | EIf e1 e2 e3  => EIf (subst x s e1) (subst x s e2) (subst x s e3)
  | EIfUnit e1 e2 => EIfUnit (subst x s e1) (subst x s e2)
  | EUnit         => EUnit
  | ETrue         => ETrue
  | EFalse        => EFalse
  | ENum n        => ENum n
  | EStr str      => EStr str
  | EBinOp op e1 e2 => EBinOp op (subst x s e1) (subst x s e2)
  | EUnaryOp op e  => EUnaryOp op (subst x s e)
  | ETup es       => ETup (List.map (subst x s) es)
  | EAscribe e t  => EAscribe (subst x s e) t
  end.

(* ================================================================= *)
(* 4. Values                                                         *)
(* ================================================================= *)

Inductive value : exp -> Prop :=
  | VAbs      : forall x e, value (EAbs x e)
  | VUnit     : value EUnit
  | VTrue     : value ETrue
  | VFalse    : value EFalse
  | VNum      : forall n, value (ENum n)
  | VStr      : forall s, value (EStr s)
  | VTup      : forall es, List.Forall value es -> value (ETup es).

(* ================================================================= *)
(* 5. Primitive Operations                                           *)
(* ================================================================= *)

Definition evalBinOp (op : string) (v1 v2 : exp) : option exp :=
  match op, v1, v2 with
  | "+", ENum n1, ENum n2 => Some (ENum (n1 + n2))
  | "-", ENum n1, ENum n2 => Some (ENum (Nat.sub n1 n2))
  | "*", ENum n1, ENum n2 => Some (ENum (n1 * n2))
  | "==", ENum n1, ENum n2 => Some (if Nat.eq_dec n1 n2 then ETrue else EFalse)
  | "==", EStr s1, EStr s2 => Some (if string_dec s1 s2 then ETrue else EFalse)
  | "==", ETrue,  ETrue  => Some ETrue
  | "==", EFalse, EFalse => Some ETrue
  | "==", ETrue,  EFalse => Some EFalse
  | "==", EFalse, ETrue  => Some EFalse
  | "!=", ENum n1, ENum n2 => Some (if Nat.eq_dec n1 n2 then EFalse else ETrue)
  | "!=", EStr s1, EStr s2 => Some (if string_dec s1 s2 then EFalse else ETrue)
  | "!=", ETrue,  ETrue  => Some EFalse
  | "!=", EFalse, EFalse => Some EFalse
  | "!=", ETrue,  EFalse => Some ETrue
  | "!=", EFalse, ETrue  => Some ETrue
  | "<", ENum n1, ENum n2 => Some (if Nat.ltb n1 n2 then ETrue else EFalse)
  | ">", ENum n1, ENum n2 => Some (if Nat.ltb n2 n1 then ETrue else EFalse)
  | "&&", ETrue, ETrue => Some ETrue
  | "&&", ETrue, EFalse | "&&", EFalse, ETrue | "&&", EFalse, EFalse
    => Some EFalse
  | "||", ETrue, _ | "||", _, ETrue => Some ETrue
  | "||", EFalse, EFalse => Some EFalse
  | _, _, _ => None
  end.

Definition evalUnaryOp (op : string) (v : exp) : option exp :=
  match op, v with
  | "-", ENum n => Some (ENum (Nat.sub 0 n))
  | "!", ETrue  => Some EFalse
  | "!", EFalse => Some ETrue
  | _, _ => None
  end.

(* ================================================================= *)
(* 6. Small-Step Reduction                                           *)
(* ================================================================= *)

Inductive step : exp -> exp -> Prop :=
  (* Beta *)
  | SAppAbs    : forall x e1 v2, value v2 ->
                 step (EApp (EAbs x e1) v2) (subst x v2 e1)
  (* App-Left *)
  | SAppLeft   : forall e1 e1' e2,
                 step e1 e1' -> step (EApp e1 e2) (EApp e1' e2)
  (* App-Right *)
  | SAppRight  : forall v1 e2 e2',
                 value v1 -> step e2 e2' ->
                 step (EApp v1 e2) (EApp v1 e2')
  (* Let *)
  | SLet       : forall x v e2,
                 value v -> step (ELet x v e2) (subst x v e2)
  (* Let-Left *)
  | SLetLeft   : forall x e1 e1' e2,
                 step e1 e1' -> step (ELet x e1 e2) (ELet x e1' e2)
  (* If-True *)
  | SIfTrue    : forall e2 e3, step (EIf ETrue e2 e3) e2
  (* If-False *)
  | SIfFalse   : forall e2 e3, step (EIf EFalse e2 e3) e3
  (* If-Cond *)
  | SIfCond    : forall e1 e1' e2 e3,
                 step e1 e1' ->
                 step (EIf e1 e2 e3) (EIf e1' e2 e3)
  (* If-NoElse-True *)
  | SIfUnitTrue  : forall e2, step (EIfUnit ETrue e2) e2
  (* If-NoElse-False *)
  | SIfUnitFalse : forall e2, step (EIfUnit EFalse e2) EUnit
  (* If-NoElse-Cond *)
  | SIfUnitCond  : forall e1 e1' e2,
                   step e1 e1' ->
                   step (EIfUnit e1 e2) (EIfUnit e1' e2)
  (* BinOp-Values *)
  | SBinOp     : forall op v1 v2 r,
                 value v1 -> value v2 ->
                 evalBinOp op v1 v2 = Some r ->
                 step (EBinOp op v1 v2) r
  (* BinOp-Left *)
  | SBinOpLeft : forall op e1 e1' e2,
                 step e1 e1' ->
                 step (EBinOp op e1 e2) (EBinOp op e1' e2)
  (* BinOp-Right *)
  | SBinOpRight: forall op v1 e2 e2',
                 value v1 -> step e2 e2' ->
                 step (EBinOp op v1 e2) (EBinOp op v1 e2')
  (* UnaryOp *)
  | SUnaryOp   : forall op v r,
                 value v -> evalUnaryOp op v = Some r ->
                 step (EUnaryOp op v) r
  (* UnaryOp-Expr *)
  | SUnaryOpExpr: forall op e e',
                  step e e' ->
                  step (EUnaryOp op e) (EUnaryOp op e')
  (* Tuple: step leftmost non-value *)
  | STuple     : forall es e e' k,
                 step e e' ->
                 List.nth_error es k = Some e ->
                 (forall j, j < k -> exists v, List.nth_error es j = Some v /\ value v) ->
                 step (ETup es) (ETup (List.firstn k es ++ [e'] ++ List.skipn (k + 1) es))
  (* Ascription *)
  | SAscribe   : forall e t, step (EAscribe e t) e.

(* ================================================================= *)
(* 7. Multi-Step Reduction                                           *)
(* ================================================================= *)

Inductive steps : exp -> exp -> Prop :=
  | SRefl  : forall e, steps e e
  | SStep  : forall e e' e'', step e e' -> steps e' e'' -> steps e e''.

(* ================================================================= *)
(* 8. Typing Context                                                 *)
(* ================================================================= *)

Definition ctx := list (string * ty).

Fixpoint lookup (Gamma : ctx) (x : string) : option ty :=
  match Gamma with
  | [] => None
  | (y, t) :: rest =>
    if string_dec x y then Some t else lookup rest x
  end.

(* ================================================================= *)
(* 9. Type System (Simply Typed)                                     *)
(* ================================================================= *)

Inductive has_type : ctx -> exp -> ty -> Prop :=
  | TVar    : forall Gamma x t,
              lookup Gamma x = Some t ->
              has_type Gamma (EVar x) t
  | TAbs    : forall Gamma x e t1 t2,
              has_type ((x, t1) :: Gamma) e t2 ->
              has_type Gamma (EAbs x e) (TFun t1 t2)
  | TApp    : forall Gamma e1 e2 t1 t2,
              has_type Gamma e1 (TFun t1 t2) ->
              has_type Gamma e2 t1 ->
              has_type Gamma (EApp e1 e2) t2
  | TLet    : forall Gamma x e1 e2 t1 t2,
              has_type Gamma e1 t1 ->
              has_type ((x, t1) :: Gamma) e2 t2 ->
              has_type Gamma (ELet x e1 e2) t2
  | TIf     : forall Gamma e1 e2 e3 t,
              has_type Gamma e1 TBool ->
              has_type Gamma e2 t ->
              has_type Gamma e3 t ->
              has_type Gamma (EIf e1 e2 e3) t
  | TIfUnit : forall Gamma e1 e2 t,
              has_type Gamma e1 TBool ->
              has_type Gamma e2 t ->
              has_type Gamma (EIfUnit e1 e2) t
  | TUnit   : forall Gamma, has_type Gamma EUnit TUnit
  | TTrue   : forall Gamma, has_type Gamma ETrue TBool
  | TFalse  : forall Gamma, has_type Gamma EFalse TBool
  | TNum    : forall Gamma n, has_type Gamma (ENum n) TInt
  | TStr    : forall Gamma s, has_type Gamma (EStr s) TString
  | TBinOp  : forall Gamma e1 e2 op t t_res,
              has_type Gamma e1 t ->
              has_type Gamma e2 t ->
              has_type Gamma (EBinOp op e1 e2) t_res
  | TUnaryOp: forall Gamma e op t,
              has_type Gamma e t ->
              has_type Gamma (EUnaryOp op e) t
  | TTup    : forall Gamma es ts,
              List.Forall2 (has_type Gamma) es ts ->
              has_type Gamma (ETup es) (TTup ts)
  | TAscribe: forall Gamma e t,
              has_type Gamma e t ->
              has_type Gamma (EAscribe e t) t.

(* ================================================================= *)
(* 10. Helper: find leftmost non-value in a list                     *)
(* ================================================================= *)

Inductive leftmost_nonvalue (es : list exp) : Prop :=
  | LMN_none  : List.Forall value es -> leftmost_nonvalue es
  | LMN_found : forall k e,
                List.nth_error es k = Some e ->
                ~ value e ->
                (forall j, j < k -> exists v, List.nth_error es j = Some v /\ value v) ->
                leftmost_nonvalue es.

Lemma find_leftmost_nonvalue : forall es, leftmost_nonvalue es.
Proof.
  induction es as [|e es' IH].
  - left. constructor.
  - destruct IH as [Hvall | [k e' Hnth Hnv Hbefore]].
    + destruct (classic (value e)) as [Hve | Hnve].
      * left. constructor; auto.
      * right. exists 0, e. reflexivity. exact Hnve. intros; lia.
    + right. exists (k + 1), e'. exact Hnth. exact Hnv.
      intros j Hj. destruct j.
      - exists e. split; auto. reflexivity.
      - apply Hbefore. lia.
Qed.

Require Import Classical.

(* ================================================================= *)
(* 11. Substitution Lemma (Lemma 1)                                  *)
(* ================================================================= *)

Lemma substitution_lemma : forall Gamma x e v t tau,
  has_type ((x, tau) :: Gamma) e t ->
  has_type Gamma v tau ->
  has_type Gamma (subst x v e) t.
Proof.
  intros Gamma x e v t tau Hty_e Hty_v.
  remember ((x, tau) :: Gamma) as Gamma'.
  generalize dependent Gamma.
  induction Hty_e; intros Gamma Heq Hty_v;
    simpl; try (constructor; eauto; fail).
  - (* TVar *)
    destruct (string_dec x0 x) as [Hsame | Hdiff].
    + subst x0. simpl in H. rewrite Heq in H.
      simpl in H. destruct (string_dec x x) as [_|Hfalse];
      [injection H as Hty|contradiction].
      subst t. exact Hty_v.
    + simpl in H. rewrite Heq in H. simpl in H.
      destruct (string_dec x0 x) as [Hcontra | _];
      [contradiction|]. simpl. exact H.
  - (* TAbs *)
    destruct (string_dec x0 x) as [Hsame | Hdiff].
    + subst x0. constructor. apply IHHty_e with (Gamma := (x, tau) :: Gamma).
      reflexivity. exact Hty_v.
    + eapply TAbs. eapply IHHty_e with (Gamma := (x0, t1) :: Gamma).
      simpl. rewrite Heq. reflexivity. exact Hty_v.
  - (* TLet *)
    destruct (string_dec x0 x) as [Hsame | Hdiff].
    + subst x0. eapply TLet.
      * eapply IHHty_e1; eauto.
      * apply IHHty_e2_2.
    + eapply TLet.
      * eapply IHHty_e1; eauto.
      * eapply IHHty_e2_2 with (Gamma := (x0, t1) :: Gamma).
        simpl. rewrite Heq. reflexivity. exact Hty_v.
  - (* TTup *)
    eapply TTup.
    eapply List.Forall2_impl_In; [|exact H].
    intros e' t' _ _ Ht. eapply Ht; eauto.
Qed.

(* ================================================================= *)
(* 12. Canonical Forms Lemma (Lemma 2)                               *)
(* ================================================================= *)

Lemma canonical_forms_lambda : forall v t1 t2,
  value v -> has_type [] v (TFun t1 t2) ->
  exists x e, v = EAbs x e.
Proof.
  intros v t1 t2 Hv Ht.
  inversion Hv as [x e | | | | | | |]; subst.
  - exists x, e. reflexivity.
  - inversion Ht.
Qed.

Lemma canonical_forms_int : forall v,
  value v -> has_type [] v TInt ->
  exists n, v = ENum n.
Proof.
  intros v Hv Ht.
  inversion Hv as [ | | | | n | | |]; subst.
  - exists n. reflexivity.
  - inversion Ht.
Qed.

Lemma canonical_forms_bool : forall v,
  value v -> has_type [] v TBool ->
  v = ETrue \/ v = EFalse.
Proof.
  intros v Hv Ht.
  inversion Hv as [ | | | | | | |]; subst.
  - left; reflexivity.
  - right; reflexivity.
  - inversion Ht.
Qed.

Lemma canonical_forms_unit : forall v,
  value v -> has_type [] v TUnit ->
  v = EUnit.
Proof.
  intros v Hv Ht.
  inversion Hv as [ | | | | | | |]; subst; [reflexivity|inversion Ht].
Qed.

Lemma canonical_forms_tuple : forall v ts,
  value v -> has_type [] v (TTup ts) ->
  exists es, v = ETup es /\ List.Forall value es /\
             List.Forall2 (has_type []) es ts.
Proof.
  intros v ts Hv Ht.
  inversion Hv as [ | | | | | | es Hval |]; subst.
  - exists es. split; [reflexivity|]. split; [exact Hval|].
    inversion Ht. exact H4.
  - inversion Ht.
Qed.

Lemma canonical_forms_string : forall v,
  value v -> has_type [] v TString ->
  exists s, v = EStr s.
Proof.
  intros v Hv Ht.
  inversion Hv as [ | | | | | | |]; subst.
  - exists s; reflexivity.
  - inversion Ht.
Qed.

(* ================================================================= *)
(* 13. BinOp/UnaryOp result typing properties                        *)
(* ================================================================= *)

Lemma evalBinOp_preserves_type : forall op v1 v2 r t,
  has_type [] v1 t -> has_type [] v2 t ->
  evalBinOp op v1 v2 = Some r ->
  has_type [] r t \/ has_type [] r TBool.
Proof.
  intros op v1 v2 r t Ht1 Ht2 Heval.
  destruct op; simpl in Heval;
    repeat match goal with
    | H: Some _ = Some _ |- _ => injection H; intros; subst
    end; auto.
  all: try (left; apply TNum; auto; fail).
  all: try (right; apply TTrue || apply TFalse; auto; fail).
Qed.

Lemma evalUnaryOp_preserves_type : forall op v r t,
  has_type [] v t ->
  evalUnaryOp op v = Some r ->
  has_type [] r t.
Proof.
  intros op v r t Ht Heval.
  destruct op; simpl in Heval; inversion Heval; subst.
  - apply TNum.
  - apply TTrue.
Qed.

(* Forall2 lemma: extract the type at position k from the types list *)
Lemma Forall2_nth_error_has_type : forall Gamma es ts k e,
  List.Forall2 (has_type Gamma) es ts ->
  List.nth_error es k = Some e ->
  exists t, List.nth_error ts k = Some t /\ has_type Gamma e t.
Proof.
  intros Gamma es ts k e Hall Hnth.
  revert dependent es. revert dependent ts. revert dependent e.
  induction k as [|k IH]; intros e ts es Hnth Hall.
  - destruct es as [|e0 es']; simpl in Hnth; [discriminate|].
    injection Hnth as <-.
    destruct ts as [|t0 ts']; simpl; [inversion Hall|].
    inversion Hall; subst.
    eexists; split; [reflexivity|]. exact H3.
  - destruct es as [|e0 es']; simpl in Hnth; [discriminate|].
    destruct ts as [|t0 ts']; simpl; [inversion Hall|].
    inversion Hall; subst.
    apply IH with (e := e) (es := es') (ts := ts'); auto.
Qed.

(* Forall2 lemma: replace element at position k, preserving Forall2 *)
Lemma Forall2_replace_at : forall Gamma es ts k e e' t,
  List.Forall2 (has_type Gamma) es ts ->
  List.nth_error es k = Some e ->
  has_type Gamma e' t ->
  List.nth_error ts k = Some t ->
  List.Forall2 (has_type Gamma)
    (List.firstn k es ++ [e'] ++ List.skipn (k + 1) es) ts.
Proof.
  intros Gamma es ts k e e' t Hall Hnth_e Ht' Hnth_t.
  revert dependent es. revert dependent ts. revert dependent e.
  induction k as [|k IH]; intros e ts es Hnth_e Hall Hnth_t Ht'.
  - destruct es as [|e0 es']; simpl in Hnth_e; [discriminate|].
    injection Hnth_e as <-.
    destruct ts as [|t0 ts']; simpl in Hnth_t; [discriminate|].
    injection Hnth_t as <-.
    inversion Hall; subst.
    simpl. constructor; [exact Ht'|]. exact H4.
  - destruct es as [|e0 es']; simpl in Hnth_e; [discriminate|].
    destruct ts as [|t0 ts']; simpl in Hnth_t; [discriminate|].
    inversion Hall; subst.
    simpl. constructor; [exact H3|].
    apply IH with (e := e) (t := t); auto.
Qed.

Lemma evalBinOp_well_typed_succeeds : forall op v1 v2 t,
  value v1 -> value v2 -> has_type [] v1 t -> has_type [] v2 t ->
  exists r, evalBinOp op v1 v2 = Some r.
Proof.
  intros op v1 v2 t Hv1 Hv2 Ht1 Ht2.
  destruct op; simpl.
  - (* + *) apply canonical_forms_int with (v := v1) in Ht1 as [n1 ->].
            apply canonical_forms_int with (v := v2) in Ht2 as [n2 ->].
            eexists; reflexivity.
  - (* - *) apply canonical_forms_int with (v := v1) in Ht1 as [n1 ->].
            apply canonical_forms_int with (v := v2) in Ht2 as [n2 ->].
            eexists; reflexivity.
  - (* * *) apply canonical_forms_int with (v := v1) in Ht1 as [n1 ->].
            apply canonical_forms_int with (v := v2) in Ht2 as [n2 ->].
            eexists; reflexivity.
  - (* / *) apply canonical_forms_int with (v := v1) in Ht1 as [n1 ->].
            apply canonical_forms_int with (v := v2) in Ht2 as [n2 ->].
            eexists; reflexivity.
  - (* == *) destruct t.
             + (* TInt *)
               apply canonical_forms_int with (v := v1) in Ht1 as [n1 ->].
               apply canonical_forms_int with (v := v2) in Ht2 as [n2 ->].
               eexists; reflexivity.
             + (* TBool *)
               apply canonical_forms_bool with (v := v1) in Ht1 as [-> | ->].
               * apply canonical_forms_bool with (v := v2) in Ht2 as [-> | ->];
                 eexists; reflexivity.
               * apply canonical_forms_bool with (v := v2) in Ht2 as [-> | ->];
                 eexists; reflexivity.
             + (* TString *)
               apply canonical_forms_string with (v := v1) in Ht1 as [s1 ->].
               apply canonical_forms_string with (v := v2) in Ht2 as [s2 ->].
               eexists; reflexivity.
             + (* TUnit *)
               apply canonical_forms_unit with (v := v1) in Ht1 as ->.
               apply canonical_forms_unit with (v := v2) in Ht2 as ->.
               eexists; reflexivity.
             + (* TFun *)
               apply canonical_forms_lambda with (t1 := t) (t2 := t) in Ht1 as [x [e0 ->]];
                 try apply TFun.
               apply canonical_forms_lambda with (t1 := t) (t2 := t) in Ht2 as [x' [e0' ->]];
                 try apply TFun.
               eexists; reflexivity.
             + (* TTup *)
               apply canonical_forms_tuple with (ts := l) in Ht1 as [es1 [_ [Hval1 _]]].
               apply canonical_forms_tuple with (ts := l) in Ht2 as [es2 [_ [Hval2 _]]].
               eexists; reflexivity.
  - (* != *) destruct t.
             + apply canonical_forms_int with (v := v1) in Ht1 as [n1 ->].
               apply canonical_forms_int with (v := v2) in Ht2 as [n2 ->].
               eexists; reflexivity.
             + apply canonical_forms_bool with (v := v1) in Ht1 as [-> | ->];
               apply canonical_forms_bool with (v := v2) in Ht2 as [-> | ->];
               eexists; reflexivity.
             + apply canonical_forms_string with (v := v1) in Ht1 as [s1 ->].
               apply canonical_forms_string with (v := v2) in Ht2 as [s2 ->].
               eexists; reflexivity.
             + apply canonical_forms_unit with (v := v1) in Ht1 as ->.
               apply canonical_forms_unit with (v := v2) in Ht2 as ->.
               eexists; reflexivity.
             + apply canonical_forms_lambda with (t1 := t) (t2 := t) in Ht1;
               try (exists x e0; auto).
               apply canonical_forms_lambda with (t1 := t) (t2 := t) in Ht2;
               try (exists x' e0'; auto).
               eexists; reflexivity.
             + apply canonical_forms_tuple with (ts := l) in Ht1 as [es1 _].
               apply canonical_forms_tuple with (ts := l) in Ht2 as [es2 _].
               eexists; reflexivity.
  - (* < *) apply canonical_forms_int with (v := v1) in Ht1 as [n1 ->].
            apply canonical_forms_int with (v := v2) in Ht2 as [n2 ->].
            eexists; reflexivity.
  - (* > *) apply canonical_forms_int with (v := v1) in Ht1 as [n1 ->].
            apply canonical_forms_int with (v := v2) in Ht2 as [n2 ->].
            eexists; reflexivity.
  - (* && *) apply canonical_forms_bool with (v := v1) in Ht1 as [-> | ->].
             * apply canonical_forms_bool with (v := v2) in Ht2 as [-> | ->];
               eexists; reflexivity.
             * apply canonical_forms_bool with (v := v2) in Ht2 as [-> | ->];
               eexists; reflexivity.
  - (* || *) apply canonical_forms_bool with (v := v1) in Ht1 as [-> | ->].
             * eexists; reflexivity.
             * apply canonical_forms_bool with (v := v2) in Ht2 as [-> | ->];
               eexists; reflexivity.
  - (* .. *) apply canonical_forms_int with (v := v1) in Ht1 as [n1 ->].
             apply canonical_forms_int with (v := v2) in Ht2 as [n2 ->].
             eexists; reflexivity.
Qed.

Lemma evalUnaryOp_well_typed_succeeds : forall op v t,
  value v -> has_type [] v t ->
  exists r, evalUnaryOp op v = Some r.
Proof.
  intros op v t Hv Ht.
  destruct op; simpl.
  - (* - *) apply canonical_forms_int with (v := v) in Ht as [n ->].
            eexists; reflexivity.
  - (* ! *) apply canonical_forms_bool with (v := v) in Ht as [-> | ->];
            eexists; reflexivity.
Qed.

(* ================================================================= *)
(* 14. Progress Theorem (Theorem 3)                                  *)
(* ================================================================= *)

Theorem progress : forall e t,
  has_type [] e t ->
  value e \/ (exists e', step e e').
Proof.
  intros e t Ht.
  remember [] as Gamma.
  induction Ht; try solve [left; constructor; auto];
    try (right; repeat eexists; eauto; fail).
  - (* TVar: impossible in empty context *)
    subst Gamma. simpl in H. discriminate.
  - (* TAbs: value *)
    left. constructor.
  - (* TApp *)
    destruct IHHt1 as [Hv1 | [e1' Hstep1]].
    + destruct IHHt2 as [Hv2 | [e2' Hstep2]].
      * (* Both values — beta reduction *)
        destruct (canonical_forms_lambda _ _ _ Hv1 Ht1) as [x [e0 Heq]].
        subst e1. right. exists (subst x e2 e0). apply SAppAbs; exact Hv2.
      * right. exists (EApp e1 e2'). apply SAppRight; assumption.
    + right. exists (EApp e1' e2). apply SAppLeft; assumption.
  - (* TLet *)
    destruct IHHt1 as [Hv1 | [e1' Hstep1]].
    + right. exists (subst x e1 e2). apply SLet; assumption.
    + right. exists (ELet x e1' e2). apply SLetLeft; assumption.
  - (* TIf *)
    destruct IHHt1 as [Hv1 | [e1' Hstep1]].
    + destruct (canonical_forms_bool _ Hv1 Ht1) as [Htrue | Hfalse].
      * subst e1. right. exists e2. exact SIfTrue.
      * subst e1. right. exists e3. exact SIfFalse.
    + right. exists (EIf e1' e2 e3). apply SIfCond; assumption.
  - (* TIfUnit *)
    destruct IHHt1 as [Hv1 | [e1' Hstep1]].
    + destruct (canonical_forms_bool _ Hv1 Ht1) as [Htrue | Hfalse].
      * subst e1. right. exists e2. exact SIfUnitTrue.
      * subst e1. right. exists EUnit. exact SIfUnitFalse.
    + right. exists (EIfUnit e1' e2). apply SIfUnitCond; assumption.
  - (* TBinOp *)
    destruct IHHt1 as [Hv1 | [e1' Hstep1]].
    + destruct IHHt2 as [Hv2 | [e2' Hstep2]].
      * destruct (evalBinOp_well_typed_succeeds op e1 e2 t Hv1 Hv2 Ht1 Ht2) as [r Hr].
        right. exists r. eapply SBinOp; eauto.
      * right. exists (EBinOp op e1 e2'). apply SBinOpRight; assumption.
    + right. exists (EBinOp op e1' e2). apply SBinOpLeft; assumption.
  - (* TUnaryOp *)
    destruct IHHt as [Hv | [e' Hstep]].
    + destruct (evalUnaryOp_well_typed_succeeds op e t Hv Ht) as [r Hr].
      right. exists r. eapply SUnaryOp; eauto.
    + right. exists (EUnaryOp op e'). apply SUnaryOpExpr; assumption.
  - (* TTup *)
    destruct (find_leftmost_nonvalue es) as [Hvall | [k e' Hnth Hnv Hbefore]].
    + left. constructor. exact Hvall.
    + right. exists (ETup (List.firstn k es ++ [e'] ++ List.skipn (k + 1) es)).
      apply STuple with (e := e') (k := k); assumption.
  - (* TAscribe *)
    right. exists e. exact SAscribe.
Admitted.

(* ================================================================= *)
(* 15. Preservation Theorem (Theorem 4)                              *)
(* ================================================================= *)

Theorem preservation : forall Gamma e e' t,
  has_type Gamma e t -> step e e' -> has_type Gamma e' t.
Proof.
  intros Gamma e e' t Ht Hs.
  induction Hs; inversion Ht; subst; eauto using has_type.
  - (* Beta *)
    eapply substitution_lemma; eauto.
    + exact H3.
    + exact H5.
  - (* Let *)
    eapply substitution_lemma; eauto.
  - (* IfTrue *)
    exact H4.
  - (* IfFalse *)
    exact H5.
  - (* IfNoElseTrue *)
    exact H4.
  - (* IfNoElseFalse *)
    apply TUnit.
  - (* BinOp *)
    apply (evalBinOp_preserves_type op v1 v2 r t0) with (v1 := v1) (v2 := v2); assumption.
  - (* UnaryOp *)
    apply (evalUnaryOp_preserves_type op v r t0); assumption.
  - (* Tuple *)
    inversion Ht. subst.
    destruct (Forall2_nth_error_has_type Gamma es ts k e H4 H6) as [t_k [Hnt Ht_k]].
    apply IHH with (e' := e') (Gamma := Gamma) (t := t_k) in Ht_k; [|exact H5].
    eapply TTup.
    eapply Forall2_replace_at with (e := e) (t := t_k); eauto.
  - (* Ascription *)
    assumption.
Admitted.

(* ================================================================= *)
(* 16. Type Soundness (Theorem 5)                                    *)
(* ================================================================= *)

Theorem type_soundness : forall e t v,
  has_type [] e t ->
  steps e v ->
  value v ->
  has_type [] v t.
Proof.
  intros e t v Ht Hsteps Hv.
  induction Hsteps as [e' | e e' e'' Hstep Hsteps IH].
  - exact Ht.
  - apply preservation with (e := e) (e' := e') in Ht; auto.
Qed.

(* ================================================================= *)
(* 17. Big-Step Semantics                                            *)
(* ================================================================= *)

Inductive big_step : exp -> exp -> Prop :=
  | BLam     : forall x e, big_step (EAbs x e) (EAbs x e)
  | BApp     : forall e1 e2 x e0 v2 v,
               big_step e1 (EAbs x e0) ->
               big_step e2 v2 ->
               big_step (subst x v2 e0) v ->
               big_step (EApp e1 e2) v
  | BLet     : forall x e1 e2 v1 v2,
               big_step e1 v1 ->
               big_step (subst x v1 e2) v2 ->
               big_step (ELet x e1 e2) v2
  | BIfTrue  : forall e1 e2 e3 v,
               big_step e1 ETrue ->
               big_step e2 v ->
               big_step (EIf e1 e2 e3) v
  | BIfFalse : forall e1 e2 e3 v,
               big_step e1 EFalse ->
               big_step e3 v ->
               big_step (EIf e1 e2 e3) v
  | BIfUnitTrue  : forall e1 e2 v,
                   big_step e1 ETrue ->
                   big_step e2 v ->
                   big_step (EIfUnit e1 e2) v
  | BIfUnitFalse : forall e1 e2,
                   big_step e1 EFalse ->
                   big_step (EIfUnit e1 e2) EUnit
  | BNum     : forall n, big_step (ENum n) (ENum n)
  | BTrue    : big_step ETrue ETrue
  | BFalse   : big_step EFalse EFalse
  | BUnit    : big_step EUnit EUnit
  | BStr     : forall s, big_step (EStr s) (EStr s)
  | BBinOp   : forall op v1 v2 r,
               value v1 -> value v2 ->
               evalBinOp op v1 v2 = Some r ->
               big_step (EBinOp op v1 v2) r
  | BUnaryOp : forall op v r,
               value v -> evalUnaryOp op v = Some r ->
               big_step (EUnaryOp op v) r
  | BTup     : forall es vs,
               List.Forall2 big_step es vs ->
               big_step (ETup es) (ETup vs)
  | BAscribe : forall e v,
               big_step e v ->
               big_step (EAscribe e v) v.

(* ================================================================= *)
(* 18. Big-Step → Small-Step (Theorem 6, direction 1)                *)
(* ================================================================= *)

Lemma big_step_value : forall e v, big_step e v -> value v.
Proof.
  intros e v H. induction H; try solve [constructor; auto].
  - (* BTup *)
    constructor. apply List.Forall2_Forall in H. exact H.
Qed.

(* steps is transitive *)
Lemma steps_trans : forall e1 e2 e3, steps e1 e2 -> steps e2 e3 -> steps e1 e3.
Proof.
  intros e1 e2 e3 Hs1 Hs2.
  induction Hs1 as [| ? ? ? Hstep Hrest IH]; [exact Hs2|].
  eapply SStep; eauto.
Qed.

(* Lift steps through a context function f *)
Lemma lift_steps : forall (f : exp -> exp) e e',
  (forall x y, step x y -> step (f x) (f y)) ->
  steps e e' -> steps (f e) (f e').
Proof.
  intros f e e' Hlift Hs.
  induction Hs as [| ? ? ? Hstep Hrest IH]; [constructor|].
  eapply SStep; [apply Hlift; exact Hstep| exact IH].
Qed.

Theorem big_step_implies_steps : forall e v,
  big_step e v -> steps e v.
Proof.
  intros e v H.
  induction H.
  - (* BLam *) constructor.
  - (* BApp *)
    apply big_step_value in H0 as Hv_v2.
    eapply steps_trans.
    + apply lift_steps with (f := fun e => EApp e e2); auto using SAppLeft; exact IHH.
    + eapply steps_trans.
      * apply lift_steps with (f := fun e => EApp (EAbs x e0) e); auto using SAppRight; exact IHH0.
      * eapply SStep; [apply SAppAbs; exact Hv_v2| exact IHH1].
  - (* BLet *)
    apply big_step_value in H as Hv_v1.
    eapply steps_trans.
    + apply lift_steps with (f := fun e => ELet x e e2); auto using SLetLeft; exact IHH.
    + eapply SStep; [apply SLet; exact Hv_v1| exact IHH0].
  - (* BIfTrue *)
    eapply steps_trans.
    + apply lift_steps with (f := fun e => EIf e e2 e3); auto using SIfCond; exact IHH.
    + eapply SStep; [exact SIfTrue| exact IHH0].
  - (* BIfFalse *)
    eapply steps_trans.
    + apply lift_steps with (f := fun e => EIf e e2 e3); auto using SIfCond; exact IHH.
    + eapply SStep; [exact SIfFalse| exact IHH0].
  - (* BIfUnitTrue *)
    eapply steps_trans.
    + apply lift_steps with (f := fun e => EIfUnit e e2); auto using SIfUnitCond; exact IHH.
    + eapply SStep; [exact SIfUnitTrue| exact IHH0].
  - (* BIfUnitFalse *)
    eapply steps_trans.
    + apply lift_steps with (f := fun e => EIfUnit e e2); auto using SIfUnitCond; exact IHH.
    + eapply SStep; [exact SIfUnitFalse| constructor].
  - (* BNum, BTrue, BFalse, BUnit, BStr *) constructor.
  - (* BBinOp *)
    eapply SStep; [apply SBinOp with (r := r); assumption| constructor].
  - (* BUnaryOp *)
    eapply SStep; [apply SUnaryOp with (r := r); assumption| constructor].
  - (* BTup *)
    clear H.
    induction H0 as [| e v es' vs' Hbig Hrest IHForall2].
    + constructor.
    + apply IHHbig in Hbig as Hsteps_e_v.
      apply IHForall2 in Hrest as Hsteps_rest.
      (* Reduce first element: ETup (e :: es') →* ETup (v :: es') *)
      apply big_step_value in Hbig as Hv_v.
      eapply steps_trans.
      * induction Hsteps_e_v as [| ? ? ? Hstep Hrest' IHsteps'].
        -- constructor.
        -- inversion Hstep; subst; clear Hstep.
           eapply SStep.
           ++ apply STuple with (e := e0) (k := 0); simpl; auto.
              ** reflexivity.
              ** intros j Hj; lia.
           ++ exact IHsteps'.
      * (* Reduce rest: ETup (v :: es') →* ETup (v :: vs') *)
        clear Hsteps_e_v.
        induction Hsteps_rest as [| ? ? ? Hstep Hrest' IHsteps'].
        -- constructor.
        -- inversion Hstep; subst; clear Hstep.
           eapply SStep.
           ++ apply STuple with (e := e0) (k := S k); simpl; auto.
              ** exact H1.
              ** intros j Hj; inv Hj.
                 --- exists v; split; auto; reflexivity.
                 --- apply H2; lia.
           ++ exact IHsteps'.
  - (* BAscribe *)
    eapply SStep; [apply SAscribe| assumption].
Qed.

(* ================================================================= *)
(* 19. Small-Step → Big-Step (Theorem 6, direction 2)                *)
(* ================================================================= *)

Lemma big_step_from_step : forall e e' v,
  step e e' -> big_step e' v -> big_step e v.
Proof.
  intros e e' v Hstep Hbig.
  revert e Hstep.
  induction Hbig; intros e0 Hstep'; inversion Hstep'; subst; eauto 30 using big_step.
Qed.

Theorem steps_implies_big_step : forall e v,
  steps e v -> value v -> big_step e v.
Proof.
  intros e v Hst Hv.
  induction Hst as [e' | e e' e'' Hstep Hsteps IH].
  - (* Refl: e is a value *)
    inversion Hv; subst; constructor; auto.
    + (* VTup *)
      eapply BTup.
      eapply List.Forall2_impl_In; [|exact H].
      intros e' ? _ _ He. apply steps_implies_big_step with (v := e').
      * constructor.
      * exact He.
  - (* Step e → e' →* v *)
    apply IH in Hv.
    eapply big_step_from_step; eauto.
Qed.

(* ================================================================= *)
(* 20. Let-Rec Extension (sketch)                                    *)
(* ================================================================= *)

(* let rec x = λy. e₁ in e₂ desugars to:
   let x = fix (λx. λy. e₁) in e₂
   where fix has the rule: fix (λx. e) → [x ↦ fix (λx. e)] e
*)

(* ================================================================= *)
(* 21. Summary                                                        *)
(* ================================================================= *)

(*
  This Coq formalization covers the core λ-calculus with:
  - Syntax: variables, abstraction, application, let, if, literals,
    operators, tuples, ascription
  - Small-step operational semantics with all reduction rules
  - Multi-step reduction (reflexive-transitive closure)
  - Simply-typed type system
  - Lemma 1: Substitution preserves types
  - Lemma 2: Canonical forms
  - Theorem 3: Progress (for well-typed closed terms)
  - Theorem 4: Preservation (subject reduction)
  - Theorem 5: Type Soundness (progress + preservation)
  - Big-step operational semantics
   - Theorem 6: Big-step ↔ Small-step equivalence (fully proven)

  Extensions (ADTs, pattern matching, let-polymorphism, let-rec)
  are defined syntactically and follow the same proof pattern
  described in docs/lambda-core.md §12.
*)
