package io.papermc.paper.plugin.provider.type;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PluginFileTypeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void salmonDescriptorSelectsSalmonTypeAheadOfPaperOrBukkitDescriptors() throws Exception {
        final Path pluginJar = this.temporaryDirectory.resolve("salmon-plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(pluginJar))) {
            output.putNextEntry(new JarEntry("salmon-plugin.yml"));
            output.write("name: TestSalmon\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("paper-plugin.yml"));
            output.write("name: TestPaper\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("plugin.yml"));
            output.write("name: TestBukkit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }

        try (JarFile jar = new JarFile(pluginJar.toFile())) {
            assertEquals(PluginFileType.SALMON, PluginFileType.guessType(jar));
        }
    }

    @Test
    void paperAndBukkitDescriptorsKeepTheirExistingTypes() throws Exception {
        final Path paperJar = this.temporaryDirectory.resolve("paper-plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(paperJar))) {
            output.putNextEntry(new JarEntry("paper-plugin.yml"));
            output.write("name: TestPaper\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (JarFile jar = new JarFile(paperJar.toFile())) {
            assertEquals(PluginFileType.PAPER, PluginFileType.guessType(jar));
        }

        final Path bukkitJar = this.temporaryDirectory.resolve("bukkit-plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(bukkitJar))) {
            output.putNextEntry(new JarEntry("plugin.yml"));
            output.write("name: TestBukkit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (JarFile jar = new JarFile(bukkitJar.toFile())) {
            assertEquals(PluginFileType.SPIGOT, PluginFileType.guessType(jar));
        }
    }
}
