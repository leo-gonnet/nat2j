package dev.lgonnet.nat2j.ir;

import dev.lgonnet.nat2j.types.NaturalType;

import java.util.List;

public final class Ir {

    public static final NaturalType LOGICAL = new NaturalType(NaturalType.Kind.L, -1, 0);

    private Ir() {
    }

    public record Program(String name, List<Var> vars, List<Stmt> body, List<Routine> routines) {
    }

    public record Var(String naturalName, String javaName, NaturalType type) {
    }

    public record Routine(String naturalName, String javaName, List<Stmt> body) {
    }

    public sealed interface Stmt {
    }

    public record Assign(Ref target, Expr value) implements Stmt {
    }

    public record Emit(List<Expr> operands) implements Stmt {
    }

    public record Branch(Expr condition, List<Stmt> whenTrue, List<Stmt> whenFalse) implements Stmt {
    }

    public record CountedLoop(Ref var, Expr from, Expr to, Expr step, List<Stmt> body) implements Stmt {
    }

    public record PostTestLoop(List<Stmt> body, Expr until) implements Stmt {
    }

    public record Call(String javaName) implements Stmt {
    }

    public sealed interface Expr {
        NaturalType type();
    }

    public record Ref(String javaName, NaturalType type) implements Expr {
    }

    public record Const(Object value, NaturalType type) implements Expr {
    }

    public record Arith(Op op, Expr left, Expr right, NaturalType type) implements Expr {
    }

    public record Compare(Cmp op, Expr left, Expr right) implements Expr {
        @Override
        public NaturalType type() {
            return LOGICAL;
        }
    }

    public record Logic(LogicOp op, List<Expr> operands) implements Expr {
        @Override
        public NaturalType type() {
            return LOGICAL;
        }
    }

    public enum Op { ADD, SUB, MUL, DIV, NEG }

    public enum Cmp { EQ, NE, LT, LE, GT, GE }

    public enum LogicOp { AND, OR, NOT }
}
