package dev.lgonnet.nat2j;

import com.squareup.javapoet.JavaFile;
import dev.lgonnet.nat2j.ast.Ast;
import dev.lgonnet.nat2j.backend.JavaEmitter;
import dev.lgonnet.nat2j.frontend.AstBuilder;
import dev.lgonnet.nat2j.ir.Ir;
import dev.lgonnet.nat2j.lowering.AstToIr;
import dev.lgonnet.nat2j.lowering.PassPipeline;
import dev.lgonnet.nat2j.parser.NaturalLexer;
import dev.lgonnet.nat2j.parser.NaturalParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Nat2J {

    private final PassPipeline passes;

    public Nat2J() {
        this(PassPipeline.defaults());
    }

    public Nat2J(PassPipeline passes) {
        this.passes = passes;
    }

    public String transpile(String source, String programName) {
        return emit(source, programName).toString();
    }

    public JavaFile emit(String source, String programName) {
        return new JavaEmitter().emit(toIr(source, programName));
    }

    public Ir.Program toIr(String source, String programName) {
        return passes.run(new AstToIr().lower(toAst(source, programName)));
    }

    public Ast.Program toAst(String source, String programName) {
        NaturalLexer lexer = new NaturalLexer(CharStreams.fromString(source));
        NaturalParser parser = new NaturalParser(new CommonTokenStream(lexer));

        CollectingErrorListener errors = new CollectingErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        NaturalParser.ProgramContext tree = parser.program();
        errors.throwIfAnythingFailedToParse(programName);

        return new AstBuilder(programName).build(tree);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: nat2j <source.nsn> [output-dir]");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        String name = input.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        JavaFile java = new Nat2J().emit(Files.readString(input, StandardCharsets.UTF_8), name);
        if (args.length > 1) {
            java.writeTo(Path.of(args[1]));
            System.err.println("wrote " + JavaEmitter.PACKAGE + "." + java.typeSpec.name);
        } else {
            System.out.println(java);
        }
    }

    static final class CollectingErrorListener extends BaseErrorListener {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
            messages.add("line " + line + ":" + charPositionInLine + " " + msg);
        }

        void throwIfAnythingFailedToParse(String programName) {
            if (!messages.isEmpty()) {
                throw new SyntaxErrors(programName, List.copyOf(messages));
            }
        }
    }

    public static final class SyntaxErrors extends RuntimeException {

        private final List<String> messages;

        SyntaxErrors(String programName, List<String> messages) {
            super(messages.size() + " syntax error(s) in " + programName + ":\n  "
                    + String.join("\n  ", messages));
            this.messages = messages;
        }

        public List<String> messages() {
            return messages;
        }
    }
}
