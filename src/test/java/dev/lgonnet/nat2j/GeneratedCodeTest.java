package dev.lgonnet.nat2j;

import dev.lgonnet.nat2j.support.TranspileAndRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCodeTest {

    private static final String SOURCE = """
            DEFINE DATA LOCAL
            1 #USED       (A6)
            1 #NEVER-READ (N5,2)
            END-DEFINE
            MOVE 'nat2j' TO #USED
            WRITE #USED
            END
            """;

    @Test
    @DisplayName("generated code still compiles at release 17 even though the tool needs 21")
    void generatedCodeCompilesAtJava17() {
        assertEquals("nat2j", TranspileAndRun.of(SOURCE, "release-check", 17).trimmed());
    }

    @Test
    @DisplayName("unused Natural fields do not reach the generated class")
    void unusedFieldsAreDropped() {
        String java = TranspileAndRun.of(SOURCE, "dead-field").javaSource();
        assertTrue(java.contains("used"));
        assertFalse(java.contains("neverRead"), () -> "unused field leaked:\n" + java);
    }

    @Test
    void classNameComesFromTheProgramName() {
        String java = TranspileAndRun.of(SOURCE, "order-report").javaSource();
        assertTrue(java.contains("class OrderReport"), () -> java);
    }

    @Test
    @DisplayName("an untouched A field prints blanks, not null")
    void alphanumericFieldsStartBlank() {
        String source = """
                DEFINE DATA LOCAL
                1 #EMPTY (A4)
                END-DEFINE
                WRITE #EMPTY '|'
                END
                """;
        assertEquals("|", TranspileAndRun.of(source, "blank-init").trimmed().strip());
    }
}
