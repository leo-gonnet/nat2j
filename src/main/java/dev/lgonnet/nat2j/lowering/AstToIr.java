package dev.lgonnet.nat2j.lowering;

import dev.lgonnet.nat2j.ast.Ast;
import dev.lgonnet.nat2j.ir.Ir;
import dev.lgonnet.nat2j.types.NaturalType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AstToIr {

    private static final NaturalType ONE = new NaturalType(NaturalType.Kind.N, 1, 0);

    private final Map<String, Ir.Var> scope = new LinkedHashMap<>();
    private final Map<String, String> routineNames = new LinkedHashMap<>();

    public Ir.Program lower(Ast.Program program) {
        scope.clear();
        routineNames.clear();

        for (Ast.Subroutine sub : program.subroutines()) {
            routineNames.put(sub.name().toUpperCase(), Names.toMethodName(sub.name()));
        }
        for (Ast.VarDecl decl : program.declarations()) {
            if (decl.level() != 1) {
                throw new LoweringNotImplemented("level " + decl.level() + " field "
                        + decl.name() + ", no group structures yet");
            }
            Ir.Var var = new Ir.Var(decl.name(), Names.toFieldName(decl.name()), decl.type());
            if (scope.put(decl.name().toUpperCase(), var) != null) {
                throw new IllegalStateException("field declared twice: " + decl.name());
            }
        }

        List<Ir.Stmt> body = block(program.body());
        List<Ir.Routine> routines = new ArrayList<>();
        for (Ast.Subroutine sub : program.subroutines()) {
            routines.add(new Ir.Routine(sub.name(), routineNames.get(sub.name().toUpperCase()),
                    block(sub.body())));
        }
        return new Ir.Program(program.name(), List.copyOf(scope.values()), body, List.copyOf(routines));
    }

    private List<Ir.Stmt> block(List<Ast.Stmt> stmts) {
        List<Ir.Stmt> out = new ArrayList<>();
        for (Ast.Stmt stmt : stmts) {
            out.add(statement(stmt));
        }
        return List.copyOf(out);
    }

    private Ir.Stmt statement(Ast.Stmt stmt) {
        return switch (stmt) {
            case Ast.Move m -> assignment(m.target(), m.source());
            case Ast.Compute c -> assignment(c.target(), c.value());
            case Ast.Assign a -> assignment(a.target(), a.value());

            case Ast.Write w -> {
                List<Ir.Expr> operands = new ArrayList<>();
                for (Ast.Expr operand : w.operands()) {
                    operands.add(expression(operand, null));
                }
                yield new Ir.Emit(List.copyOf(operands));
            }

            case Ast.If i -> new Ir.Branch(condition(i.condition()),
                    block(i.thenBody()), block(i.elseBody()));

            case Ast.For f -> {
                Ir.Ref loopVar = resolve(f.loopVar());
                Ir.Expr step = f.step() == null
                        ? new Ir.Const(BigDecimal.ONE, ONE)
                        : expression(f.step(), loopVar.type());
                yield new Ir.CountedLoop(loopVar,
                        expression(f.from(), loopVar.type()),
                        expression(f.to(), loopVar.type()),
                        step,
                        block(f.body()));
            }

            case Ast.Repeat r -> new Ir.PostTestLoop(block(r.body()), condition(r.until()));

            case Ast.Perform p -> {
                String javaName = routineNames.get(p.subroutine().toUpperCase());
                if (javaName == null) {
                    throw new IllegalStateException("no such subroutine: " + p.subroutine());
                }
                yield new Ir.Call(javaName);
            }
        };
    }

    private Ir.Assign assignment(Ast.VarRef target, Ast.Expr value) {
        Ir.Ref ref = resolve(target);
        return new Ir.Assign(ref, expression(value, ref.type()));
    }

    private Ir.Expr expression(Ast.Expr expr, NaturalType targetHint) {
        return switch (expr) {
            case Ast.VarRef v -> resolve(v);
            case Ast.StringLit s -> new Ir.Const(s.value(),
                    new NaturalType(NaturalType.Kind.A, Math.max(1, s.value().length()), 0));
            case Ast.NumberLit n -> new Ir.Const(n.value(), narrowestTypeHolding(n.value()));
            case Ast.BoolLit b -> new Ir.Const(b.value(), Ir.LOGICAL);

            case Ast.Binary b -> {
                Ir.Expr left = expression(b.left(), targetHint);
                Ir.Expr right = expression(b.right(), targetHint);
                yield new Ir.Arith(arithOp(b.op()), left, right,
                        arithResultType(b.op(), left.type(), right.type(), targetHint));
            }

            case Ast.Negate n -> {
                Ir.Expr inner = expression(n.operand(), targetHint);
                yield new Ir.Arith(Ir.Op.NEG, inner, null, asDecimal(inner.type()));
            }
        };
    }

    private Ir.Expr condition(Ast.Cond cond) {
        return switch (cond) {
            case Ast.Compare c -> new Ir.Compare(compareOp(c.op()),
                    expression(c.left(), null), expression(c.right(), null));
            case Ast.AndCond a -> new Ir.Logic(Ir.LogicOp.AND,
                    List.of(condition(a.left()), condition(a.right())));
            case Ast.OrCond o -> new Ir.Logic(Ir.LogicOp.OR,
                    List.of(condition(o.left()), condition(o.right())));
            case Ast.NotCond n -> new Ir.Logic(Ir.LogicOp.NOT, List.of(condition(n.inner())));
        };
    }

    private NaturalType arithResultType(Ast.Op op, NaturalType left, NaturalType right,
                                        NaturalType targetHint) {
        rejectNonNumeric(left);
        rejectNonNumeric(right);
        int leftDigits = integerDigits(left);
        int rightDigits = integerDigits(right);
        return switch (op) {
            case ADD, SUB -> decimal(Math.max(leftDigits, rightDigits) + 1,
                    Math.max(left.scale(), right.scale()));
            case MUL -> decimal(leftDigits + rightDigits, left.scale() + right.scale());
            // division cannot work its scale out from its operands, Natural takes it
            // from whatever you are assigning into
            case DIV -> decimal(Math.max(leftDigits, rightDigits),
                    targetHint != null && targetHint.isDecimal()
                            ? targetHint.scale()
                            : Math.max(left.scale(), right.scale()));
        };
    }

    private void rejectNonNumeric(NaturalType type) {
        if (!type.isNumeric()) {
            throw new IllegalStateException("cannot do arithmetic on a " + type + " field");
        }
    }

    private int integerDigits(NaturalType type) {
        return switch (type.kind()) {
            case N, P -> Math.max(1, type.length() - type.scale());
            case I -> switch (type.length()) {
                case 1 -> 3;
                case 2 -> 5;
                default -> 10;
            };
            case F -> 17;
            default -> throw new IllegalStateException("not a numeric format: " + type);
        };
    }

    private NaturalType asDecimal(NaturalType type) {
        return type.isDecimal() ? type : decimal(integerDigits(type), type.scale());
    }

    private NaturalType decimal(int integerDigits, int scale) {
        return new NaturalType(NaturalType.Kind.N, Math.max(1, integerDigits) + scale, scale);
    }

    private Ir.Op arithOp(Ast.Op op) {
        return switch (op) {
            case ADD -> Ir.Op.ADD;
            case SUB -> Ir.Op.SUB;
            case MUL -> Ir.Op.MUL;
            case DIV -> Ir.Op.DIV;
        };
    }

    private Ir.Cmp compareOp(Ast.Cmp op) {
        return switch (op) {
            case EQ -> Ir.Cmp.EQ;
            case NE -> Ir.Cmp.NE;
            case LT -> Ir.Cmp.LT;
            case LE -> Ir.Cmp.LE;
            case GT -> Ir.Cmp.GT;
            case GE -> Ir.Cmp.GE;
        };
    }

    private Ir.Ref resolve(Ast.VarRef varRef) {
        Ir.Var var = scope.get(varRef.name().toUpperCase());
        if (var == null) {
            throw new IllegalStateException("field not declared: " + varRef.name());
        }
        return new Ir.Ref(var.javaName(), var.type());
    }

    private NaturalType narrowestTypeHolding(BigDecimal value) {
        int scale = Math.max(value.scale(), 0);
        int integerDigits = Math.max(1, value.precision() - value.scale());
        return new NaturalType(NaturalType.Kind.N, integerDigits + scale, scale);
    }
}
