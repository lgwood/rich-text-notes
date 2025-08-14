package dev.seafoo.richtextnotes.ui.panels;

import dev.seafoo.richtextnotes.RichTextNotesConfig;
import dev.seafoo.richtextnotes.models.EditorLayout;
import dev.seafoo.richtextnotes.models.NoteMetadata;
import dev.seafoo.richtextnotes.services.FileStorageService;
import dev.seafoo.richtextnotes.services.ItemIconService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Main notes panel that manages multiple pane groups.
 */
@Slf4j
public class NotesPanel extends PluginPanel
{
	private static final int AUTO_SAVE_DELAY_MS = 2000;
	private static final int LAYOUT_SAVE_DELAY_MS = 2000;

	private FileStorageService storageService;
	private RichTextNotesConfig config;
	private ItemIconService itemIconService;

	// Split pane management
	private JPanel mainContentPanel;
	private final List<NotePaneGroup> paneGroups = new ArrayList<>();
	private final AtomicInteger paneGroupCounter = new AtomicInteger(0);

	// UI Components
	private Timer autoSaveTimer;

	// State tracking
	private NotePaneGroup activePaneGroup;
	private String activeNoteId;
	private Timer layoutSaveTimer;

	public void init(RichTextNotesConfig config, FileStorageService storageService, ItemIconService itemIconService)
	{

		this.config = config;
		this.storageService = storageService;
		this.itemIconService = itemIconService;

		setupUI();
		setupAutoSave();
		restoreEditorLayout();

	}

	private void setupUI()
	{
		// Set up main layout
		getParent().setLayout(new BorderLayout());
		getParent().add(this, BorderLayout.CENTER);

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Create main content panel that will contain the split panes
		mainContentPanel = new JPanel(new BorderLayout());
		mainContentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(mainContentPanel, BorderLayout.CENTER);


		// Create the first pane group
		createInitialPaneGroup();
	}

	private void createInitialPaneGroup()
	{
		String paneGroupId = "pane_" + paneGroupCounter.getAndIncrement();
		NotePaneGroup paneGroup = new NotePaneGroup(this, storageService, config, paneGroupId, itemIconService);

		paneGroups.add(paneGroup);
		activePaneGroup = paneGroup;

		// Add directly to main content panel (no split pane needed for single pane)
		mainContentPanel.add(paneGroup, BorderLayout.CENTER);

		log.debug("Created initial pane group: {}", paneGroupId);
	}

	/**
	 * Split the specified pane group, creating a new pane group below
	 */
	public void splitPaneGroup()
	{
		try
		{
			// Check if we've reached the maximum number of pane groups
			if (paneGroups.size() >= config.maxPaneCount())
			{
				return;
			}

			String newPaneGroupId = "pane_" + paneGroupCounter.getAndIncrement();
			NotePaneGroup newPaneGroup = new NotePaneGroup(this, storageService, config, newPaneGroupId, itemIconService);

			paneGroups.add(newPaneGroup);

			// Rebuild the split pane layout
			rebuildSplitPaneLayout();
			scheduleLayoutSave();

			log.debug("Split pane group. Total panes: {}", paneGroups.size());

		}
		catch (Exception e)
		{
			log.error("Failed to split pane group", e);
		}
	}

