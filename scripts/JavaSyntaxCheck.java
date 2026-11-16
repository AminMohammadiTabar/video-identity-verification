import com.sun.source.util.JavacTask;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parses Java sources without loading Spring dependencies or performing type-checking. */
public class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        List<Path> sources = new ArrayList<>();
        for (String service : List.of("auth", "dms", "workflow")) {
            try (var walk = Files.walk(Path.of(service, "src"))) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
            }
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK (javac), not a JRE, required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var units = manager.getJavaFileObjectsFromPaths(sources);
            // JDK 21 can parse the syntax used here; project target remains JDK 25.
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "21"), null, units);
            task.parse();
        }
        long errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
        diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .forEach(d -> System.err.println(d.getSource() + ":" + d.getLineNumber() + ": " + d.getMessage(null)));
        System.out.println("Java parse (no type-check or dependency resolution): "
                + sources.size() + " sources; " + errors + " syntax errors");
        if (errors > 0) System.exit(1);
    }
}
