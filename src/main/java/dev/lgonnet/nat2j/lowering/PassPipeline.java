package dev.lgonnet.nat2j.lowering;

import dev.lgonnet.nat2j.ir.Ir;

import java.util.List;

public final class PassPipeline {

    public static final List<LoweringPass> DEFAULT = List.of(
            new UnusedVariableElimination()
    );

    private final List<LoweringPass> passes;

    public PassPipeline(List<LoweringPass> passes) {
        this.passes = List.copyOf(passes);
    }

    public static PassPipeline defaults() {
        return new PassPipeline(DEFAULT);
    }

    public Ir.Program run(Ir.Program program) {
        Ir.Program current = program;
        for (LoweringPass pass : passes) {
            current = pass.apply(current);
        }
        return current;
    }
}
