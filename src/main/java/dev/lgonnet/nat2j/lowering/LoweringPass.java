package dev.lgonnet.nat2j.lowering;

import dev.lgonnet.nat2j.ir.Ir;

public interface LoweringPass {

    String name();

    Ir.Program apply(Ir.Program program);
}
