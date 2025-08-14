package dev.seafoo.notesenhanced;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("notesenhanced")
public interface NotesEnhancedConfig extends Config
{
	@ConfigItem(
		keyName = "alwaysShowToolbar",
		name = "Always show Toolbar",
		description = "Always show the editor toolbar even when it is not in focus"
	)
	default boolean alwaysShowToolbar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "maxPaneCount",
		name = "Pane Limit",
		description = "Maximum Number of Panes"
	)
	default int maxPaneCount()
	{
		return 5;
	}
}