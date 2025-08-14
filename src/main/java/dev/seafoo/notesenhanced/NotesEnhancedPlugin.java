package dev.seafoo.notesenhanced;

import com.google.inject.Provides;
import dev.seafoo.notesenhanced.services.FileStorageService;
import dev.seafoo.notesenhanced.services.ItemIconService;
import dev.seafoo.notesenhanced.ui.panels.NotesPanel;
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
	name = "Notes Enhanced",
	description = "Enhanced notes plugin with rich text formatting and item icons",
	tags = {"notes", "enhanced", "sidebar", "markdown", "text"}
)
public class NotesEnhancedPlugin extends Plugin
{

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private NotesEnhancedConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemIconService itemIconService;

	private NotesPanel panel;
	private NavigationButton navButton;
	private FileStorageService storageService;

	@Provides
	NotesEnhancedConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NotesEnhancedConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		try
		{
			// Initialize storage service with configManager
			storageService = new FileStorageService(configManager);

			log.debug("Notes Enhanced Initialized with profile: {}", storageService.getCurrentProfileName());

			// Create and initialize the panel
			panel = injector.getInstance(NotesPanel.class);
			panel.init(config, storageService, itemIconService);

			// Create navigation button
			final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icons/notes_enhanced_icon.png");

			navButton = NavigationButton.builder()
				.tooltip("Notes Enhanced")
				.icon(icon)
				.priority(7)
				.panel(panel)
				.build();

			clientToolbar.addNavigation(navButton);

			log.info("Notes Enhanced plugin started successfully for profile: {}",
				storageService.getDisplayProfileName());

		}
		catch (Exception e)
		{
			log.error("Failed to start Notes Enhanced plugin", e);

			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
				null,
				"Failed to start Notes Enhanced plugin:\n" + e.getMessage(),
				"Notes Enhanced Error",
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