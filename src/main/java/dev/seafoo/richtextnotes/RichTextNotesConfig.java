package dev.seafoo.richtextnotes;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("richtextnotes")
public interface RichTextNotesConfig extends Config
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

	@ConfigItem(
		keyName = "menuPriority",
		name = "Sidebar Priority",
		description = "Adjust the runelite sidebar priority. Lower priority => higher on sidebar. Restart the client to take effect"
	)
	default int menuPriority()
	{
		return 7;
	}
}