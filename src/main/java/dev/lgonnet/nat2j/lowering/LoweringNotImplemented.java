package dev.lgonnet.nat2j.lowering;

public final class LoweringNotImplemented extends RuntimeException {

    public LoweringNotImplemented(String construct) {
        super("lowering not implemented for: " + construct);
    }
}
