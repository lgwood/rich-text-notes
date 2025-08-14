package dev.seafoo.notesenhanced.ui.components;

import dev.seafoo.notesenhanced.NotesEnhancedConfig;
import dev.seafoo.notesenhanced.models.Note;
import dev.seafoo.notesenhanced.services.ItemIconService;
import dev.seafoo.notesenhanced.ui.popups.InlineItemAutoComplete;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * Rich Text Editor component for Notes Enhanced
 * Combines the NotesEnhancedEditorKit with FormattingToolbar
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class RichTextEditor extends JPanel
{

	private final Note note;
	private final Runnable changeCallback;

	// Components
	private NotesEnhancedEditorKit editorKit;
	private JTextPane textPane;
	private FormattingToolbar toolbar;
	private JScrollPane scrollPane;
	private ItemIconService itemIconService;
	private InlineItemAutoComplete autoComplete;
	private NotesEnhancedConfig config;

	// Undo/Redo support
	private UndoManager undoManager;

	// State tracking
	private boolean documentChanging = false;
	private boolean toolbarVisible = true; // Track toolbar visibility state


	public RichTextEditor(Note note, Runnable changeCallback, ItemIconService itemIconService, NotesEnhancedConfig config)
	{
		this.note = note;
		this.changeCallback = changeCallback;
		this.itemIconService = itemIconService;
		this.config = config;

		setupEditor();
		setupUndoRedo();
		loadContent();
		setupKeyboardShortcuts();
	}

	private void setupEditor()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Create the editor kit with icon service
		editorKit = new NotesEnhancedEditorKit(itemIconService);
		textPane = editorKit.createTextPane();

		// Configure text pane
		textPane.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Add focus listener to show/hide toolbar
		textPane.addFocusListener(new FocusListener()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				showToolbar();
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				// Small delay to prevent flickering when clicking on toolbar buttons
				SwingUtilities.invokeLater(() -> {
					// Check if focus went to a toolbar component
					Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
					if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, toolbar))
					{
						// Focus went to toolbar, don't hide it
						return;
					}
					hideToolbar();
				});
			}
		});

		// Create scroll pane
		scrollPane = new JScrollPane(textPane);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		scrollPane.setMinimumSize(new Dimension(100, 100));
		scrollPane.setPreferredSize(new Dimension(100, 100));

		// Create formatting toolbar
		toolbar = new FormattingToolbar(textPane, editorKit, itemIconService);

		// NEW: Create inline autocomplete if item service is available
		if (itemIconService != null)
		{
			autoComplete = new InlineItemAutoComplete(textPane, itemIconService);
		}

		// Add components - toolbar starts hidden
		add(toolbar, BorderLayout.NORTH);
		hideToolbar();
		add(scrollPane, BorderLayout.CENTER);

		// Add document listener for change tracking
		textPane.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onTextChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onTextChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				// This fires for attribute changes (formatting, icons, etc.)
				onTextChanged();
			}
		});
	}

	/**
	 * Setup undo/redo functionality
	 */
	private void setupUndoRedo()
	{
		// Create undo manager
		undoManager = new UndoManager();

		// Limit undo history to prevent memory issues
		undoManager.setLimit(100);

		// Add undoable edit listener to document
		textPane.getDocument().addUndoableEditListener(e -> {
			// Only add to undo history if we're not currently loading content
			if (!documentChanging)
			{
				undoManager.addEdit(e.getEdit());
			}
		});

		// Configure the undo/redo actions with our undo manager
		NotesEnhancedEditorKit.UndoAction undoAction =
			(NotesEnhancedEditorKit.UndoAction) editorKit.getCustomAction("undo");
		NotesEnhancedEditorKit.RedoAction redoAction =
			(NotesEnhancedEditorKit.RedoAction) editorKit.getCustomAction("redo");

		if (undoAction != null)
		{
			undoAction.setUndoManager(undoManager);
		}
		if (redoAction != null)
		{
			redoAction.setUndoManager(undoManager);
		}

	}

	/**
	 * Show the formatting toolbar
	 */
	private void showToolbar()
	{
		if (!toolbarVisible)
		{
			add(toolbar, BorderLayout.NORTH);
			toolbarVisible = true;
			revalidate();
			repaint();
			log.debug("Toolbar shown for note: {}", note != null ? note.getNoteId() : "unknown");
		}
	}

	/**
	 * Hide the formatting toolbar
	 */
	private void hideToolbar()
	{
		if (toolbarVisible && !config.alwaysShowToolbar())
		{
			remove(toolbar);
			toolbarVisible = false;
			revalidate();
			repaint();
			log.debug("Toolbar hidden for note: {}", note != null ? note.getNoteId() : "unknown");
		}
	}

	private void loadContent()
	{
		if (note == null || note.getRtfContent() == null)
		{
			return;
		}

		documentChanging = true;

		try
		{
			// Clear undo history when loading new content
			if (undoManager != null)
			{
				undoManager.discardAllEdits();
				log.debug("Cleared undo history for content load");
			}

			String content = note.getRtfContent();

			if (content.trim().isEmpty())
			{
				// Empty content, just clear the editor
				textPane.setText("");
			}
			else if (isRtfContent(content))
			{
				// Load RTF content - icons will be automatically restored
				loadRtfContent(content);
			}
			else
			{
				// Plain text content
				textPane.setText(content);
			}

		}
		catch (Exception e)
		{
			log.error("Failed to load note content", e);
			// Fallback to plain text
			textPane.setText(note.getRtfContent() != null ? note.getRtfContent() : "");
		}
		finally
		{
			documentChanging = false;

			// Reset the modified flag since we just loaded content
			if (note != null)
			{
				note.markSaved();
			}
		}
	}

	private boolean isRtfContent(String content)
	{
		return content.trim().startsWith("{\\rtf");
	}

	private void loadRtfContent(String rtfContent)
	{
		try
		{
			ByteArrayInputStream bais = new ByteArrayInputStream(rtfContent.getBytes(StandardCharsets.UTF_8));
			textPane.getEditorKit().read(bais, textPane.getDocument(), 0);
		}
		catch (Exception e)
		{
			log.warn("Failed to load RTF content, falling back to plain text", e);
			textPane.setText(rtfContent);
		}
	}

	private void setupKeyboardShortcuts()
	{
		int shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

		// Add common keyboard shortcuts
		InputMap inputMap = textPane.getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap actionMap = textPane.getActionMap();

		// Bold: Ctrl+B
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, shortcutKeyMask), "toggle-bold");
		actionMap.put("toggle-bold", editorKit.getCustomAction("toggle-bold"));

		// Italic: Ctrl+I
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, shortcutKeyMask), "toggle-italic");
		actionMap.put("toggle-italic", editorKit.getCustomAction("toggle-italic"));

		// Strikethrough: Ctrl+Shift+S
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutKeyMask | KeyEvent.SHIFT_DOWN_MASK), "toggle-strikethrough");
		actionMap.put("toggle-strikethrough", editorKit.getCustomAction("toggle-strikethrough"));

		// Undo: Ctrl+Z
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutKeyMask), "undo");
		actionMap.put("undo", editorKit.getCustomAction("undo"));

		// Redo: Ctrl+Y
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcutKeyMask), "redo");
		actionMap.put("redo", editorKit.getCustomAction("redo"));

		// Enhanced Copy: Ctrl+C
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutKeyMask), "copy-to-clipboard");
		actionMap.put("copy-to-clipboard", editorKit.getCustomAction("copy-to-clipboard"));

		// Enhanced Paste: Ctrl+V
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutKeyMask), "paste-from-clipboard");
		actionMap.put("paste-from-clipboard", editorKit.getCustomAction("paste-from-clipboard"));

		// Enhanced Cut: Ctrl+X
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcutKeyMask), "cut-to-clipboard");
		actionMap.put("cut-to-clipboard", editorKit.getCustomAction("cut-to-clipboard"));

		// Force enter to insert a break with modifiers
		inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
		inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "insert-break");
		inputMap.put(KeyStroke.getKeyStroke("ctrl shift ENTER"), "insert-break");
		inputMap.put(KeyStroke.getKeyStroke("alt ENTER"), "insert-break");
		inputMap.put(KeyStroke.getKeyStroke("meta ENTER"), "insert-break");
		inputMap.put(KeyStroke.getKeyStroke("meta shift ENTER"), "insert-break");


	}

	private void onTextChanged()
	{
		if (documentChanging || note == null)
		{
			return;
		}

		// Update the note content
		try
		{
			note.setModified(true);
		}
		catch (Exception e)
		{
			log.error("Error saving content", e);
		}

		// Notify change callback
		if (changeCallback != null)
		{
			SwingUtilities.invokeLater(changeCallback);
		}
	}

	/**
	 * Get the current content as RTF string
	 * Icons are automatically converted to placeholders during this process
	 */
	public String getContentAsRtf()
	{
		try
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			textPane.getEditorKit().write(baos, textPane.getDocument(), 0, textPane.getDocument().getLength());
			return baos.toString(String.valueOf(StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			log.warn("Failed to export RTF content, using plain text", e);
			return textPane.getText();
		}
	}

	/**
	 * Set content from RTF string
	 * Icons are automatically restored from placeholders during this process
	 */
	public void setContent(String rtfContent)
	{
		documentChanging = true;

		try
		{
			// Clear undo history when setting new content
			if (undoManager != null)
			{
				undoManager.discardAllEdits();
				log.debug("Cleared undo history for content change");
			}

			if (rtfContent == null || rtfContent.trim().isEmpty())
			{
				textPane.setText("");
			}
			else if (isRtfContent(rtfContent))
			{
				loadRtfContent(rtfContent);
			}
			else
			{
				textPane.setText(rtfContent);
			}
		}
		finally
		{
			documentChanging = false;
		}
	}

	/**
	 * Request focus on the text editor
	 */
	public void requestEditorFocus()
	{
		textPane.requestFocus();
	}


	/**
	 * Enable or disable the editor
	 */
	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		textPane.setEditable(enabled);
		toolbar.setEnabled(enabled);
	}
}