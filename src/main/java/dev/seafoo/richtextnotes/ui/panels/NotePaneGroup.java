package dev.seafoo.richtextnotes.ui.panels;

import dev.seafoo.richtextnotes.RichTextNotesConfig;
import dev.seafoo.richtextnotes.RichTextNotesPlugin;
import dev.seafoo.richtextnotes.models.Note;
import dev.seafoo.richtextnotes.models.NoteContentV2;
import dev.seafoo.richtextnotes.models.NoteMetadata;
import dev.seafoo.richtextnotes.services.CloudSyncService;
import dev.seafoo.richtextnotes.services.FileStorageService;
import dev.seafoo.richtextnotes.services.ItemIconService;
import dev.seafoo.richtextnotes.services.NoteSerializerV2;
import dev.seafoo.richtextnotes.ui.components.RichTextEditor;
import dev.seafoo.richtextnotes.ui.popups.NoteSelectionDialog;
import dev.seafoo.richtextnotes.ui.popups.TagEditDialog;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.StyledDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

/**
 * A single pane group that contains a tabbed interface for notes.
 * This can be split vertically to create multiple pane groups.
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class NotePaneGroup extends JPanel
{
	public static final ImageIcon SYNCED_ICON =
		new ImageIcon(ImageUtil.loadImageResource(RichTextNotesPlugin.class, "icons/cloud_synced.png"));
	private static final ImageIcon LOCAL_ICON =
		new ImageIcon(ImageUtil.loadImageResource(RichTextNotesPlugin.class, "icons/cloud_local.png"));
	// Typing stops this far under the size limit; the size estimate ignores formatting that typed text adds
	private static final int TYPING_SIZE_MARGIN = 256;

	private final NotesPanel parentPanel;
	private final FileStorageService storageService;
	private final RichTextNotesConfig config;
	private final ItemIconService itemIconService;

	// UI Components
	private JTabbedPane tabbedPane;

	// Note management for this pane group
	// Shared with every pane (NotesPanel.openNotes), so a note open in two panes is one Note
	private final Map<String, Note> loadedNotes;
	private final Map<String, NoteEditor> noteEditors = new ConcurrentHashMap<>();
	private String currentNoteId;
	private final String paneGroupId;
	// Set when the profile switches away; this pane's notes must no longer be saved or uploaded
	private volatile boolean closed;

	public NotePaneGroup(NotesPanel parentPanel, FileStorageService storageService,
						 RichTextNotesConfig config, String paneGroupId, ItemIconService itemIconService)
	{
		this.parentPanel = parentPanel;
		this.storageService = storageService;
		this.config = config;
		this.paneGroupId = paneGroupId;
		this.itemIconService = itemIconService;
		this.loadedNotes = parentPanel.getOpenNotes();
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

		JButton syncButton = createHeaderButton(null, LOCAL_ICON);
		syncButton.addActionListener(e -> {
			Note note = currentNoteId != null ? loadedNotes.get(currentNoteId) : null;
			if (note != null && note.getMetadata() != null)
			{
				setSynced(currentNoteId, !note.getMetadata().isSynced());
			}
		});
		actionsPanel.add(syncButton);

		JButton prevButton = createHeaderButton("<", null);
		prevButton.addActionListener(e -> navigateTab(-1));
		actionsPanel.add(prevButton);

		JButton nextButton = createHeaderButton(">", null);
		nextButton.addActionListener(e -> navigateTab(1));
		actionsPanel.add(nextButton);

		JButton menuButton = createHeaderButton("...", null);
		menuButton.setToolTipText("Menu");
		menuButton.addActionListener(e -> showPaneMenu(menuButton));
		actionsPanel.add(menuButton);

		header.add(actionsPanel, BorderLayout.EAST);

		// Store references
		header.putClientProperty("currentNoteLabel", currentNoteLabel);
		header.putClientProperty("prevButton", prevButton);
		header.putClientProperty("nextButton", nextButton);
		header.putClientProperty("syncButton", syncButton);

		return header;
	}

	private static JButton createHeaderButton(String text, Icon icon)
	{
		JButton button = new JButton(text, icon);
		button.setPreferredSize(new Dimension(22, 24));
		button.setMargin(new Insets(0, 0, 0, 0));
		return button;
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
			if (currentNoteId != null)
			{
				pushToCloud(currentNoteId);
			}
			tabbedPane.setSelectedIndex(newIndex);
			updateTabHeader();
		}
	}

	void updateTabHeader()
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
		JButton syncButton = (JButton) header.getClientProperty("syncButton");

		if (currentNoteLabel == null)
		{
			return;
		}

		int tabCount = tabbedPane.getTabCount();
		int selectedIndex = tabbedPane.getSelectedIndex();

		// Update current note display
		Note note = selectedIndex >= 0 && currentNoteId != null ? loadedNotes.get(currentNoteId) : null;
		if (selectedIndex >= 0 && currentNoteId != null)
		{
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

		if (syncButton != null)
		{
			updateSyncButton(syncButton, note);
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

		JMenuItem newNote = new JMenuItem("New Note");
		newNote.addActionListener(e -> createNewNote());
		menu.add(newNote);

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

		JMenuItem openDirectory = new JMenuItem("Show Notes Directory");
		openDirectory.addActionListener(e -> showNotesDirectory());
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


	private void showNotesDirectory()
	{
		JTextField path = new JTextField(storageService.getProfileDirectory().toString());
		path.setEditable(false);
		JOptionPane.showMessageDialog(this, path, "Notes Directory", JOptionPane.PLAIN_MESSAGE);
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
				pushToCloud(noteId);
				parentPanel.refreshHeaders(); // Update display to show tag count
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
			String noteId = storageService.saveNote(null, new NoteContentV2());

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
				NoteContentV2 contentV2 = storageService.loadContentV2(noteId);
				String rtfV1 = contentV2 == null ? storageService.loadRtfV1(noteId) : null;
				NoteMetadata metadata = storageService.loadNoteMetadata(noteId);
				note = new Note(noteId, contentV2, rtfV1, metadata);
				loadedNotes.put(noteId, note);
			}

			// Create editor for this note
			NoteEditor editor;
			RichTextEditor openElsewhere = parentPanel.findOpenEditor(noteId);
			if (openElsewhere != null)
			{
				editor = new NoteEditor(openElsewhere, config);
			}
			else
			{
				editor = new NoteEditor(note, parentPanel::scheduleAutoSave, itemIconService, config);
				limitSyncedNoteSize(noteId, editor.getTextPane());
			}
			noteEditors.put(noteId, editor);
			editor.getTextPane().addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					pushToCloud(noteId);
				}
			});

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
			pushToCloud(noteId);
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
		if (!parentPanel.isOpenInAnotherPane(noteId, this))
		{
			loadedNotes.remove(noteId);
		}

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
				pushToCloud(noteId);

				// Update tab display
				parentPanel.refreshHeaders();

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
				parentPanel.getCloudSyncService().remove(noteId);
				storageService.deleteNote(noteId);

				// Close it in every pane, so no other pane saves it back
				parentPanel.closeNoteInAllPanes(noteId);

				log.debug("Deleted note: {} from pane group: {}", noteId, paneGroupId);

			}
			catch (Exception e)
			{
				log.error("Failed to delete note", e);
			}
		}
	}

	private void updateSyncButton(JButton button, Note note)
	{
		if (note == null || note.getMetadata() == null)
		{
			button.setEnabled(false);
			button.setIcon(LOCAL_ICON);
			button.setToolTipText(null);
			return;
		}

		boolean synced = note.getMetadata().isSynced();
		CloudSyncService cloud = parentPanel.getCloudSyncService();
		int noteCount = cloud.getSyncedNoteCount();
		// Unknown (-1) for notes not yet saved as v2
		int size = note.getContentV2() != null ? cloud.getCloudSize(note.getMetadata(), note.getContentV2()) : -1;

		boolean tooManyNotes = !synced && noteCount >= CloudSyncService.MAX_SYNCED_NOTES;
		boolean tooLarge = size > CloudSyncService.MAX_NOTE_SIZE;
		String status;
		if (synced)
		{
			// Over the limit while synced: uploads pause until it shrinks
			status = tooLarge ? "Too large to sync" : "Synced to cloud";
		}
		else if (tooManyNotes)
		{
			status = "Note limit reached";
		}
		else if (tooLarge)
		{
			status = "Too large to sync";
		}
		else
		{
			status = "Local - click to sync";
		}

		StringBuilder tooltip = new StringBuilder("<html>").append(status)
			.append("<br>").append(noteCount).append("/").append(CloudSyncService.MAX_SYNCED_NOTES).append(" notes");
		if (size >= 0)
		{
			tooltip.append(String.format(" (%.1f/%d KB)", size / 1024.0, CloudSyncService.MAX_NOTE_SIZE / 1024));
		}
		tooltip.append("</html>");

		// Turning sync off always works; turning it on needs room
		button.setEnabled(synced || (!tooManyNotes && !tooLarge));
		button.setIcon(synced ? SYNCED_ICON : LOCAL_ICON);
		button.setToolTipText(tooltip.toString());
	}

	/**
	 * Stops typing or pasting once a synced note reaches the cloud size limit. Deleting still works.
	 * Installed after the note is loaded, so loading is never blocked.
	 */
	private void limitSyncedNoteSize(String noteId, JTextPane textPane)
	{
		((AbstractDocument) textPane.getDocument()).setDocumentFilter(new DocumentFilter()
		{
			@Override
			public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs) throws BadLocationException
			{
				replace(fb, offset, 0, text, attrs);
			}

			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
			{
				if (text == null || text.isEmpty() || fitsInCloud(noteId, (StyledDocument) fb.getDocument(), offset, length, text))
				{
					super.replace(fb, offset, length, text, attrs);
				}
				else
				{
					UIManager.getLookAndFeel().provideErrorFeedback(textPane);
				}
			}
		});
	}

	private boolean fitsInCloud(String noteId, StyledDocument doc, int offset, int removedLength, String inserted)
		throws BadLocationException
	{
		Note note = loadedNotes.get(noteId);
		if (note == null || note.getMetadata() == null || !note.getMetadata().isSynced())
		{
			return true;
		}

		// Estimate only: formatting offsets after the edit shift, which barely changes the size
		NoteContentV2 content = NoteSerializerV2.fromDocument(doc);
		String text = content.getText();
		content.setText(text.substring(0, offset) + inserted + text.substring(offset + removedLength));
		return parentPanel.getCloudSyncService().getCloudSize(note.getMetadata(), content) <= CloudSyncService.MAX_NOTE_SIZE - TYPING_SIZE_MARGIN;
	}

	private void setSynced(String noteId, boolean synced)
	{
		Note note = loadedNotes.get(noteId);
		if (note == null || note.getMetadata() == null)
		{
			return;
		}

		note.getMetadata().setSynced(synced);
		if (synced)
		{
			// Over a limit it never reached the cloud, so it goes back to being local
			note.setModified(true);
			if (!pushToCloud(noteId))
			{
				setSynced(noteId, false);
				return;
			}
			parentPanel.refreshHeaders(); // the synced note count changed
			return;
		}

		parentPanel.getCloudSyncService().remove(noteId);
		try
		{
			storageService.saveNoteMetadata(noteId, note.getMetadata());
		}
		catch (Exception e)
		{
			log.error("Failed to save sync setting for note: {}", noteId, e);
		}
		parentPanel.refreshHeaders();
	}

	/**
	 * Saves the note, then writes it to config if it's synced. Returns false if not synced or over a limit.
	 * Called on focus loss, tab switch/close and ConfigSync rather than every autosave, since each
	 * config write notifies every plugin.
	 */
	private boolean pushToCloud(String noteId)
	{
		saveNote(noteId);
		Note note = loadedNotes.get(noteId);
		if (closed || note == null || note.getContentV2() == null || note.getMetadata() == null || !note.getMetadata().isSynced())
		{
			return false;
		}

		try
		{
			parentPanel.getCloudSyncService().upload(note.getMetadata(), note.getContentV2());
			return true;
		}
		catch (CloudSyncService.SyncLimitException e)
		{
			// Over a limit: stay synced and keep the last cloud version; the sync button's tooltip says why
			log.debug("Not uploading note {}: {}", noteId, e.getMessage());
			return false;
		}
	}

	// Public methods for parent panel management

	/**
	 * Stops all saving and uploading from this pane. The notes folder and config may already
	 * belong to another profile by the time its editors lose focus.
	 */
	public void close()
	{
		closed = true;
	}

	public void saveNote(String noteId)
	{
		if (closed)
		{
			return;
		}

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
				note.setContentV2(editor.getContentV2());
			}
			if (note.getContentV2() == null)
			{
				return; // v1 note with no editor open, nothing new to write
			}

			// Save to storage
			storageService.saveNote(noteId, note.getContentV2());
			storageService.saveNoteMetadata(noteId, note.getMetadata());

			note.markSaved();
			parentPanel.refreshHeaders(); // Update to remove the * indicator

		}
		catch (Exception e)
		{
			log.error("Failed to save note: {}", noteId, e);
			throw new RuntimeException("Failed to save note", e);
		}
	}

	public RichTextEditor getEditor(String noteId)
	{
		NoteEditor editor = noteEditors.get(noteId);
		return editor != null ? editor.richTextEditor : null;
	}

	/**
	 * Closes the note's tab in this pane without saving it.
	 */
	void closeNoteTab(String noteId)
	{
		for (int i = 0; i < tabbedPane.getTabCount(); i++)
		{
			if (noteId.equals(tabbedPane.getClientProperty("noteId_" + i)))
			{
				closeTabInternal(i);
				return;
			}
		}
	}

	public void pushAllToCloud()
	{
		for (String noteId : noteEditors.keySet())
		{
			pushToCloud(noteId);
		}
	}

	public void saveAllNotes()
	{
		for (String noteId : noteEditors.keySet())
		{
			if (loadedNotes.get(noteId).isModified())
			{
				saveNote(noteId);
			}
		}
	}

	public boolean hasModifiedNotes()
	{
		return noteEditors.keySet().stream().map(loadedNotes::get).anyMatch(note -> note != null && note.isModified());
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
		private final dev.seafoo.richtextnotes.ui.components.RichTextEditor richTextEditor;
		private final Runnable saveCallback;
		private final RichTextNotesConfig config;

		// A second view of a note already open in another pane
		public NoteEditor(RichTextEditor openElsewhere, RichTextNotesConfig config)
		{
			this.note = openElsewhere.getNote();
			this.saveCallback = openElsewhere.getChangeCallback();
			this.config = config;
			this.richTextEditor = new RichTextEditor(openElsewhere, config);
		}

		public NoteEditor(Note note, Runnable saveCallback, ItemIconService itemIconService, RichTextNotesConfig config)
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

		public NoteContentV2 getContentV2() throws BadLocationException
		{
			return richTextEditor.getContentV2();
		}

		public JTextPane getTextPane()
		{
			return richTextEditor.getTextPane();
		}

		public void requestFocus()
		{
			richTextEditor.requestEditorFocus();
		}
	}

}