	/**
	 * Close the specified pane group
	 */
	public void closePaneGroup(NotePaneGroup paneGroup)
	{
		if (paneGroups.size() <= 1)
		{
			JOptionPane.showMessageDialog(
				this,
				"Cannot close the last pane group.\nUse 'Reset Layout' from the menu to clear all notes.",
				"Close Pane",
				JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		// Count tabs in this pane
		int tabCount = paneGroup.getTabCount();

		// Check for unsaved changes
		if (paneGroup.hasModifiedNotes())
		{
			int result = JOptionPane.showConfirmDialog(
				this,
				String.format("This pane has %d tab(s) with unsaved changes.\nSave before closing?", tabCount),
				"Close Pane - Unsaved Changes",
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE
			);

			if (result == JOptionPane.YES_OPTION)
			{
				paneGroup.saveAllNotes();
			}
			else if (result == JOptionPane.CANCEL_OPTION)
			{
				return;
			}
		}

		String closedPaneId = paneGroup.getPaneGroupId();

		// Remove the pane group
		paneGroups.remove(paneGroup);

		// Update active pane if necessary
		if (activePaneGroup == paneGroup)
		{
			activePaneGroup = paneGroups.isEmpty() ? null : paneGroups.get(0);
			activeNoteId = null;
		}

		// Rebuild the split pane layout
		rebuildSplitPaneLayout();

		log.debug("Closed pane group: {}. Remaining panes: {}", closedPaneId, paneGroups.size());
	}

	/**
	 * Rebuild the entire split pane layout based on current pane groups
	 */
	private void rebuildSplitPaneLayout()
	{
		mainContentPanel.removeAll();
		JSplitPane rootSplitPane;

		if (paneGroups.isEmpty())
		{
			createInitialPaneGroup();
			return;
		}

		if (paneGroups.size() == 1)
		{
			// Single pane, no split needed
			mainContentPanel.add(paneGroups.get(0), BorderLayout.CENTER);
		}
		else
		{
			// Multiple panes, create nested split panes
			rootSplitPane = createNestedSplitPanes(paneGroups);
			mainContentPanel.add(rootSplitPane, BorderLayout.CENTER);
		}

		mainContentPanel.revalidate();
		mainContentPanel.repaint();
	}

	/**
	 * Create nested split panes for multiple pane groups
	 */
	private JSplitPane createNestedSplitPanes(List<NotePaneGroup> paneGroups)
	{
		if (paneGroups.size() == 2)
		{
			JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				paneGroups.get(0), paneGroups.get(1));
			setupSplitPane(splitPane);
			return splitPane;
		}

		// For more than 2 panes, create nested split panes
		// Split the list in half and recurse
		int mid = paneGroups.size() / 2;
		List<NotePaneGroup> topPanes = paneGroups.subList(0, mid);
		List<NotePaneGroup> bottomPanes = paneGroups.subList(mid, paneGroups.size());

		Component topComponent = topPanes.size() == 1 ?
			topPanes.get(0) : createNestedSplitPanes(topPanes);
		Component bottomComponent = bottomPanes.size() == 1 ?
			bottomPanes.get(0) : createNestedSplitPanes(bottomPanes);

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
			topComponent, bottomComponent);
		setupSplitPane(splitPane);
		return splitPane;
	}

	private void setupSplitPane(JSplitPane splitPane)
	{
		splitPane.setResizeWeight(0.5); // Equal space by default
		splitPane.setContinuousLayout(true);
		splitPane.setOneTouchExpandable(false);
		splitPane.setBorder(BorderFactory.createEmptyBorder());
		splitPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		splitPane.setDividerSize(5);
	}

	private void setupAutoSave()
	{
		autoSaveTimer = new Timer(AUTO_SAVE_DELAY_MS, e -> {
			saveAllNotes();
			autoSaveTimer.stop();
		});
		autoSaveTimer.setRepeats(false);

		layoutSaveTimer = new Timer(LAYOUT_SAVE_DELAY_MS, e -> {
			saveEditorLayout();
			layoutSaveTimer.stop();
		});
		layoutSaveTimer.setRepeats(false);

	}


