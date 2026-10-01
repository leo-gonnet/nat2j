package dev.lgonnet.nat2j.lowering;

import dev.lgonnet.nat2j.ir.Ir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UnusedVariableElimination implements LoweringPass {

    @Override
    public String name() {
        return "unused-variable-elimination";
    }

    @Override
    public Ir.Program apply(Ir.Program program) {
        Set<String> referenced = new HashSet<>();
        collectReferencedNames(program.body(), referenced);
        for (Ir.Routine routine : program.routines()) {
            collectReferencedNames(routine.body(), referenced);
        }

        List<Ir.Var> kept = new ArrayList<>();
        for (Ir.Var var : program.vars()) {
            if (referenced.contains(var.javaName())) {
                kept.add(var);
            }
        }
        if (kept.size() == program.vars().size()) {
            return program;
        }
        return new Ir.Program(program.name(), List.copyOf(kept), program.body(), program.routines());
    }

    private void collectReferencedNames(List<Ir.Stmt> stmts, Set<String> out) {
        for (Ir.Stmt stmt : stmts) {
            switch (stmt) {
                case Ir.Assign a -> {
                    out.add(a.target().javaName());
                    collectReferencedNames(a.value(), out);
                }
                case Ir.Emit e -> e.operands().forEach(operand -> collectReferencedNames(operand, out));
                case Ir.Branch b -> {
                    collectReferencedNames(b.condition(), out);
                    collectReferencedNames(b.whenTrue(), out);
                    collectReferencedNames(b.whenFalse(), out);
                }
                case Ir.CountedLoop l -> {
                    out.add(l.var().javaName());
                    collectReferencedNames(l.from(), out);
                    collectReferencedNames(l.to(), out);
                    if (l.step() != null) {
                        collectReferencedNames(l.step(), out);
                    }
                    collectReferencedNames(l.body(), out);
                }
                case Ir.PostTestLoop l -> {
                    collectReferencedNames(l.body(), out);
                    collectReferencedNames(l.until(), out);
                }
                case Ir.Call ignored -> {
                }
            }
        }
    }

    private void collectReferencedNames(Ir.Expr expr, Set<String> out) {
        switch (expr) {
            case Ir.Ref r -> out.add(r.javaName());
            case Ir.Const ignored -> {
            }
            case Ir.Arith a -> {
                collectReferencedNames(a.left(), out);
                if (a.right() != null) {
                    collectReferencedNames(a.right(), out);
                }
            }
            case Ir.Compare c -> {
                collectReferencedNames(c.left(), out);
                collectReferencedNames(c.right(), out);
            }
            case Ir.Logic l -> l.operands().forEach(operand -> collectReferencedNames(operand, out));
        }
    }
}
