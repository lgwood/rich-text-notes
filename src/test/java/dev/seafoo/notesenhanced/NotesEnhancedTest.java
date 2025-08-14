package dev.seafoo.notesenhanced;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NotesEnhancedTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NotesEnhancedPlugin.class);
		RuneLite.main(args);
	}
}