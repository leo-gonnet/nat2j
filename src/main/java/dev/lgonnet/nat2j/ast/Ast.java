package dev.lgonnet.nat2j.ast;

import dev.lgonnet.nat2j.types.NaturalType;

import java.math.BigDecimal;
import java.util.List;

public final class Ast {

    private Ast() {
    }

    public sealed interface Node {
    }

    public record Program(String name,
                          List<VarDecl> declarations,
                          List<Stmt> body,
                          List<Subroutine> subroutines) implements Node {
    }

    public record VarDecl(int level, String name, NaturalType type) implements Node {
    }

    public record Subroutine(String name, List<Stmt> body) implements Node {
    }

    public sealed interface Stmt extends Node {
    }

    public record Move(Expr source, VarRef target) implements Stmt {
    }

    public record Compute(VarRef target, Expr value) implements Stmt {
    }

    public record Assign(VarRef target, Expr value) implements Stmt {
    }

    public record If(Cond condition, List<Stmt> thenBody, List<Stmt> elseBody) implements Stmt {
    }

    public record For(VarRef loopVar, Expr from, Expr to, Expr step, List<Stmt> body) implements Stmt {
    }

    public record Repeat(List<Stmt> body, Cond until) implements Stmt {
    }

    public record Write(List<Expr> operands) implements Stmt {
    }

    public record Perform(String subroutine) implements Stmt {
    }

    public sealed interface Expr extends Node {
    }

    public record VarRef(String name) implements Expr {
    }

    public record StringLit(String value) implements Expr {
    }

    public record NumberLit(BigDecimal value) implements Expr {
    }

    public record BoolLit(boolean value) implements Expr {
    }

    public record Binary(Op op, Expr left, Expr right) implements Expr {
    }

    public record Negate(Expr operand) implements Expr {
    }

    public enum Op { ADD, SUB, MUL, DIV }

    public sealed interface Cond extends Node {
    }

    public record Compare(Cmp op, Expr left, Expr right) implements Cond {
    }

    public record AndCond(Cond left, Cond right) implements Cond {
    }

    public record OrCond(Cond left, Cond right) implements Cond {
    }

    public record NotCond(Cond inner) implements Cond {
    }

    public enum Cmp { EQ, NE, LT, LE, GT, GE }
}