	private void restoreEditorLayout()
	{
		try
		{
			EditorLayout layout = storageService.loadEditorLayout();

			if (layout == null || !layout.hasContent())
			{
				log.debug("No layout to restore or layout is empty");
				loadExistingNotesDefaultBehavior();
				return;
			}

			log.debug("Restoring editor layout: {} pane groups, {} total notes",
				layout.getPaneGroupCount(), layout.getTotalNoteCount());

			// Clear current layout (we already have one initial pane group)
			paneGroups.clear();
			mainContentPanel.removeAll();
			activePaneGroup = null;
			activeNoteId = null;

			// Sort pane groups by order index
			List<EditorLayout.PaneGroupLayout> sortedPaneGroups = new ArrayList<>(layout.getPaneGroups());
			sortedPaneGroups.sort(Comparator.comparingInt(EditorLayout.PaneGroupLayout::getOrderIndex));


			for (EditorLayout.PaneGroupLayout paneLayout : sortedPaneGroups)
			{
				// Create new pane group
				String paneGroupId = paneLayout.getPaneGroupId();
				NotePaneGroup paneGroup = new NotePaneGroup(this, storageService, config, paneGroupId, itemIconService);
				paneGroups.add(paneGroup);

				// Load notes into this pane group
				for (String noteId : paneLayout.getNoteIds())
				{
					try
					{
						// Check if note still exists
						if (storageService.noteExists(noteId))
						{
							paneGroup.openNoteInTab(noteId, false);
						}
						else
						{
							log.warn("Note {} no longer exists, skipping", noteId);
						}
					}
					catch (Exception e)
					{
						log.error("Failed to load note {} into pane {}", noteId, paneGroupId, e);
					}
				}

				// Set active note for this pane
				if (paneLayout.getActiveNoteId() != null && paneGroup.getTabCount() > 0)
				{
					try
					{
						paneGroup.setActiveNote(paneLayout.getActiveNoteId());
					}
					catch (Exception e)
					{
						log.warn("Failed to set active note {} in pane {}",
							paneLayout.getActiveNoteId(), paneGroupId, e);
					}
				}
			}

			// Handle case where no pane groups were created successfully
			if (paneGroups.isEmpty())
			{
				log.warn("No pane groups were restored, creating initial pane");
				createInitialPaneGroup();
				loadExistingNotesDefaultBehavior();
				return;
			}

			// Rebuild the split pane layout
			rebuildSplitPaneLayout();

			// Set global active pane and note
			String activePaneGroupId = layout.getActivePaneGroupId();
			if (activePaneGroupId != null)
			{
				activePaneGroup = paneGroups.stream()
					.filter(pg -> activePaneGroupId.equals(pg.getPaneGroupId()))
					.findFirst()
					.orElse(paneGroups.get(0));
			}
			else
			{
				activePaneGroup = paneGroups.get(0);
			}

			// Set active note
			String activeNoteIdFromLayout = layout.getActiveNoteId();
			if (activeNoteIdFromLayout != null)
			{
				activeNoteId = activeNoteIdFromLayout;
			}
			else if (activePaneGroup != null)
			{
				activeNoteId = activePaneGroup.getCurrentNoteId();
			}

		}
		catch (Exception e)
		{
			log.error("Failed to restore editor layout, falling back to default behavior", e);

			// Fallback to default behavior if restoration fails
			try
			{
				paneGroups.clear();
				mainContentPanel.removeAll();
				activePaneGroup = null;
				activeNoteId = null;
				createInitialPaneGroup();
				loadExistingNotesDefaultBehavior();
			}
			catch (Exception fallbackError)
			{
				log.error("Even fallback failed", fallbackError);
			}
		}
	}

	private void loadExistingNotesDefaultBehavior()
	{
		try
		{
			List<NoteMetadata> allNotes = storageService.listNotesWithMetadata();

			if (allNotes.isEmpty())
			{
				log.debug("No existing notes found");
				return;
			}

			// Sort notes by last modified date (most recent first)
			allNotes.sort((a, b) -> {
				if (a.getLastModified() == null && b.getLastModified() == null)
				{
					return 0;
				}
				if (a.getLastModified() == null)
				{
					return 1;
				}
				if (b.getLastModified() == null)
				{
					return -1;
				}
				return b.getLastModified().compareTo(a.getLastModified());
			});

			// Load notes into the first pane group
			NotePaneGroup firstPane = paneGroups.isEmpty() ? null : paneGroups.get(0);
			if (firstPane != null)
			{
				int loadedCount = 0;
				for (NoteMetadata metadata : allNotes)
				{
					if (metadata.isPinned() || loadedCount < 5)
					{
						firstPane.openNoteInTab(metadata.getNoteId(), false);
						loadedCount++;
					}
				}

				log.debug("Loaded {} notes into first pane (default behavior)", loadedCount);
			}

		}
		catch (Exception e)
		{
			log.error("Failed to load existing notes with default behavior", e);
		}
	}


