package dev.lgonnet.nat2j.frontend;

import dev.lgonnet.nat2j.ast.Ast;
import dev.lgonnet.nat2j.parser.NaturalParser;
import dev.lgonnet.nat2j.types.NaturalType;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class AstBuilder {

    private final String programName;

    public AstBuilder(String programName) {
        this.programName = programName;
    }

    public Ast.Program build(NaturalParser.ProgramContext ctx) {
        List<Ast.VarDecl> declarations = new ArrayList<>();
        if (ctx.defineData() != null) {
            for (NaturalParser.VariableDeclContext decl : ctx.defineData().variableDecl()) {
                declarations.add(varDecl(decl));
            }
        }
        List<Ast.Stmt> body = new ArrayList<>();
        List<Ast.Subroutine> subroutines = new ArrayList<>();
        hoistSubroutinesOutOfStatements(ctx.statement(), body, subroutines);
        return new Ast.Program(programName, List.copyOf(declarations), List.copyOf(body),
                List.copyOf(subroutines));
    }

    private Ast.VarDecl varDecl(NaturalParser.VariableDeclContext ctx) {
        NaturalParser.TypeSpecContext spec = ctx.typeSpec();
        Integer frac = spec.frac == null ? null : Integer.valueOf(spec.frac.getText());
        NaturalType type = NaturalType.parse(spec.base.getText(), frac);
        return new Ast.VarDecl(Integer.parseInt(ctx.level.getText()), ctx.name.getText(), type);
    }

    private void hoistSubroutinesOutOfStatements(List<NaturalParser.StatementContext> in,
                                                 List<Ast.Stmt> body,
                                                 List<Ast.Subroutine> subroutines) {
        for (NaturalParser.StatementContext s : in) {
            if (s.subroutineDef() != null) {
                NaturalParser.SubroutineDefContext sub = s.subroutineDef();
                List<Ast.Stmt> subBody = new ArrayList<>();
                hoistSubroutinesOutOfStatements(sub.statement(), subBody, subroutines);
                subroutines.add(new Ast.Subroutine(sub.IDENT().getText(), List.copyOf(subBody)));
            } else {
                body.add(statement(s));
            }
        }
    }

    private List<Ast.Stmt> block(List<NaturalParser.StatementContext> in) {
        List<Ast.Stmt> out = new ArrayList<>();
        for (NaturalParser.StatementContext s : in) {
            if (s.subroutineDef() != null) {
                throw new ParseFailure("DEFINE SUBROUTINE only works at the top level, line "
                        + s.getStart().getLine());
            }
            out.add(statement(s));
        }
        return List.copyOf(out);
    }

    private Ast.Stmt statement(NaturalParser.StatementContext ctx) {
        if (ctx.moveStmt() != null) {
            NaturalParser.MoveStmtContext c = ctx.moveStmt();
            return new Ast.Move(expr(c.expr()), varRef(c.varRef()));
        }
        if (ctx.computeStmt() != null) {
            NaturalParser.ComputeStmtContext c = ctx.computeStmt();
            return new Ast.Compute(varRef(c.varRef()), expr(c.expr()));
        }
        if (ctx.assignStmt() != null) {
            NaturalParser.AssignStmtContext c = ctx.assignStmt();
            return new Ast.Assign(varRef(c.varRef()), expr(c.expr()));
        }
        if (ctx.ifStmt() != null) {
            return ifStmt(ctx.ifStmt());
        }
        if (ctx.forStmt() != null) {
            NaturalParser.ForStmtContext c = ctx.forStmt();
            return new Ast.For(varRef(c.varRef()), expr(c.from), expr(c.to),
                    c.step == null ? null : expr(c.step), block(c.statement()));
        }
        if (ctx.repeatStmt() != null) {
            NaturalParser.RepeatStmtContext c = ctx.repeatStmt();
            return new Ast.Repeat(block(c.statement()), condition(c.condition()));
        }
        if (ctx.writeStmt() != null) {
            List<Ast.Expr> operands = new ArrayList<>();
            for (NaturalParser.OperandContext o : ctx.writeStmt().operand()) {
                operands.add(o.literal() != null ? literal(o.literal()) : varRef(o.varRef()));
            }
            return new Ast.Write(List.copyOf(operands));
        }
        if (ctx.performStmt() != null) {
            return new Ast.Perform(ctx.performStmt().IDENT().getText());
        }
        throw new ParseFailure("statement I do not handle, line " + ctx.getStart().getLine()
                + ": " + ctx.getText());
    }

    private Ast.If ifStmt(NaturalParser.IfStmtContext ctx) {
        TerminalNode elseToken = ctx.ELSE();
        List<NaturalParser.StatementContext> all = ctx.statement();
        if (elseToken == null) {
            return new Ast.If(condition(ctx.condition()), block(all), List.of());
        }
        return new Ast.If(condition(ctx.condition()),
                block(statementsBeforeToken(all, elseToken)),
                block(statementsAfterToken(all, elseToken)));
    }

    private List<NaturalParser.StatementContext> statementsBeforeToken(
            List<NaturalParser.StatementContext> all, TerminalNode token) {
        List<NaturalParser.StatementContext> out = new ArrayList<>();
        for (NaturalParser.StatementContext s : all) {
            if (s.getStart().getTokenIndex() < token.getSymbol().getTokenIndex()) {
                out.add(s);
            }
        }
        return out;
    }

    private List<NaturalParser.StatementContext> statementsAfterToken(
            List<NaturalParser.StatementContext> all, TerminalNode token) {
        List<NaturalParser.StatementContext> out = new ArrayList<>();
        for (NaturalParser.StatementContext s : all) {
            if (s.getStart().getTokenIndex() > token.getSymbol().getTokenIndex()) {
                out.add(s);
            }
        }
        return out;
    }

    private Ast.Cond condition(NaturalParser.ConditionContext ctx) {
        if (ctx instanceof NaturalParser.AndCondContext c) {
            return new Ast.AndCond(condition(c.condition(0)), condition(c.condition(1)));
        }
        if (ctx instanceof NaturalParser.OrCondContext c) {
            return new Ast.OrCond(condition(c.condition(0)), condition(c.condition(1)));
        }
        if (ctx instanceof NaturalParser.NotCondContext c) {
            return new Ast.NotCond(condition(c.condition()));
        }
        if (ctx instanceof NaturalParser.ParenCondContext c) {
            return condition(c.condition());
        }
        if (ctx instanceof NaturalParser.CompareCondContext c) {
            NaturalParser.ComparisonContext cmp = c.comparison();
            return new Ast.Compare(relop(cmp.relop()), expr(cmp.expr(0)), expr(cmp.expr(1)));
        }
        throw new ParseFailure("condition I do not handle: " + ctx.getText());
    }

    private Ast.Cmp relop(NaturalParser.RelopContext ctx) {
        String text = ctx.getText().toUpperCase();
        return switch (text) {
            case "=", "EQ" -> Ast.Cmp.EQ;
            case "<>", "!=", "NE" -> Ast.Cmp.NE;
            case ">=", "GE" -> Ast.Cmp.GE;
            case "<=", "LE" -> Ast.Cmp.LE;
            case ">", "GT" -> Ast.Cmp.GT;
            case "<", "LT" -> Ast.Cmp.LT;
            default -> throw new ParseFailure("unknown operator: " + text);
        };
    }

    private Ast.Expr expr(NaturalParser.ExprContext ctx) {
        if (ctx instanceof NaturalParser.MulDivContext c) {
            Ast.Op op = c.op.getText().equals("*") ? Ast.Op.MUL : Ast.Op.DIV;
            return new Ast.Binary(op, expr(c.expr(0)), expr(c.expr(1)));
        }
        if (ctx instanceof NaturalParser.AddSubContext c) {
            Ast.Op op = c.op.getText().equals("+") ? Ast.Op.ADD : Ast.Op.SUB;
            return new Ast.Binary(op, expr(c.expr(0)), expr(c.expr(1)));
        }
        if (ctx instanceof NaturalParser.NegExprContext c) {
            return new Ast.Negate(expr(c.expr()));
        }
        if (ctx instanceof NaturalParser.PrimaryExprContext c) {
            return primary(c.primary());
        }
        throw new ParseFailure("expression I do not handle: " + ctx.getText());
    }

    private Ast.Expr primary(NaturalParser.PrimaryContext ctx) {
        if (ctx instanceof NaturalParser.LiteralPrimaryContext c) {
            return literal(c.literal());
        }
        if (ctx instanceof NaturalParser.VarPrimaryContext c) {
            return varRef(c.varRef());
        }
        if (ctx instanceof NaturalParser.ParenPrimaryContext c) {
            return expr(c.expr());
        }
        throw new ParseFailure("primary I do not handle: " + ctx.getText());
    }

    private Ast.Expr literal(NaturalParser.LiteralContext ctx) {
        if (ctx instanceof NaturalParser.StringLiteralContext c) {
            return new Ast.StringLit(unquote(c.STRING().getText()));
        }
        if (ctx instanceof NaturalParser.DecimalLiteralContext c) {
            return new Ast.NumberLit(new BigDecimal(c.NUMBER().getText()));
        }
        if (ctx instanceof NaturalParser.IntLiteralContext c) {
            return new Ast.NumberLit(new BigDecimal(c.INT().getText()));
        }
        if (ctx instanceof NaturalParser.TrueLiteralContext) {
            return new Ast.BoolLit(true);
        }
        if (ctx instanceof NaturalParser.FalseLiteralContext) {
            return new Ast.BoolLit(false);
        }
        throw new ParseFailure("literal I do not handle: " + ctx.getText());
    }

    private String unquote(String raw) {
        return raw.substring(1, raw.length() - 1).replace("''", "'");
    }

    private Ast.VarRef varRef(NaturalParser.VarRefContext ctx) {
        return new Ast.VarRef(ctx.VAR().getText());
    }

    public static final class ParseFailure extends RuntimeException {
        public ParseFailure(String message) {
            super(message);
        }
    }
}
