package dev.lgonnet.nat2j;

import dev.lgonnet.nat2j.support.TranspileAndRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    static Stream<String> fixtures() throws IOException {
        try (var paths = Files.list(FIXTURES)) {
            return paths.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".nsn"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("fixture transpiles, compiles, runs and prints what it should")
    @MethodSource("fixtures")
    void fixture(String name) {
        String source = read(FIXTURES.resolve(name + ".nsn"));
        String expected = read(FIXTURES.resolve(name + ".expected"));

        TranspileAndRun.Result result = TranspileAndRun.of(source, name);

        assertEquals(expected.strip(), result.trimmed(),
                () -> "wrong output for " + name + "\n--- generated Java ---\n"
                        + result.javaSource());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void everyFixtureHasAnExpectedFile(String name) {
        assertTrue(Files.exists(FIXTURES.resolve(name + ".expected")),
                name + ".expected is missing");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("missing fixture file: " + path, e);
        }
    }
}
