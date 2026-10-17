package dev.firesmasher.salmon;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import dev.firesmasher.salmon.modifiers.SalmonScriptParserTest;
import io.papermc.paper.plugin.provider.type.PluginFileTypeTest;

@Suite
@SelectClasses({SalmonConfigurationTest.class, SalmonCommandCompletionTest.class, SalmonScriptParserTest.class, PluginFileTypeTest.class})
public class SalmonConfigurationTestSuite {
}
