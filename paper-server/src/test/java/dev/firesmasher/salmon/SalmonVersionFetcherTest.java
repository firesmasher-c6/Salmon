package dev.firesmasher.salmon;

import dev.firesmasher.salmon.version.SalmonVersionFetcher;

import static net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class SalmonVersionFetcherTest {
    @Test
    void reportsMatchingReleaseAsCurrent() {
        final Component message = SalmonVersionFetcher.updateMessage("1r-007", "1r-007");

        assertEquals("You are running the latest Salmon release (1r-007).", plainText().serialize(message));
    }

    @Test
    void reportsHigherLocalBuildAsUnreleasedAndAhead() {
        final Component message = SalmonVersionFetcher.updateMessage("2r-008", "1r-007");

        assertTrue(plainText().serialize(message).contains("unreleased Salmon build (2r-008)"));
        assertTrue(plainText().serialize(message).contains("ahead of the latest published release (1r-007)"));
    }

    @Test
    void reportsLowerLocalBuildAsOutdated() {
        final Component message = SalmonVersionFetcher.updateMessage("1r-007", "2r-008");

        assertTrue(plainText().serialize(message).contains("A newer Salmon release is available: 2r-008"));
    }

    @Test
    void comparesReleaseSeriesBeforeBuildNumber() {
        final Component newerSeries = SalmonVersionFetcher.updateMessage("2r-008", "1r-009");
        final Component olderSeries = SalmonVersionFetcher.updateMessage("1r-009", "2r-008");

        assertTrue(plainText().serialize(newerSeries).contains("unreleased Salmon build (2r-008)"));
        assertTrue(plainText().serialize(olderSeries).contains("A newer Salmon release is available: 2r-008"));
    }

    @Test
    void avoidsGuessingWhenBuildNumbersCannotBeCompared() {
        final Component message = SalmonVersionFetcher.updateMessage("custom", "latest");

        assertTrue(plainText().serialize(message).contains("differs from the latest published release"));
    }
}
