package io.github.chronicle.core.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioContentToolTest {
    @TempDir Path directory;
    private static ScenarioContent content() { return new ScenarioContent("quiet_below:scenario", java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()); }
    @Test void validatesAndAtomicallyImportsCanonicalBundle() throws Exception {
        Path input = directory.resolve("export.json").toAbsolutePath(); Files.writeString(input, ScenarioContentJson.encode(content()));
        Path output = directory.resolve("resources/data/quiet_below/chronicle/scenario-content.json").toAbsolutePath();
        assertEquals(content(), ScenarioContentTool.validate(input)); ScenarioContentTool.importFile(input, output, "quiet_below:scenario");
        assertEquals(content(), ScenarioContentJson.decode(Files.readString(output)));
    }
    @Test void refusesMalformedInputAndInvalidExistingOutputWithoutWriting() throws Exception {
        Path input = directory.resolve("export.json").toAbsolutePath(); Files.write(input, new byte[] {(byte) 0xc3, (byte) 0x28});
        assertThrows(IOException.class, () -> ScenarioContentTool.validate(input));
        Path valid = directory.resolve("valid.json").toAbsolutePath(); Files.writeString(valid, ScenarioContentJson.encode(content())); Path output = directory.resolve("resource.json").toAbsolutePath(); Files.writeString(output, "not scenario JSON");
        assertThrows(IOException.class, () -> ScenarioContentTool.importFile(valid, output, "quiet_below:scenario")); assertEquals("not scenario JSON", Files.readString(output));
    }
    @Test void refusesAnExportForAnotherScenarioBeforeCreatingOutput() throws Exception {
        Path input = directory.resolve("export.json").toAbsolutePath(); Files.writeString(input, ScenarioContentJson.encode(content())); Path output = directory.resolve("resource.json").toAbsolutePath();
        assertThrows(IOException.class, () -> ScenarioContentTool.importFile(input, output, "other:scenario")); assertFalse(Files.exists(output));
    }
    @Test void requiresAbsoluteCliPaths() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioContentTool.main(new String[] {"validate", "relative.json"}));
    }
}
