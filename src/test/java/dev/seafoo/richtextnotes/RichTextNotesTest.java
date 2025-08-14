package dev.seafoo.richtextnotes;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RichTextNotesTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RichTextNotesPlugin.class);
		RuneLite.main(args);
	}
}