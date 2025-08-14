package dev.seafoo.notesenhanced.ui.panels;

import dev.seafoo.notesenhanced.NotesEnhancedConfig;
import dev.seafoo.notesenhanced.models.Note;
import dev.seafoo.notesenhanced.models.NoteMetadata;
import dev.seafoo.notesenhanced.services.FileStorageService;
import dev.seafoo.notesenhanced.services.ItemIconService;
import dev.seafoo.notesenhanced.ui.components.RichTextEditor;
import dev.seafoo.notesenhanced.ui.popups.NoteSelectionDialog;
import dev.seafoo.notesenhanced.ui.popups.TagEditDialog;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * A single pane group that contains a tabbed interface for notes.
 * This can be split vertically to create multiple pane groups.
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class NotePaneGroup extends JPanel
{

	private final NotesPanel parentPanel;
	private final FileStorageService storageService;
	private final NotesEnhancedConfig config;
	private final ItemIconService itemIconService;

	// UI Components
	private JTabbedPane tabbedPane;

	// Note management for this pane group
	private final Map<String, Note> loadedNotes = new ConcurrentHashMap<>();
	private final Map<String, NoteEditor> noteEditors = new ConcurrentHashMap<>();
	private String currentNoteId;
	private final String paneGroupId;

	public NotePaneGroup(NotesPanel parentPanel, FileStorageService storageService,
						 NotesEnhancedConfig config, String paneGroupId, ItemIconService itemIconService)
	{
		this.parentPanel = parentPanel;
		this.storageService = storageService;
		this.config = config;
		this.paneGroupId = paneGroupId;
		this.itemIconService = itemIconService;
		setupUI();
	}

	private void setupUI()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

		// Create compact tab header with navigation and actions
		JPanel tabHeader = createTabHeader();
		add(tabHeader, BorderLayout.NORTH);

		// Create tabbed pane
		tabbedPane = new JTabbedPane();
		tabbedPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		tabbedPane.setTabPlacement(JTabbedPane.TOP);

		// Hide the default tab area since we're using custom header
		tabbedPane.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI()
		{
			@Override
			protected int calculateTabAreaHeight(int tabPlacement, int horizRunCount, int maxTabHeight)
			{
				return 0; // Hide default tabs
			}

			@Override
			protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex)
			{
				// Don't paint default tab area
			}
		});

		// Add tab change listener
		tabbedPane.addChangeListener(e -> {
			int selectedIndex = tabbedPane.getSelectedIndex();
			if (selectedIndex >= 0)
			{
				String noteId = (String) tabbedPane.getClientProperty("noteId_" + selectedIndex);
				if (noteId != null)
				{
					currentNoteId = noteId;
					updateTabHeader();
					parentPanel.onActiveNoteChanged(this, noteId);
				}
			}
		});

		add(tabbedPane, BorderLayout.CENTER);
	}

	private JPanel createTabHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(3, 3, 3, 3));

		// Simple current note display
		JLabel currentNoteLabel = new JLabel("No note selected");
		currentNoteLabel.setForeground(Color.WHITE);
		currentNoteLabel.setFont(currentNoteLabel.getFont().deriveFont(Font.BOLD, 11f));

		// Add right-click menu to the header area
		MouseAdapter headerClickListener = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e))
				{
					showHeaderContextMenu(e);
				}
			}
		};
		currentNoteLabel.addMouseListener(headerClickListener);
		header.addMouseListener(headerClickListener);

		header.add(currentNoteLabel, BorderLayout.CENTER);

		// Simple action buttons
		JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		actionsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton prevButton = new JButton("<");
		prevButton.setPreferredSize(new Dimension(30, 24));
		prevButton.addActionListener(e -> navigateTab(-1));
		actionsPanel.add(prevButton);

		JButton nextButton = new JButton(">");
		nextButton.setPreferredSize(new Dimension(30, 24));
		nextButton.addActionListener(e -> navigateTab(1));
		actionsPanel.add(nextButton);

		JButton newButton = new JButton("+");
		newButton.setPreferredSize(new Dimension(30, 24));
		newButton.setToolTipText("New note");
		newButton.addActionListener(e -> createNewNote());
		actionsPanel.add(newButton);

		JButton menuButton = new JButton("...");
		menuButton.setPreferredSize(new Dimension(30, 24));
		menuButton.setToolTipText("Menu");
		menuButton.addActionListener(e -> showPaneMenu(menuButton));
		actionsPanel.add(menuButton);

		header.add(actionsPanel, BorderLayout.EAST);

		// Store references
		header.putClientProperty("currentNoteLabel", currentNoteLabel);
		header.putClientProperty("prevButton", prevButton);
		header.putClientProperty("nextButton", nextButton);

		return header;
	}

	private void navigateTab(int direction)
	{
		int tabCount = tabbedPane.getTabCount();
		if (tabCount == 0)
		{
			return;
		}

		int currentIndex = tabbedPane.getSelectedIndex();
		int newIndex = currentIndex + direction;

		if (newIndex >= 0 && newIndex < tabCount)
		{
			tabbedPane.setSelectedIndex(newIndex);
			updateTabHeader();
		}
	}

	private void updateTabHeader()
	{
		Component headerComponent = getComponent(0); // North component
		if (!(headerComponent instanceof JPanel))
		{
			return;
		}

		JPanel header = (JPanel) headerComponent;
		JLabel currentNoteLabel = (JLabel) header.getClientProperty("currentNoteLabel");
		JButton prevButton = (JButton) header.getClientProperty("prevButton");
		JButton nextButton = (JButton) header.getClientProperty("nextButton");

		if (currentNoteLabel == null)
		{
			return;
		}

		int tabCount = tabbedPane.getTabCount();
		int selectedIndex = tabbedPane.getSelectedIndex();

		// Update current note display
		if (selectedIndex >= 0 && currentNoteId != null)
		{
			Note note = loadedNotes.get(currentNoteId);
			if (note != null)
			{
				String title = note.getDisplayTitle();
				if (note.isModified())
				{
					title += " *";
				}

				// Show tab position if multiple tabs
				if (tabCount > 1)
				{
					title += String.format(" (%d/%d)", selectedIndex + 1, tabCount);
				}
				currentNoteLabel.setText(title);

				// Set the note title as tooltip
				currentNoteLabel.setToolTipText(note.getDisplayTitle());
			}
		}
		else
		{
			currentNoteLabel.setText("No note selected");
			currentNoteLabel.setToolTipText(null);
		}

		// Update navigation button states
		if (prevButton != null && nextButton != null)
		{
			prevButton.setEnabled(selectedIndex > 0);
			nextButton.setEnabled(selectedIndex >= 0 && selectedIndex < tabCount - 1);
		}

		this.parentPanel.scheduleLayoutSave();
	}

	private void showHeaderContextMenu(MouseEvent e)
	{
		if (currentNoteId == null)
		{
			return;
		}

		JPopupMenu menu = new JPopupMenu();

		JMenuItem closeTab = new JMenuItem("Close Current Tab");
		closeTab.addActionListener(ev -> {
			int currentIndex = tabbedPane.getSelectedIndex();
			if (currentIndex >= 0)
			{
				closeTab(currentIndex);
			}
		});
		closeTab.setEnabled(tabbedPane.getTabCount() > 0);
		menu.add(closeTab);

		menu.addSeparator();

		JMenuItem rename = new JMenuItem("Rename Note");
		rename.addActionListener(ev -> renameNote(currentNoteId));
		menu.add(rename);

		JMenuItem editTags = new JMenuItem("Edit Tags");
		editTags.addActionListener(ev -> editNoteTags(currentNoteId));
		menu.add(editTags);

		JMenuItem deleteNote = new JMenuItem("Delete Note");
		deleteNote.setForeground(Color.RED);
		deleteNote.addActionListener(ev -> {
			Note note = loadedNotes.get(currentNoteId);
			String noteTitle = note != null ? note.getDisplayTitle() : "Note";
			deleteNote(currentNoteId, noteTitle);
		});
		menu.add(deleteNote);

		menu.show((Component) e.getSource(), e.getX(), e.getY());
	}

	private void showPaneMenu(JButton menuButton)
	{
		JPopupMenu menu = new JPopupMenu();

		JMenuItem openExisting = new JMenuItem("Open Existing Note");
		openExisting.addActionListener(e -> showOpenNoteDialog());
		menu.add(openExisting);

		menu.addSeparator();

		JMenuItem closeTab = new JMenuItem("Close Current Tab");
		closeTab.addActionListener(ev -> {
			int currentIndex = tabbedPane.getSelectedIndex();
			if (currentIndex >= 0)
			{
				closeTab(currentIndex);
			}
		});
		closeTab.setEnabled(tabbedPane.getTabCount() > 0);
		menu.add(closeTab);

		menu.addSeparator();

		JMenuItem rename = new JMenuItem("Rename Note");
		rename.addActionListener(ev -> renameNote(currentNoteId));
		rename.setEnabled(tabbedPane.getTabCount() > 0);
		menu.add(rename);

		JMenuItem editTags = new JMenuItem("Edit Tags");
		editTags.addActionListener(ev -> editNoteTags(currentNoteId));
		editTags.setEnabled(tabbedPane.getTabCount() > 0);
		menu.add(editTags);

		JMenuItem deleteNote = new JMenuItem("Delete Note");
		deleteNote.setForeground(Color.RED);
		deleteNote.addActionListener(ev -> {
			Note note = loadedNotes.get(currentNoteId);
			String noteTitle = note != null ? note.getDisplayTitle() : "Note";
			deleteNote(currentNoteId, noteTitle);
		});
		deleteNote.setEnabled(tabbedPane.getTabCount() > 0);
		menu.add(deleteNote);

		menu.addSeparator();

		JMenuItem splitPane = new JMenuItem("Split Pane");
		splitPane.addActionListener(e -> parentPanel.splitPaneGroup());
		// Disable split pane if we already have the maximum number of pane groups
		splitPane.setEnabled(parentPanel.getPaneGroupCount() < config.maxPaneCount());
		if (parentPanel.getPaneGroupCount() >= config.maxPaneCount())
		{
			splitPane.setToolTipText("Maximum of " + config.maxPaneCount() + " panes allowed");
		}
		menu.add(splitPane);

		JMenuItem closePane = new JMenuItem("Close Pane");
		closePane.addActionListener(e -> parentPanel.closePaneGroup(this));
		closePane.setEnabled(parentPanel.getPaneGroupCount() > 1);
		menu.add(closePane);

		menu.addSeparator();

		JMenuItem openDirectory = new JMenuItem("Open Notes Directory");
		openDirectory.addActionListener(e -> openNotesDirectory());
		menu.add(openDirectory);

		menu.show(menuButton, 0, menuButton.getHeight());
	}


	private void showOpenNoteDialog()
	{
		try
		{
			java.util.List<NoteMetadata> allNotes = storageService.listNotesWithMetadata();

			if (allNotes.isEmpty())
			{
				JOptionPane.showMessageDialog(this, "No notes found.", "Open Note", JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			// Use the enhanced note selection dialog
			NoteSelectionDialog dialog =
				new NoteSelectionDialog(this, allNotes);

			dialog.setVisible(true);

			if (dialog.isOkPressed())
			{
				NoteMetadata selectedNote = dialog.getSelectedNote();
				if (selectedNote != null)
				{
					openNoteInTab(selectedNote.getNoteId(), true);
				}
			}

		}
		catch (Exception e)
		{
			log.error("Failed to show open note dialog", e);
		}
	}


	private void openNotesDirectory()
	{
		try
		{
			java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
			desktop.open(storageService.getProfileDirectory().toFile());
		}
		catch (Exception e)
		{
			log.error("Failed to open notes directory", e);

			String directoryPath = storageService.getNotesDirectory().toString();
			JOptionPane.showMessageDialog(
				this,
				"Failed to open directory automatically.\nNotes are stored at:\n" + directoryPath,
				"Notes Directory",
				JOptionPane.INFORMATION_MESSAGE
			);
		}
	}

	private void editNoteTags(String noteId)
	{
		Note note = loadedNotes.get(noteId);
		if (note == null || note.getMetadata() == null)
		{
			return;
		}

		NoteMetadata metadata = note.getMetadata();
		List<String> currentTags = metadata.getTags() != null ?
			new ArrayList<>(metadata.getTags()) : new ArrayList<>();

		TagEditDialog dialog = new TagEditDialog(this, currentTags, note.getDisplayTitle());
		dialog.setVisible(true);

		if (dialog.isOkPressed())
		{
			List<String> newTags = dialog.getTags();

			// Clear existing tags and add new ones
			if (metadata.getTags() != null)
			{
				metadata.getTags().clear();
			}

			for (String tag : newTags)
			{
				metadata.addTag(tag);
			}

			metadata.updateModified();

			try
			{
				storageService.saveNoteMetadata(noteId, metadata);
				updateTabHeader(); // Update display to show tag count
				log.debug("Updated tags for note {}: {}", noteId, newTags);
			}
			catch (Exception e)
			{
				log.error("Failed to save note tags", e);
			}
		}
	}

	public void createNewNote()
	{
		try
		{
			// Create new note with default content
			String defaultContent = "";
			String noteId = storageService.saveNote(null, defaultContent);

			// Create metadata
			NoteMetadata metadata = new NoteMetadata(
				noteId,
				"Untitled Note",
				LocalDateTime.now(),
				new ArrayList<>()
			);
			storageService.saveNoteMetadata(noteId, metadata);

			// Open in new tab
			openNoteInTab(noteId, true);

			log.debug("Created new note: {} in pane group: {}", noteId, paneGroupId);

		}
		catch (Exception e)
		{
			log.error("Failed to create new note", e);
		}
	}

	public void openNoteInTab(String noteId, boolean focus)
	{
		try
		{
			// Check if note is already open
			for (int i = 0; i < tabbedPane.getTabCount(); i++)
			{
				String existingNoteId = (String) tabbedPane.getClientProperty("noteId_" + i);
				if (noteId.equals(existingNoteId))
				{
					if (focus)
					{
						tabbedPane.setSelectedIndex(i);
						updateTabHeader();
					}
					return;
				}
			}

			// Load note if not already loaded
			Note note = loadedNotes.get(noteId);
			if (note == null)
			{
				String content = storageService.loadNote(noteId);
				NoteMetadata metadata = storageService.loadNoteMetadata(noteId);
				note = new Note(noteId, content, metadata);
				loadedNotes.put(noteId, note);
			}

			// Create editor for this note
			NoteEditor editor = new NoteEditor(note, parentPanel::scheduleAutoSave, itemIconService, config);
			noteEditors.put(noteId, editor);

			// Add tab to tabbedPane (hidden, just for content management)
			int tabIndex = tabbedPane.getTabCount();
			tabbedPane.addTab("", editor.getEditorComponent()); // Empty title since we use custom header
			tabbedPane.putClientProperty("noteId_" + tabIndex, noteId);

			if (currentNoteId == null)
			{
				currentNoteId = noteId;
			}

			// Focus if requested
			if (focus)
			{
				tabbedPane.setSelectedIndex(tabIndex);
				currentNoteId = noteId;
				editor.requestFocus();
			}

			updateTabHeader();

		}
		catch (Exception e)
		{
			log.error("Failed to open note in tab: {}", noteId, e);
		}
	}

	private void closeTab(int tabIndex)
	{
		String noteId = (String) tabbedPane.getClientProperty("noteId_" + tabIndex);
		if (noteId != null)
		{
			// Save note before closing
			saveNote(noteId);
			closeTabInternal(tabIndex);
		}
	}

	private void closeTabInternal(int tabIndex)
	{
		String noteId = (String) tabbedPane.getClientProperty("noteId_" + tabIndex);
		if (noteId == null)
		{
			return;
		}

		// Remove tab from tabbedPane
		tabbedPane.removeTabAt(tabIndex);

		// Update client properties for remaining tabs
		for (int i = tabIndex; i < tabbedPane.getTabCount(); i++)
		{
			String nextNoteId = (String) tabbedPane.getClientProperty("noteId_" + (i + 1));
			if (nextNoteId != null)
			{
				tabbedPane.putClientProperty("noteId_" + i, nextNoteId);
			}
		}
		// Remove the last property that's now unused
		tabbedPane.putClientProperty("noteId_" + tabbedPane.getTabCount(), null);

		// Clean up editor and memory
		noteEditors.remove(noteId);
		loadedNotes.remove(noteId);

		// Update current note
		if (noteId.equals(currentNoteId))
		{
			currentNoteId = tabbedPane.getTabCount() > 0 ?
				(String) tabbedPane.getClientProperty("noteId_0") : null;
		}

		updateTabHeader();
	}

	private void renameNote(String noteId)
	{
		Note note = loadedNotes.get(noteId);
		if (note == null)
		{
			return;
		}

		String currentTitle = note.getDisplayTitle();
		String newTitle = JOptionPane.showInputDialog(
			this,
			"Enter new title:",
			currentTitle
		);

		if (newTitle != null && !newTitle.trim().isEmpty() && !newTitle.equals(currentTitle))
		{
			try
			{
				note.getMetadata().setTitle(newTitle.trim());
				note.getMetadata().updateModified();
				storageService.saveNoteMetadata(noteId, note.getMetadata());

				// Update tab display
				updateTabHeader();

			}
			catch (Exception e)
			{
				log.error("Failed to rename note", e);
			}
		}
	}

	private void deleteNote(String noteId, String noteTitle)
	{
		int result = JOptionPane.showConfirmDialog(
			this,
			"Delete \"" + noteTitle + "\"?\nThis cannot be undone.",
			"Delete Note",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);

		if (result == JOptionPane.YES_OPTION)
		{
			try
			{
				// Remove from storage
				storageService.deleteNote(noteId);

				// Find and remove tab
				for (int i = 0; i < tabbedPane.getTabCount(); i++)
				{
					String tabNoteId = (String) tabbedPane.getClientProperty("noteId_" + i);
					if (noteId.equals(tabNoteId))
					{
						closeTabInternal(i);
						break;
					}
				}

				log.debug("Deleted note: {} from pane group: {}", noteId, paneGroupId);

			}
			catch (Exception e)
			{
				log.error("Failed to delete note", e);
			}
		}
	}

	// Public methods for parent panel management

	public void saveNote(String noteId)
	{
		try
		{
			Note note = loadedNotes.get(noteId);
			if (note == null || !note.isModified())
			{
				return;
			}

			// Get current content from editor
			NoteEditor editor = noteEditors.get(noteId);
			if (editor != null)
			{
				String currentContent = editor.getContent();
				note.setRtfContent(currentContent);
			}

			// Save to storage
			storageService.saveNote(noteId, note.getRtfContent());
			storageService.saveNoteMetadata(noteId, note.getMetadata());

			note.markSaved();
			updateTabHeader(); // Update to remove the * indicator

		}
		catch (Exception e)
		{
			log.error("Failed to save note: {}", noteId, e);
			throw new RuntimeException("Failed to save note", e);
		}
	}

	public void saveAllNotes()
	{
		for (String noteId : loadedNotes.keySet())
		{
			if (loadedNotes.get(noteId).isModified())
			{
				saveNote(noteId);
			}
		}
	}

	public boolean hasModifiedNotes()
	{
		return loadedNotes.values().stream().anyMatch(Note::isModified);
	}

	public int getTabCount()
	{
		return tabbedPane.getTabCount();
	}

	public void setActiveNote(String noteId)
	{
		if (noteId == null)
		{
			return;
		}

		// Find the tab with this note ID
		for (int i = 0; i < tabbedPane.getTabCount(); i++)
		{
			String tabNoteId = (String) tabbedPane.getClientProperty("noteId_" + i);
			if (noteId.equals(tabNoteId))
			{
				tabbedPane.setSelectedIndex(i);
				currentNoteId = noteId;
				updateTabHeader();
				return;
			}
		}

		log.warn("Could not set active note {} - note not found in this pane group", noteId);
	}

	// Inner class for note editing using the new RTF editor
	private static class NoteEditor
	{
		private final Note note;
		private final dev.seafoo.notesenhanced.ui.components.RichTextEditor richTextEditor;
		private final Runnable saveCallback;
		private final NotesEnhancedConfig config;

		public NoteEditor(Note note, Runnable saveCallback, ItemIconService itemIconService, NotesEnhancedConfig config)
		{
			this.note = note;
			this.saveCallback = saveCallback;
			this.config = config;
			this.richTextEditor = new RichTextEditor(
				note, saveCallback, itemIconService, config);
		}

		public JComponent getEditorComponent()
		{
			return richTextEditor;
		}

		public String getContent()
		{
			return richTextEditor.getContentAsRtf();
		}

		public void requestFocus()
		{
			richTextEditor.requestEditorFocus();
		}
	}

}