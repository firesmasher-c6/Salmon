package dev.firesmasher.salmon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ModrinthService {
    private static final Logger LOGGER = Logger.getLogger("Salmon");
    private static final String API = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "Salmon/1.0 (https://github.com/firesmasher-c6/Salmon)";
    private static final long MAX_DOWNLOAD_BYTES = 100L * 1024L * 1024L;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    private final Path pluginDirectory;
    private final String gameVersion;
    private final List<String> loaders;

    public ModrinthService(final Path pluginDirectory, final String gameVersion) {
        this(pluginDirectory, gameVersion, List.of("paper", "spigot"));
    }

    private ModrinthService(final Path pluginDirectory, final String gameVersion, final List<String> loaders) {
        this.pluginDirectory = pluginDirectory.toAbsolutePath().normalize();
        this.gameVersion = gameVersion;
        this.loaders = loaders;
    }

    public List<Project> search(final String query) throws IOException, InterruptedException {
        if (query == null || query.isBlank() || query.length() > 128) {
            throw new IllegalArgumentException("search text must contain between 1 and 128 characters");
        }
        final String facets = URLEncoder.encode("[[\"project_type:plugin\"]]", StandardCharsets.UTF_8);
        final String url = API + "/search?query=" + encode(query.trim()) + "&limit=5&facets=" + facets;
        final JsonArray hits = getJson(url).getAsJsonArray("hits");
        final List<Project> results = new ArrayList<>();
        for (JsonElement element : hits) {
            final JsonObject hit = element.getAsJsonObject();
            results.add(new Project(
                value(hit, "project_id"),
                value(hit, "slug"),
                value(hit, "title"),
                optionalValue(hit, "description")
            ));
        }
        return List.copyOf(results);
    }

    public InstallResult install(final String slugOrId) throws IOException, InterruptedException {
        if (slugOrId == null || !slugOrId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("use a Modrinth project slug or ID");
        }
        final JsonObject projectJson = getJson(API + "/project/" + encode(slugOrId));
        if (!"plugin".equals(value(projectJson, "project_type"))) {
            throw new IllegalArgumentException("Modrinth project '" + slugOrId + "' is not a plugin");
        }
        final Project project = new Project(
            value(projectJson, "id"),
            value(projectJson, "slug"),
            value(projectJson, "title"),
            optionalValue(projectJson, "description")
        );
        final Set<String> visitedProjects = new HashSet<>();
        final List<Artifact> artifacts = new ArrayList<>();
        this.resolveProject(project, null, visitedProjects, artifacts);
        return this.downloadAndInstall(project, artifacts);
    }

    public InstallResult installChunky() throws IOException, InterruptedException {
        Files.createDirectories(this.pluginDirectory);
        for (Path pluginJar : this.listPluginJars()) {
            if (isChunkyPlugin(pluginJar)) {
                LOGGER.info("Chunky is already installed and its plugin archive is valid: " + pluginJar.getFileName());
                return new InstallResult("Chunky", List.of(), true);
            }
        }
        for (Path candidate : this.listJarFiles()) {
            if (candidate.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("chunky-")
                && !isPluginJar(candidate)) {
                Files.deleteIfExists(candidate);
                LOGGER.warning("Removed invalid Chunky plugin archive: " + candidate.getFileName());
            }
        }

        final List<String> chunkyLoader = List.of("paper");
        final ModrinthService chunkyService = new ModrinthService(this.pluginDirectory, this.gameVersion, chunkyLoader);
        final JsonObject projectJson = chunkyService.getJson(API + "/project/chunky");
        final Project project = new Project(
            value(projectJson, "id"),
            value(projectJson, "slug"),
            value(projectJson, "title"),
            optionalValue(projectJson, "description")
        );
        final List<Artifact> artifacts = new ArrayList<>();
        chunkyService.resolveProject(project, null, new HashSet<>(), artifacts);
        if (artifacts.isEmpty()) {
            throw new IOException("Modrinth did not return a compatible Chunky release");
        }
        final Artifact selected = artifacts.getLast();
        final String version = safeFilePart(selected.version().versionNumber());
        final Artifact chunkyArtifact = new Artifact(selected.project(), selected.version(),
            selected.file(), "chunky-" + version + ".jar");
        final List<Artifact> dependencyArtifacts = artifacts.subList(0, artifacts.size() - 1);
        final List<Artifact> installArtifacts = new ArrayList<>(dependencyArtifacts);
        installArtifacts.add(chunkyArtifact);
        final InstallResult result = chunkyService.downloadAndInstall(project, installArtifacts);
        LOGGER.info("Installed Chunky " + version + " from Modrinth. Paper will load it during plugin initialization.");
        return result;
    }

    private void resolveProject(
        final Project project,
        final String pinnedVersionId,
        final Set<String> visitedProjects,
        final List<Artifact> artifacts
    ) throws IOException, InterruptedException {
        if (!visitedProjects.add(project.id())) {
            return;
        }
        if (visitedProjects.size() > 64) {
            throw new IOException("Modrinth dependency graph exceeds the limit of 64 plugins");
        }
        final JsonObject version = pinnedVersionId == null
            ? this.findCompatibleVersion(project.id())
            : this.getJson(API + "/version/" + encode(pinnedVersionId));
        final JsonArray dependencies = version.has("dependencies") && version.get("dependencies").isJsonArray()
            ? version.getAsJsonArray("dependencies")
            : new JsonArray();
        for (JsonElement dependencyElement : dependencies) {
            final JsonObject dependency = dependencyElement.getAsJsonObject();
            if (!"required".equals(optionalValue(dependency, "dependency_type"))) {
                continue;
            }
            final String dependencyProjectId = optionalValue(dependency, "project_id");
            final String dependencyVersion = optionalValue(dependency, "version_id");
            if (dependencyProjectId.isBlank() && dependencyVersion.isBlank()) {
                LOGGER.warning("Modrinth required dependency has no project ID; cannot install it automatically.");
                continue;
            }
            final JsonObject dependencyVersionJson = dependencyProjectId.isBlank()
                ? this.getJson(API + "/version/" + encode(dependencyVersion))
                : null;
            final String resolvedProjectId = dependencyProjectId.isBlank()
                ? value(dependencyVersionJson, "project_id")
                : dependencyProjectId;
            final JsonObject dependencyProjectJson = this.getJson(API + "/project/" + encode(resolvedProjectId));
            if (!"plugin".equals(value(dependencyProjectJson, "project_type"))) {
                throw new IOException("Required dependency '" + value(dependencyProjectJson, "title")
                    + "' is not a plugin project");
            }
            final Project dependencyProject = new Project(
                value(dependencyProjectJson, "id"),
                value(dependencyProjectJson, "slug"),
                value(dependencyProjectJson, "title"),
                optionalValue(dependencyProjectJson, "description")
            );
            this.resolveProject(dependencyProject, dependencyVersion.isBlank() ? null : dependencyVersion,
                visitedProjects, artifacts);
        }
        final JsonObject file = selectJarFile(version);
        final String requestedFileName = safeFileName(value(file, "filename"));
        artifacts.add(new Artifact(project, parseVersion(version), file, requestedFileName));
    }

    private JsonObject findCompatibleVersion(final String projectId) throws IOException, InterruptedException {
        final JsonArray loaderArray = new JsonArray();
        this.loaders.forEach(loaderArray::add);
        final String encodedLoaders = encode(loaderArray.toString());
        final String gameVersions = URLEncoder.encode("[\"" + this.gameVersion + "\"]", StandardCharsets.UTF_8);
        final JsonElement versionResponse = this.getJsonElement(API + "/project/" + encode(projectId) + "/version?loaders="
            + encodedLoaders + "&game_versions=" + gameVersions);
        if (!versionResponse.isJsonArray()) {
            throw new IOException("Modrinth API returned an unexpected version list");
        }
        final JsonArray versions = versionResponse.getAsJsonArray();
        if (versions.isEmpty()) {
            throw new IOException("No compatible Modrinth plugin release found for Minecraft " + this.gameVersion);
        }
        return versions.get(0).getAsJsonObject();
    }

    private InstallResult downloadAndInstall(final Project root, final List<Artifact> artifacts)
        throws IOException, InterruptedException {
        Files.createDirectories(this.pluginDirectory);
        final List<StagedArtifact> staged = new ArrayList<>();
        try {
            for (Artifact artifact : artifacts) {
                final Path destination = this.pluginDirectory.resolve(artifact.installFileName()).normalize();
                if (!destination.getParent().equals(this.pluginDirectory)
                    || !artifact.installFileName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    throw new IOException("Modrinth returned an unsafe plugin filename");
                }
                final String expectedHash = expectedHash(artifact.file());
                if (Files.exists(destination) && isPluginJar(destination) && !expectedHash.isBlank()
                    && expectedHash.equalsIgnoreCase(sha512(destination))) {
                    continue;
                }

                final Path temporary = Files.createTempFile(this.pluginDirectory, ".salmon-modrinth-", ".part");
                staged.add(new StagedArtifact(artifact, temporary, destination));
                this.download(artifact.file(), temporary, expectedHash);
                if (!isPluginJar(temporary)) {
                    throw new IOException("Modrinth file '" + artifact.installFileName()
                        + "' is not a valid Paper/Spigot plugin JAR");
                }
            }
            for (StagedArtifact artifact : staged) {
                moveReplacing(artifact.temporary(), artifact.destination());
                LOGGER.info("Installed Modrinth plugin archive '" + artifact.destination().getFileName() + "'");
            }
        } catch (IOException | InterruptedException | RuntimeException ex) {
            for (StagedArtifact artifact : staged) {
                Files.deleteIfExists(artifact.temporary());
            }
            throw ex;
        }
        final List<String> installed = staged.stream()
            .map(artifact -> artifact.destination().getFileName().toString()).toList();
        return new InstallResult(root.title(), installed, false);
    }

    private void download(final JsonObject file, final Path destination, final String expectedHash)
        throws IOException, InterruptedException {
        final URI uri;
        try {
            uri = URI.create(value(file, "url"));
        } catch (IllegalArgumentException ex) {
            throw new IOException("Modrinth returned an invalid download URL", ex);
        }
        final String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
            || !(host.equalsIgnoreCase("modrinth.com") || host.endsWith(".modrinth.com")
                || host.equalsIgnoreCase("modrinth.net") || host.endsWith(".modrinth.net"))) {
            throw new IOException("Refusing non-Modrinth or non-HTTPS plugin download URL");
        }
        final HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
            .header("User-Agent", USER_AGENT).GET().build();
        final HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("Modrinth file download failed with HTTP " + response.statusCode());
            }
            long written = 0L;
            try (var output = Files.newOutputStream(destination)) {
                final byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    written += read;
                    if (written > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Modrinth plugin archive exceeds the 100 MiB download limit");
                    }
                    output.write(buffer, 0, read);
                }
            }
        }
        if (!expectedHash.isBlank() && !expectedHash.equalsIgnoreCase(sha512(destination))) {
            throw new IOException("Modrinth plugin archive checksum did not match its API metadata");
        }
    }

    private JsonObject getJson(final String url) throws IOException, InterruptedException {
        final JsonElement json = this.getJsonElement(url);
        if (!json.isJsonObject()) {
            throw new IOException("Modrinth API returned an unexpected JSON response");
        }
        return json.getAsJsonObject();
    }

    private JsonElement getJsonElement(final String url) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
            .header("User-Agent", USER_AGENT).header("Accept", "application/json").GET().build();
        final HttpResponse<String> response = this.client.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Modrinth API returned HTTP " + response.statusCode() + " for " + url);
        }
        try {
            return JsonParser.parseString(response.body());
        } catch (IllegalStateException | com.google.gson.JsonParseException ex) {
            throw new IOException("Could not parse Modrinth API response", ex);
        }
    }

    private static JsonObject selectJarFile(final JsonObject version) throws IOException {
        final JsonArray files = version.getAsJsonArray("files");
        for (JsonElement element : files) {
            final JsonObject file = element.getAsJsonObject();
            if (value(file, "filename").toLowerCase(Locale.ROOT).endsWith(".jar")
                && file.has("primary") && file.get("primary").getAsBoolean()) {
                return file;
            }
        }
        for (JsonElement element : files) {
            final JsonObject file = element.getAsJsonObject();
            if (value(file, "filename").toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return file;
            }
        }
        throw new IOException("Modrinth release has no JAR file");
    }

    private static Version parseVersion(final JsonObject json) throws IOException {
        return new Version(value(json, "id"), value(json, "version_number"));
    }

    private static String expectedHash(final JsonObject file) throws IOException {
        final JsonObject hashes = file.getAsJsonObject("hashes");
        if (hashes != null && hashes.has("sha512")) {
            return value(hashes, "sha512");
        }
        throw new IOException("Modrinth did not provide a SHA-512 checksum for the plugin file");
    }

    private static String sha512(final Path path) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-512");
            try (InputStream input = Files.newInputStream(path)) {
                final byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-512 is unavailable", ex);
        }
    }

    private List<Path> listPluginJars() throws IOException {
        return this.listJarFiles().stream().filter(ModrinthService::isPluginJar).toList();
    }

    private List<Path> listJarFiles() throws IOException {
        try (var paths = Files.list(this.pluginDirectory)) {
            return paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .toList();
        }
    }

    private static boolean isPluginJar(final Path path) {
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            for (String descriptor : List.of("plugin.yml", "paper-plugin.yml")) {
                final var entry = jar.getJarEntry(descriptor);
                if (entry == null) {
                    continue;
                }
                final YamlConfiguration config = new YamlConfiguration();
                try (InputStream input = jar.getInputStream(entry)) {
                    config.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    return !config.getString("name", "").isBlank();
                } catch (InvalidConfigurationException ex) {
                    return false;
                }
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isChunkyPlugin(final Path path) {
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            for (String descriptor : List.of("plugin.yml", "paper-plugin.yml")) {
                final var entry = jar.getJarEntry(descriptor);
                if (entry == null) {
                    continue;
                }
                final YamlConfiguration config = new YamlConfiguration();
                try (InputStream input = jar.getInputStream(entry)) {
                    config.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    if ("chunky".equalsIgnoreCase(config.getString("name", ""))) {
                        return true;
                    }
                } catch (InvalidConfigurationException ex) {
                    return false;
                }
            }
        } catch (IOException ex) {
            return false;
        }
        return false;
    }

    private static void moveReplacing(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String safeFileName(final String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9._+-]{1,128}\\.jar")) {
            throw new IOException("Modrinth returned an unsafe plugin filename");
        }
        return name;
    }

    private static String safeFilePart(final String part) throws IOException {
        if (part == null || !part.matches("[A-Za-z0-9._+-]{1,64}")) {
            throw new IOException("Modrinth returned an invalid plugin version number");
        }
        return part;
    }

    private static String value(final JsonObject object, final String key) throws IOException {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IOException("Modrinth response is missing '" + key + "'");
        }
        return object.get(key).getAsString();
    }

    private static String optionalValue(final JsonObject object, final String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record Project(String id, String slug, String title, String description) {
    }

    public record InstallResult(String project, List<String> installedFiles, boolean alreadyInstalled) {
    }

    private record Version(String id, String versionNumber) {
    }

    private record Artifact(Project project, Version version, JsonObject file, String installFileName) {
    }

    private record StagedArtifact(Artifact artifact, Path temporary, Path destination) {
    }
}
