package dev.lgonnet.nat2j.lowering;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.lang.model.SourceVersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamesTest {

    @ParameterizedTest
    @CsvSource({
            "#NAME,          name",
            "#TOTAL-DUE,     totalDue",
            "#A-B-C,         aBC",
            "#ORDER_COUNT,   orderCount",
            "#X1,            x1"
    })
    void manglesFieldNames(String natural, String expected) {
        assertEquals(expected, Names.toFieldName(natural));
    }

    @Test
    @DisplayName("a Java keyword gets a suffix, so the name still reads left to right")
    void avoidsJavaKeywords() {
        assertEquals("class_", Names.toFieldName("#CLASS"));
        assertEquals("int_", Names.toFieldName("#INT"));
        assertTrue(SourceVersion.isName(Names.toFieldName("#CLASS")));
    }

    @Test
    void buildsTypeNames() {
        assertEquals("OrderReport", Names.toTypeName("order-report"));
        assertEquals("Greeting", Names.toTypeName("greeting"));
    }
}
