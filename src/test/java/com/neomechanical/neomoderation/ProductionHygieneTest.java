package com.neomechanical.neomoderation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionHygieneTest {
    @Test
    void productionSourcesDoNotDumpStackTracesOrWrapGenericRuntimeExceptions() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            violations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(ProductionHygieneTest::scan)
                    .toList();
        }
        assertTrue(violations.isEmpty(), () -> "Production exception hygiene violations:\n" + String.join("\n", violations));
    }

    private static Stream<String> scan(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            return Stream.iterate(0, index -> index + 1)
                    .limit(lines.size())
                    .filter(index -> lines.get(index).contains(".printStackTrace()")
                            || lines.get(index).contains("throw new RuntimeException("))
                    .map(index -> path + ":" + (index + 1) + " " + lines.get(index).trim());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan " + path, e);
        }
    }
}
