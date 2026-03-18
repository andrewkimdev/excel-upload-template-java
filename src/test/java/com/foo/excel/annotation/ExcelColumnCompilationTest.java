package com.foo.excel.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelColumnCompilationTest {

  @TempDir Path tempDir;

  @Test
  void compile_missingLabel_fails() throws Exception {
    CompilationResult result =
        compile(
            "test.MissingLabelDto",
            """
            package test;

            import com.foo.excel.annotation.ExcelColumn;

            public class MissingLabelDto {
              @ExcelColumn(column = "A")
              private String value;
            }
            """);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessages()).anySatisfy(message -> assertThat(message).contains("label"));
  }

  @Test
  void compile_missingColumn_fails() throws Exception {
    CompilationResult result =
        compile(
            "test.MissingColumnDto",
            """
            package test;

            import com.foo.excel.annotation.ExcelColumn;

            public class MissingColumnDto {
              @ExcelColumn(label = "헤더")
              private String value;
            }
            """);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessages()).anySatisfy(message -> assertThat(message).contains("column"));
  }

  private CompilationResult compile(String className, String source) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("JDK compiler must be available for compilation tests").isNotNull();

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    Path outputDir = Files.createDirectories(tempDir.resolve(className.replace('.', '/')).getParent());

    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      List<String> options =
          List.of("-classpath", System.getProperty("java.class.path"), "-d", outputDir.toString());

      JavaFileObject sourceFile = new InMemoryJavaSource(className, source);
      boolean success =
          Boolean.TRUE.equals(
              compiler
                  .getTask(null, fileManager, diagnostics, options, null, List.of(sourceFile))
                  .call());

      List<String> errorMessages =
          diagnostics.getDiagnostics().stream()
              .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
              .map(diagnostic -> diagnostic.getMessage(null))
              .toList();

      return new CompilationResult(success, errorMessages);
    }
  }

  private record CompilationResult(boolean success, List<String> errorMessages) {}

  private static final class InMemoryJavaSource extends SimpleJavaFileObject {
    private final String source;

    private InMemoryJavaSource(String className, String source) {
      super(
          URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
          JavaFileObject.Kind.SOURCE);
      this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }
}
