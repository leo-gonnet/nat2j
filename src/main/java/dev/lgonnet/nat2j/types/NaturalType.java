package dev.lgonnet.nat2j.types;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record NaturalType(Kind kind, int length, int scale) {

    public enum Kind { A, N, P, I, B, F, L, D, T }

    public static NaturalType parse(String base, Integer fractionalDigits) {
        if (base == null || base.isEmpty()) {
            throw new IllegalArgumentException("empty format");
        }
        String upper = base.toUpperCase();
        Kind kind;
        try {
            kind = Kind.valueOf(upper.substring(0, 1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("not a Natural format letter: " + upper.charAt(0));
        }
        String digits = upper.substring(1);
        int length = digits.isEmpty() ? -1 : Integer.parseInt(digits);
        int scale = fractionalDigits == null ? 0 : fractionalDigits;

        switch (kind) {
            case L, D, T -> {
                if (length != -1 || scale != 0) {
                    throw new IllegalArgumentException(kind + " takes no length or scale: " + upper);
                }
            }
            case I -> {
                if (length != 1 && length != 2 && length != 4) {
                    throw new IllegalArgumentException("I must be I1, I2 or I4: " + upper);
                }
            }
            case F -> {
                if (length != 4 && length != 8) {
                    throw new IllegalArgumentException("F must be F4 or F8: " + upper);
                }
            }
            case A, B -> {
                if (length < 1) {
                    throw new IllegalArgumentException(kind + " needs a length: " + upper);
                }
                if (scale != 0) {
                    throw new IllegalArgumentException(kind + " takes no scale: " + upper);
                }
            }
            case N, P -> {
                if (length < 1) {
                    throw new IllegalArgumentException(kind + " needs a length: " + upper);
                }
            }
        }
        return new NaturalType(kind, length, scale);
    }

    public static NaturalType parse(String format) {
        int separator = Math.max(format.indexOf('.'), format.indexOf(','));
        if (separator < 0) {
            return parse(format, null);
        }
        return parse(format.substring(0, separator),
                Integer.parseInt(format.substring(separator + 1)));
    }

    public TypeName javaType() {
        return switch (kind) {
            case A -> ClassName.get(String.class);
            // N and P are decimal, so BigDecimal and never double: an N7.2 field
            // holds exactly two fractional digits and 0.1 + 0.2 has to be 0.30.
            // Scale 0 included, so there is only one numeric type in the IR.
            case N, P -> ClassName.get(BigDecimal.class);
            case I -> switch (length) {
                case 1 -> TypeName.BYTE;
                case 2 -> TypeName.SHORT;
                default -> TypeName.INT;
            };
            case B -> ArrayTypeName.of(TypeName.BYTE);
            case F -> length == 4 ? TypeName.FLOAT : TypeName.DOUBLE;
            case L -> TypeName.BOOLEAN;
            case D -> ClassName.get(LocalDate.class);
            case T -> ClassName.get(LocalDateTime.class);
        };
    }

    public boolean isNumeric() {
        return kind == Kind.N || kind == Kind.P || kind == Kind.I || kind == Kind.F;
    }

    public boolean isDecimal() {
        return kind == Kind.N || kind == Kind.P;
    }

    @Override
    public String toString() {
        if (length < 0) {
            return kind.name();
        }
        return scale > 0 ? kind + "" + length + "." + scale : kind + "" + length;
    }
}
