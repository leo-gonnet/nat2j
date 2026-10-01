package dev.lgonnet.nat2j.types;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalTypeTest {

    @ParameterizedTest
    @CsvSource({
            "A20, A, 20, 0",
            "N7,  N, 7,  0",
            "P9,  P, 9,  0",
            "I4,  I, 4,  0",
            "I1,  I, 1,  0",
            "F8,  F, 8,  0",
            "L,   L, -1, 0",
            "D,   D, -1, 0"
    })
    void parsesFormatsWithoutAScale(String format, String kind, int length, int scale) {
        NaturalType type = NaturalType.parse(format.trim());
        assertEquals(NaturalType.Kind.valueOf(kind), type.kind());
        assertEquals(length, type.length());
        assertEquals(scale, type.scale());
    }

    @Test
    @DisplayName("both '.' and ',' work as the decimal separator")
    void parsesEitherDecimalSeparator() {
        assertEquals(NaturalType.parse("N7.2"), NaturalType.parse("N7,2"));
        assertEquals(2, NaturalType.parse("N7,2").scale());
    }

    @Test
    @DisplayName("N and P become BigDecimal, never double")
    void decimalFormatsBecomeBigDecimal() {
        TypeName expected = ClassName.get(BigDecimal.class);
        assertEquals(expected, NaturalType.parse("N7.2").javaType());
        assertEquals(expected, NaturalType.parse("P9.3").javaType());
        assertEquals(expected, NaturalType.parse("N5").javaType());
        assertTrue(NaturalType.parse("N5").isDecimal());
    }

    @Test
    void binaryIntegersGetASizedPrimitive() {
        assertEquals(TypeName.BYTE, NaturalType.parse("I1").javaType());
        assertEquals(TypeName.SHORT, NaturalType.parse("I2").javaType());
        assertEquals(TypeName.INT, NaturalType.parse("I4").javaType());
    }

    @Test
    @DisplayName("an A field keeps its width, because assignment pads to it")
    void alphanumericKeepsItsWidth() {
        assertEquals(20, NaturalType.parse("A20").length());
        assertEquals(ClassName.get(String.class), NaturalType.parse("A20").javaType());
    }

    @ParameterizedTest
    @CsvSource({"I3", "F2", "L4", "A", "A4.2", "Z9"})
    void rejectsFormatsThatCannotExist(String format) {
        assertThrows(IllegalArgumentException.class, () -> NaturalType.parse(format));
    }

    @Test
    void roundTripsThroughToString() {
        for (String format : new String[]{"A20", "N7.2", "P9.3", "I4", "F8", "L"}) {
            assertEquals(format, NaturalType.parse(format).toString());
        }
    }
}
