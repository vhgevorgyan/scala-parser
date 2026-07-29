Require Import String List Bool Arith.

Inductive exp : Type :=
  | EVar : string -> exp
with ty : Type :=
  | TInt : ty
  | TBool : ty
  | TFun : ty -> ty -> ty
  | TTup : list ty -> ty
  | TConst : string -> list ty -> ty
  | TDynamic : ty.

Inductive prec : ty -> ty -> Prop :=
  | PDynTop : forall t, prec t TDynamic
  | PRefl : forall t, prec t t
  | PFun : forall t1 t1' t2 t2',
            prec t1' t1 -> prec t2 t2' ->
            prec (TFun t1 t2) (TFun t1' t2')
  | PTup : forall ts ts',
            List.Forall2 prec ts ts' ->
            prec (TTup ts) (TTup ts')
  | PConst : forall s ts ts',
              List.Forall2 prec ts ts' ->
              prec (TConst s ts) (TConst s ts').

Lemma prec_trans : forall t1 t2 t3,
  prec t1 t2 -> prec t2 t3 -> prec t1 t3.
Proof.
  intro t1; induction t2 using ty_ind; intros t3 H12 H23.
  all: try (inversion H12; subst; inversion H23; subst; try discriminate; auto).
  - (* TFun *)
    inversion H12 as [| | X X' Y Y' Hprec1 Hprec2| |]; subst.
    inversion H23 as [| | X'' X''' Y'' Y''' Hprec1' Hprec2'| |]; subst.
    all: try (apply PDynTop).
    2: { apply PFun; auto. }
    2: { discriminate. }
    2: { discriminate. }
    eapply PFun.
    * apply (IHt2_1 X''' X Hprec1' Hprec1).
    * apply (IHt2_2 Y Y''' Hprec2 Hprec2').
  - (* TTup *)
    inversion H12 as [| | | ts1 ts2 Hprec12|]; subst.
    inversion H23 as [| | | ts3 ts4 Hprec23|]; subst.
    all: try (apply PDynTop).
    2: { apply PTup; exact Hprec12. }
    2: { discriminate. }
    apply PTup.
    induction Hprec12 as [|x y xs ys Hprec Hrest IHrest] in ts4, Hprec23 |- *.
    * inversion Hprec23; subst; constructor.
    * inversion Hprec23 as [|x' y' xs' ys' Hprec' Hrest'|]; subst.
      constructor.
      ** apply (IHt2 x y Hprec Hprec').
      ** apply IHrest; exact Hrest'.
  - (* TConst *)
    inversion H12 as [| | | | s1 s2 ts1 ts2 Hprec12|]; subst.
    inversion H23 as [| | | | s3 s4 ts3 ts4 Hprec23|]; subst.
    all: try (apply PDynTop).
    2: { apply PConst; auto. }
    3: { discriminate. }
    4: { discriminate. }
    apply PConst; auto.
    induction Hprec12 as [|x y xs ys Hprec Hrest IHrest] in ts4, Hprec23 |- *.
    * inversion Hprec23; subst; constructor.
    * inversion Hprec23 as [|x' y' xs' ys' Hprec' Hrest'|]; subst.
      constructor.
      ** apply (IHt2 x y Hprec Hprec').
      ** apply IHrest; exact Hrest'.
Qed.
