/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.revelc.code.formatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.BeforeAll;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;

public abstract class AbstractFormatterTest {

    protected static final String RESOURCE_LOCATION_PRIMARY = "src/test/resources";
    protected static final String RESOURCE_LOCATION_SECONDARY = "target/testoutput";

    protected static final File TEST_OUTPUT_PRIMARY_DIR = new File("target/testoutput");
    protected static final File TEST_OUTPUT_SECONDARY_DIR = new File("target/testoutputrepeat");

    @BeforeAll
    public static void createTestDir() {
        assertTrue(TEST_OUTPUT_PRIMARY_DIR.mkdirs() || TEST_OUTPUT_PRIMARY_DIR.isDirectory());
        assertTrue(TEST_OUTPUT_SECONDARY_DIR.mkdirs() || TEST_OUTPUT_SECONDARY_DIR.isDirectory());
    }

    public static class TestConfigurationSource implements ConfigurationSource {

        private final File targetDir;

        public TestConfigurationSource(File targetDir) {
            this.targetDir = targetDir;
        }

        @Override
        public File getTargetDirectory() {
            return this.targetDir;
        }

        @Override
        public Log getLog() {
            return new SystemStreamLog();
        }

        @Override
        public Charset getEncoding() {
            return StandardCharsets.UTF_8;
        }

        @Override
        public String getCompilerSources() {
            return "1.8";
        }

        @Override
        public String getCompilerCompliance() {
            return "1.8";
        }

        @Override
        public String getCompilerCodegenTargetPlatform() {
            return "1.8";
        }
    }

    protected void doTestFormat(Formatter formatter, String fileUnderTest, String expectedSha512,
            FormatCycle formatCycle) throws IOException {
        if (System.lineSeparator().equals("\n")) {
            doTestFormat(Collections.emptyMap(), formatter, fileUnderTest, expectedSha512, LineEnding.LF, formatCycle);
        } else {
            doTestFormat(Collections.emptyMap(), formatter, fileUnderTest, expectedSha512, LineEnding.CRLF,
                    formatCycle);
        }
    }

    protected void doTestFormat(Map<String, String> options, Formatter formatter, String fileUnderTest,
            String expectedSha512, LineEnding lineEnding, FormatCycle formatCycle) throws IOException {

        // Set the used resource location for test (either first pass or second pass)
        String resourceLocation;
        if (FormatCycle.SECOND == formatCycle) {
            resourceLocation = RESOURCE_LOCATION_SECONDARY;
        } else {
            resourceLocation = RESOURCE_LOCATION_PRIMARY;
        }

        // Set the used output directory for test (either first pass or second pass)
        File testOutputDir;
        if (FormatCycle.SECOND == formatCycle) {
            testOutputDir = TEST_OUTPUT_SECONDARY_DIR;
        } else {
            testOutputDir = TEST_OUTPUT_PRIMARY_DIR;
        }

        // Set original file and file to use for test
        File originalSourceFile = new File(resourceLocation, fileUnderTest);
        File sourceFile = new File(testOutputDir, fileUnderTest);

        // Copy file to new location
        Files.copy(originalSourceFile, sourceFile);

        // Read file to be formatted
        String originalCode = FileUtils.fileRead(sourceFile, StandardCharsets.UTF_8.name());

        // Format the file and make sure formatting worked
        formatter.init(options, new TestConfigurationSource(testOutputDir));
        String formattedCode = formatter.formatFile(sourceFile, originalCode, lineEnding);
        assertNotNull(formattedCode);

        // Write the file we formatte4d
        FileUtils.fileWrite(sourceFile, StandardCharsets.UTF_8.name(), formattedCode);

        // Run assertions on formatted file, if not valid, reject and tester can look at resulting files to debug issue.
        if (FormatCycle.SECOND == formatCycle) {
            assertEquals(originalCode, formattedCode);
        } else {
            assertNotEquals(originalCode, formattedCode);
        }

        // We are hashing this as set in stone in case for some reason our source file changes unexpectedly.
        byte[] sha512 = Files.asByteSource(sourceFile).hash(Hashing.sha512()).asBytes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sha512.length; i++) {
            sb.append(Integer.toString((sha512[i] & 0xff) + 0x100, 16).substring(1));
        }

        assertEquals(expectedSha512, sb.toString());
    }

}
