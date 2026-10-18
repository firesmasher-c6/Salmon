package dev.firesmasher.salmon;

import dev.firesmasher.salmon.config.SalmonConfiguration;
import dev.firesmasher.salmon.runtime.SalmonMotdManager;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({SalmonConfigurationTest.class, SalmonCommandCompletionTest.class, PluginExecutionMetricsTest.class,
    SalmonVersionFetcherTest.class})
public class SalmonConfigurationTestSuite {
}
