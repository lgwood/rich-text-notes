package dev.seafoo.richtextnotes;

import com.google.gson.Gson;
import com.google.inject.Provides;
import dev.seafoo.richtextnotes.services.FileStorageService;
import dev.seafoo.richtextnotes.services.ItemIconService;
import dev.seafoo.richtextnotes.ui.panels.NotesPanel;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Rich Text Notes",
	description = "Enhanced notes plugin with rich text formatting and item icons",
	tags = {"notes", "rich", "text", "enhanced", "sidebar", "markdown"}
)
public class RichTextNotesPlugin extends Plugin
{

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private RichTextNotesConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemIconService itemIconService;

	@Inject
	private Gson gson;

	private NotesPanel panel;
	private NavigationButton navButton;
	private FileStorageService storageService;

	@Provides
	RichTextNotesConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RichTextNotesConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		try
		{
			// Initialize storage service with configManager
			storageService = new FileStorageService(configManager, gson);

			log.debug("Rich Text Notes Initialized with profile: {}", storageService.getCurrentProfileName());

			// Create and initialize the panel
			panel = injector.getInstance(NotesPanel.class);
			panel.init(config, storageService, itemIconService);

			// Create navigation button
			final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icons/rich_text_notes_icon.png");

			navButton = NavigationButton.builder()
				.tooltip("Rich Text Notes")
				.icon(icon)
				.priority(config.menuPriority())
				.panel(panel)
				.build();

			clientToolbar.addNavigation(navButton);

			log.info("Rich Text Notes plugin started successfully for profile: {}",
				storageService.getDisplayProfileName());

		}
		catch (Exception e)
		{
			log.error("Failed to start Rich Text Notes plugin", e);

			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
				null,
				"Failed to start Rich Text Notes plugin:\n" + e.getMessage(),
				"Rich Text Notes Error",
				JOptionPane.ERROR_MESSAGE
			));

			throw new Exception(e);
		}
	}

	@Override
	protected void shutDown()
	{
		try
		{
			if (panel != null)
			{
				panel.saveAllNotes();
				panel.saveEditorLayout();
			}

			if (navButton != null)
			{
				clientToolbar.removeNavigation(navButton);
			}

			if (panel != null)
			{
				panel.cleanup();
			}

		}
		catch (Exception e)
		{
			log.error("Error during plugin shutdown", e);
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged profileChanged)
	{
		log.debug("Profile changed event received");

		try
		{
			// Save current state before switching
			if (panel != null)
			{
				panel.saveAllNotes();
				panel.saveEditorLayout();
			}

			String newProfile = storageService.updateCurrentProfile();

			// Switch panel to new profile
			if (panel != null)
			{
				panel.switchProfile(newProfile);
			}

			log.debug("Successfully switched to profile: {}", storageService.getDisplayProfileName());
		}
		catch (Exception e)
		{
			log.error("Error handling profile change", e);

			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
				panel,
				"Failed to switch to new profile:\n" + e.getMessage(),
				"Profile Switch Error",
				JOptionPane.WARNING_MESSAGE
			));
		}
	}
}