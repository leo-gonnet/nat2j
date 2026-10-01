package dev.lgonnet.nat2j.support;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryJavaCompiler {

    private final int releaseLevel;

    public InMemoryJavaCompiler(int releaseLevel) {
        this.releaseLevel = releaseLevel;
    }

    public ClassLoader compile(String className, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("no compiler, tests need a JDK not a JRE");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null, null);
        ByteArrayFileManager fileManager = new ByteArrayFileManager(standard);

        List<String> options = new ArrayList<>(List.of("--release", String.valueOf(releaseLevel)));
        options.add("-Xlint:all");
        options.add("-Werror");

        boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null,
                List.of(new StringSource(className, source))).call();

        if (!ok) {
            throw new CompilationFailed(describe(diagnostics), source);
        }
        return fileManager.loader();
    }

    private String describe(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            sb.append(d.getKind()).append(" at line ").append(d.getLineNumber())
                    .append(": ").append(d.getMessage(null)).append('\n');
        }
        return sb.toString();
    }

    public static final class CompilationFailed extends RuntimeException {

        public CompilationFailed(String diagnostics, String source) {
            super("generated code did not compile:\n" + diagnostics
                    + "\n--- generated source ---\n" + withLineNumbers(source));
        }

        private static String withLineNumbers(String source) {
            String[] lines = source.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                sb.append(String.format("%4d | %s%n", i + 1, lines[i]));
            }
            return sb.toString();
        }
    }

    private static final class StringSource extends SimpleJavaFileObject {

        private final String code;

        StringSource(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static final class ClassBytes extends SimpleJavaFileObject {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        ClassBytes(String className) {
            super(URI.create("bytes:///" + className.replace('.', '/') + Kind.CLASS.extension),
                    Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }

    private static final class ByteArrayFileManager
            extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final Map<String, ClassBytes> classes = new HashMap<>();

        ByteArrayFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) {
            ClassBytes out = new ClassBytes(className);
            classes.put(className, out);
            return out;
        }

        ClassLoader loader() {
            Map<String, byte[]> compiled = new HashMap<>();
            classes.forEach((name, obj) -> compiled.put(name, obj.toByteArray()));
            return new ClassLoader(InMemoryJavaCompiler.class.getClassLoader()) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    byte[] bytes = compiled.get(name);
                    if (bytes == null) {
                        throw new ClassNotFoundException(name);
                    }
                    return defineClass(name, bytes, 0, bytes.length);
                }
            };
        }
    }
}