	// Public interface methods for NotePaneGroup callbacks

	public void onActiveNoteChanged(NotePaneGroup paneGroup, String noteId)
	{
		activePaneGroup = paneGroup;
		activeNoteId = noteId;
	}

	public void scheduleAutoSave()
	{
		if (autoSaveTimer.isRunning())
		{
			autoSaveTimer.restart();
		}
		else
		{
			autoSaveTimer.start();
		}
	}

	public int getPaneGroupCount()
	{
		return paneGroups.size();
	}

	// Public interface methods for plugin

	public void saveAllNotes()
	{
		try
		{
			int savedCount = 0;
			for (NotePaneGroup paneGroup : paneGroups)
			{
				if (paneGroup.hasModifiedNotes())
				{
					paneGroup.saveAllNotes();
					savedCount++;
				}
			}

			if (savedCount > 0)
			{
				log.debug("Auto-saved notes in {} pane groups", savedCount);
			}

		}
		catch (Exception e)
		{
			log.error("Failed to auto-save notes", e);
		}
	}

	public void scheduleLayoutSave()
	{
		if (layoutSaveTimer.isRunning())
		{
			layoutSaveTimer.restart();
		}
		else
		{
			layoutSaveTimer.start();
		}
	}


	public void saveEditorLayout()
	{
		try
		{
			EditorLayout layout = new EditorLayout();

			// Save current active state
			if (activePaneGroup != null)
			{
				layout.setActivePaneGroupId(activePaneGroup.getPaneGroupId());
			}
			if (activeNoteId != null)
			{
				layout.setActiveNoteId(activeNoteId);
			}

			// Save each pane group
			for (int i = 0; i < paneGroups.size(); i++)
			{
				NotePaneGroup paneGroup = paneGroups.get(i);
				EditorLayout.PaneGroupLayout paneLayout = new EditorLayout.PaneGroupLayout(
					paneGroup.getPaneGroupId(), i);

				// Get all note IDs from this pane group
				JTabbedPane tabbedPane = paneGroup.getTabbedPane();
				for (int j = 0; j < paneGroup.getTabCount(); j++)
				{
					String noteId = (String) tabbedPane.getClientProperty("noteId_" + j);
					if (noteId != null)
					{
						paneLayout.addNoteId(noteId);
					}
				}

				// Set active note for this pane
				if (paneGroup.getCurrentNoteId() != null)
				{
					paneLayout.setActiveNoteId(paneGroup.getCurrentNoteId());
				}

				layout.addPaneGroup(paneLayout);
			}

			// Save to storage
			storageService.saveEditorLayout(layout);


		}
		catch (Exception e)
		{
			log.warn("Failed to save editor layout", e);
		}
	}


	public void switchProfile(String newProfile)
	{
		try
		{
			log.debug("Switching to profile: {}", newProfile);

			// Save current state before switching
			try
			{
				saveAllNotes();
				saveEditorLayout();
				log.debug("Saved current profile state before switching");
			}
			catch (Exception e)
			{
				log.warn("Failed to save current state before profile switch", e);
			}

			SwingUtilities.invokeLater(() -> {
				try
				{
					// Clear all pane groups
					paneGroups.clear();
					mainContentPanel.removeAll();
					activePaneGroup = null;
					activeNoteId = null;

					// Create fresh initial pane group
					createInitialPaneGroup();

					// Load notes for new profile
					restoreEditorLayout();

					log.debug("Successfully switched to profile: {}", storageService.getDisplayProfileName());

				}
				catch (Exception e)
				{
					log.error("Error during profile switch UI update", e);
				}
			});

		}
		catch (Exception e)
		{
			log.error("Failed to switch profile", e);
		}
	}

	public void cleanup()
	{
		try
		{
			// Save everything before cleanup
			saveAllNotes();
			saveEditorLayout();

			// Stop auto-save timer
			if (autoSaveTimer != null)
			{
				autoSaveTimer.stop();
			}

			paneGroups.clear();

		}
		catch (Exception e)
		{
			log.error("Error during NotesPanel cleanup", e);
		}
	}
}