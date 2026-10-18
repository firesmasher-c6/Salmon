package dev.firesmasher.salmon;

import com.destroystokyo.paper.util.VersionFetcher;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.papermc.paper.ServerBuildInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

public final class SalmonVersionFetcher implements VersionFetcher {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final String RELEASE_API = "https://api.github.com/repos/firesmasher-c6/Salmon/releases/latest";
    private static final String RELEASE_PAGE = "https://github.com/firesmasher-c6/Salmon/releases/latest";
    private static final Pattern BUILD_VERSION = Pattern.compile("^(\\d+)([a-z])-(\\d+)$", Pattern.CASE_INSENSITIVE);

    @Override
    public long getCacheTime() {
        return 720_000L;
    }

    @Override
    public Component getVersionMessage() {
        try {
            final String latestRelease = fetchLatestRelease();
            final String currentBuild = currentSalmonBuild();
            return updateMessage(currentBuild, latestRelease);
        } catch (final IOException | JsonParseException exception) {
            LOGGER.error("Failed to check Salmon releases using GitHub's API", exception);
            return Component.text("Error checking Salmon version information. Try again later.", NamedTextColor.RED);
        }
    }

    public static void logStartupVersionStatus() {
        final Component message = new SalmonVersionFetcher().getVersionMessage();
        LOGGER.info("Salmon release check: {}", PlainTextComponentSerializer.plainText().serialize(message));
    }

    private static String fetchLatestRelease() throws IOException {
        final URL releasesUrl = URI.create(RELEASE_API).toURL();
        final HttpURLConnection connection = (HttpURLConnection) releasesUrl.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Salmon/"
            + ServerBuildInfo.buildInfo().asString(ServerBuildInfo.StringRepresentation.VERSION_SIMPLE));
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

        try {
            final int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub releases API returned HTTP " + responseCode);
            }

            try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                final JsonElement response = JsonParser.parseReader(reader);
                if (!response.isJsonObject()) {
                    throw new IOException("GitHub releases API returned a non-object response");
                }
                final JsonElement tagName = response.getAsJsonObject().get("tag_name");
                if (tagName == null || !tagName.isJsonPrimitive() || !tagName.getAsJsonPrimitive().isString()
                    || tagName.getAsString().isBlank()) {
                    throw new IOException("GitHub releases API response did not contain a release tag");
                }
                return tagName.getAsString();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String currentSalmonBuild() {
        final ServerBuildInfo buildInfo = ServerBuildInfo.buildInfo();
        final String fullVersion = buildInfo.asString(ServerBuildInfo.StringRepresentation.VERSION_SIMPLE);
        final String versionPrefix = buildInfo.minecraftVersionId() + "-";
        String build = fullVersion.startsWith(versionPrefix) ? fullVersion.substring(versionPrefix.length()) : fullVersion;
        // Local development builds append a Git commit hash (for example
        // 8e-009-abc1234). Strip that suffix so a locally-built release build
        // is still recognized as the corresponding Salmon release.
        final java.util.regex.Matcher buildMatcher = Pattern.compile("^(\\d+[a-z]-\\d+)", Pattern.CASE_INSENSITIVE).matcher(build);
        if (buildMatcher.find()) {
            build = buildMatcher.group(1);
        }
        return build;
    }

    static Component updateMessage(final String currentBuild, final String latestRelease) {
        if (currentBuild.equals(latestRelease)) {
            return Component.text("You are running the latest Salmon release (" + latestRelease + ").", NamedTextColor.GREEN);
        }

        final OptionalInt buildComparison = compareBuildNumbers(currentBuild, latestRelease);
        if (buildComparison.isPresent() && buildComparison.getAsInt() > 0) {
            return Component.text("You are running an unreleased Salmon build (" + currentBuild
                + "), ahead of the latest published release (" + latestRelease + ").", NamedTextColor.AQUA);
        }

        final String message;
        if (buildComparison.isPresent() && buildComparison.getAsInt() < 0) {
            message = "A newer Salmon release is available: " + latestRelease + " (you are running " + currentBuild + ").";
        } else {
            message = "Your Salmon build (" + (currentBuild.isBlank() ? "unknown" : currentBuild)
                + ") differs from the latest published release (" + latestRelease + ").";
        }
        return Component.text(message, NamedTextColor.YELLOW)
            .append(Component.newline())
            .append(Component.text("Download: " + RELEASE_PAGE, NamedTextColor.GOLD)
                .clickEvent(ClickEvent.openUrl(RELEASE_PAGE)));
    }

    private static OptionalInt compareBuildNumbers(final String currentBuild, final String latestRelease) {
        final Optional<BuildVersion> current = parseBuildVersion(currentBuild);
        final Optional<BuildVersion> latest = parseBuildVersion(latestRelease);
        if (current.isEmpty() || latest.isEmpty()) {
            return OptionalInt.empty();
        }
        final int seriesComparison = current.get().series().compareTo(latest.get().series());
        if (seriesComparison != 0) {
            return OptionalInt.of(seriesComparison);
        }
        final int channelComparison = Character.compare(current.get().channel(), latest.get().channel());
        return OptionalInt.of(channelComparison != 0
            ? channelComparison
            : current.get().number().compareTo(latest.get().number()));
    }

    private static Optional<BuildVersion> parseBuildVersion(final String build) {
        final Matcher matcher = BUILD_VERSION.matcher(build);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new BuildVersion(new BigInteger(matcher.group(1)),
            Character.toLowerCase(matcher.group(2).charAt(0)), new BigInteger(matcher.group(3))));
    }

    private record BuildVersion(BigInteger series, char channel, BigInteger number) {}
}
