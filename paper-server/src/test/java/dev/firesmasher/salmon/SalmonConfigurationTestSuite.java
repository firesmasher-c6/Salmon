package dev.firesmasher.salmon;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import io.papermc.paper.plugin.provider.type.PluginFileTypeTest;

@Suite
@SelectClasses({SalmonConfigurationTest.class, SalmonCommandCompletionTest.class, PluginExecutionMetricsTest.class,
    SalmonVersionFetcherTest.class, PluginFileTypeTest.class})
public class SalmonConfigurationTestSuite {
}
