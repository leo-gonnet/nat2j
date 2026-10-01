package dev.lgonnet.nat2j.support;

import com.squareup.javapoet.JavaFile;
import dev.lgonnet.nat2j.Nat2J;
import dev.lgonnet.nat2j.backend.JavaEmitter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class TranspileAndRun {

    private TranspileAndRun() {
    }

    public static Result of(String naturalSource, String programName, int releaseLevel) {
        JavaFile javaFile = new Nat2J().emit(naturalSource, programName);
        String source = javaFile.toString();
        String className = JavaEmitter.PACKAGE + "." + javaFile.typeSpec.name;

        ClassLoader loader = new InMemoryJavaCompiler(releaseLevel).compile(className, source);
        StringWriter captured = new StringWriter();
        try (PrintWriter out = new PrintWriter(captured)) {
            Class<?> generated = loader.loadClass(className);
            Object instance = generated.getDeclaredConstructor().newInstance();
            Method run = generated.getMethod("run", PrintWriter.class);
            run.invoke(instance, out);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("generated program blew up at run time", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not run " + className, e);
        }
        return new Result(source, captured.toString());
    }

    public static Result of(String naturalSource, String programName) {
        return of(naturalSource, programName, 17);
    }

    public record Result(String javaSource, String output) {

        // A padded A12 field really does print its padding, so the generated code keeps it
        // and the comparison drops it. Expected-output files ending in spaces do not survive
        // normal editing.
        public String[] linesWithoutTrailingBlanks() {
            String[] lines = output.split("\\R");
            String[] out = new String[lines.length];
            for (int i = 0; i < lines.length; i++) {
                out[i] = lines[i].replaceAll("\\s+$", "");
            }
            return out;
        }

        public String trimmed() {
            return String.join("\n", linesWithoutTrailingBlanks()).strip();
        }
    }
}
