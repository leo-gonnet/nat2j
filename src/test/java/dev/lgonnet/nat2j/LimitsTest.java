package dev.lgonnet.nat2j;

import dev.lgonnet.nat2j.lowering.LoweringNotImplemented;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LimitsTest {

    @Test
    @DisplayName("a group structure is rejected instead of half translated")
    void groupStructuresAreRejected() {
        assertThrows(LoweringNotImplemented.class, () -> transpile("""
                DEFINE DATA LOCAL
                1 #GROUP (A4)
                2 #PART  (A2)
                END-DEFINE
                END
                """));
    }

    @Test
    void dateFormatsAreRejected() {
        assertThrows(LoweringNotImplemented.class, () -> transpile("""
                DEFINE DATA LOCAL
                1 #WHEN (D)
                END-DEFINE
                WRITE #WHEN
                END
                """));
    }

    @Test
    @DisplayName("moving text into a number is an error, not a guess")
    void crossFormatMoveIsRejected() {
        assertThrows(IllegalStateException.class, () -> transpile("""
                DEFINE DATA LOCAL
                1 #N (N5,2)
                END-DEFINE
                MOVE 'abc' TO #N
                END
                """));
    }

    @Test
    void unknownSubroutineIsRejected() {
        assertThrows(IllegalStateException.class, () -> transpile("""
                DEFINE DATA LOCAL
                1 #N (I4)
                END-DEFINE
                PERFORM NOT-DEFINED
                END
                """));
    }

    @Test
    void undeclaredFieldIsRejected() {
        assertThrows(IllegalStateException.class, () -> transpile("""
                DEFINE DATA LOCAL
                1 #A (A4)
                END-DEFINE
                MOVE 'x' TO #B
                END
                """));
    }

    @Test
    void arithmeticOnTextIsRejected() {
        assertThrows(IllegalStateException.class, () -> transpile("""
                DEFINE DATA LOCAL
                1 #A (A4)
                1 #N (N5,2)
                END-DEFINE
                COMPUTE #N = #A * 2
                END
                """));
    }

    @Test
    void syntaxErrorsStopThePipeline() {
        assertThrows(Nat2J.SyntaxErrors.class, () -> transpile("""
                DEFINE DATA LOCAL
                1 #A (A4
                END-DEFINE
                END
                """));
    }

    private void transpile(String source) {
        new Nat2J().transpile(source, "probe");
    }
}
