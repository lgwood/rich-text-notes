package dev.seafoo.notesenhanced.ui.components;

import dev.seafoo.notesenhanced.services.ItemIconService;
import dev.seafoo.notesenhanced.ui.components.clipboard.EnhancedCopyAction;
import dev.seafoo.notesenhanced.ui.components.clipboard.EnhancedCutAction;
import dev.seafoo.notesenhanced.ui.components.clipboard.EnhancedPasteAction;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JEditorPane;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.rtf.RTFEditorKit;
import javax.swing.undo.UndoManager;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * Enhanced RTF Editor Kit with custom formatting actions, undo/redo support,
 * and improved copy/paste functionality that preserves internal icons while
 * stripping external formatting
 */
@Slf4j
public class NotesEnhancedEditorKit extends RTFEditorKit
{

	// Font size constants - normal is 16pt base
	public static final int FONT_SIZE_SMALLER = 12;
	public static final int FONT_SIZE_SMALL = 14;
	public static final int FONT_SIZE_NORMAL = 16;
	public static final int FONT_SIZE_BIG = 20;
	public static final int FONT_SIZE_BIGGER = 24;
	public static final int FONT_SIZE_HUGE = 28;

	// Style constants
	public static final String STYLE_SMALLER = "smaller";
	public static final String STYLE_SMALL = "small";
	public static final String STYLE_NORMAL = "normal";
	public static final String STYLE_BIG = "big";
	public static final String STYLE_BIGGER = "bigger";
	public static final String STYLE_HUGE = "huge";

	private final Map<String, Action> customActions;
	private final ItemIconService itemIconService;

	public NotesEnhancedEditorKit(ItemIconService itemIconService)
	{
		super();
		this.itemIconService = itemIconService;
		customActions = new HashMap<>();
		initializeCustomActions();
	}

	/**
	 * Override to use our custom document
	 */
	@Override
	public Document createDefaultDocument()
	{
		return new NotesEnhancedDocument();
	}

	/**
	 * Override write to convert icons to placeholders before saving
	 */
	@Override
	public void write(OutputStream out, Document doc, int pos, int len)
		throws IOException, BadLocationException
	{

		if (doc instanceof NotesEnhancedDocument)
		{
			// Create a copy of the document for saving
			NotesEnhancedDocument copyDoc = createDocumentCopy((NotesEnhancedDocument) doc);

			try
			{
				// Convert icons to placeholders in the copy only
				copyDoc.convertIconsToPlaceholders();

				// Write the copy to RTF
				super.write(out, copyDoc, 0, copyDoc.getLength());

			}
			catch (Exception e)
			{
				log.error("Failed to save with icon conversion, trying original document", e);
				// Fallback to original document if copy fails
				super.write(out, doc, pos, len);
			}
		}
		else
		{
			// Fallback for non-enhanced documents
			super.write(out, doc, pos, len);
		}
	}

	/**
	 * Create a copy of the document with all content and formatting
	 */
	private NotesEnhancedDocument createDocumentCopy(NotesEnhancedDocument originalDoc) throws BadLocationException
	{
		NotesEnhancedDocument copyDoc = new NotesEnhancedDocument();
		try
		{
			// Copy all content from original to copy
			copyDocumentContent(originalDoc, copyDoc);
			return copyDoc;

		}
		catch (Exception e)
		{
			log.error("Failed to create document copy", e);
			throw new BadLocationException("Could not copy document", 0);
		}
	}

	/**
	 * Copy all content and formatting from source to destination document
	 */
	private void copyDocumentContent(StyledDocument sourceDoc, StyledDocument destDoc) throws BadLocationException
	{
		Element root = sourceDoc.getDefaultRootElement();
		copyElementContent(sourceDoc, destDoc, root, 0);
	}

	/**
	 * Recursively copy element content with all formatting and icons
	 */
	private int copyElementContent(StyledDocument sourceDoc, StyledDocument destDoc, Element element, int destPos)
		throws BadLocationException
	{

		if (element.isLeaf())
		{
			int elemStart = element.getStartOffset();
			int elemEnd = element.getEndOffset();

			if (elemEnd > elemStart)
			{
				AttributeSet attrs = element.getAttributes();

				// Check if this element contains an icon
				Object iconAttr = attrs.getAttribute(StyleConstants.IconAttribute);
				if (iconAttr != null)
				{
					// Copy icon with all its attributes
					destDoc.insertString(destPos, " ", attrs);
					return destPos + 1;
				}
				else
				{
					// Copy regular text with attributes
					String text = sourceDoc.getText(elemStart, elemEnd - elemStart);
					if (!text.isEmpty())
					{
						destDoc.insertString(destPos, text, attrs);
						return destPos + text.length();
					}
				}
			}
		}
		else
		{
			// Process child elements
			for (int i = 0; i < element.getElementCount(); i++)
			{
				destPos = copyElementContent(sourceDoc, destDoc, element.getElement(i), destPos);
			}
		}

		return destPos;
	}

	/**
	 * Override read to restore icons from placeholders after loading
	 */
	@Override
	public void read(InputStream in, Document doc, int pos)
		throws IOException, BadLocationException
	{

		// First do the regular RTF read
		super.read(in, doc, pos);

		// Then convert placeholders back to icons
		if (doc instanceof NotesEnhancedDocument && itemIconService != null)
		{
			NotesEnhancedDocument enhancedDoc = (NotesEnhancedDocument) doc;
			enhancedDoc.convertPlaceholdersToIcons(itemIconService);
		}
	}

	private void initializeCustomActions()
	{
		// Font size actions
		customActions.put("font-size-smaller", new FontSizeAction("font-size-smaller", FONT_SIZE_SMALLER));
		customActions.put("font-size-small", new FontSizeAction("font-size-small", FONT_SIZE_SMALL));
		customActions.put("font-size-normal", new FontSizeAction("font-size-normal", FONT_SIZE_NORMAL));
		customActions.put("font-size-big", new FontSizeAction("font-size-big", FONT_SIZE_BIG));
		customActions.put("font-size-bigger", new FontSizeAction("font-size-bigger", FONT_SIZE_BIGGER));
		customActions.put("font-size-huge", new FontSizeAction("font-size-huge", FONT_SIZE_HUGE));

		// Style toggle actions
		customActions.put("toggle-bold", new BoldAction());
		customActions.put("toggle-italic", new ItalicAction());
		customActions.put("toggle-strikethrough", new StrikeThroughAction());

		// Color action
		customActions.put("set-color", new ColorAction());

		// Undo/Redo actions - will be configured with UndoManager later
		customActions.put("undo", new UndoAction());
		customActions.put("redo", new RedoAction());

		// Enhanced Copy/Paste actions
		customActions.put("copy-to-clipboard", new EnhancedCopyAction());
		customActions.put("paste-from-clipboard", new EnhancedPasteAction(itemIconService));
		customActions.put("cut-to-clipboard", new EnhancedCutAction());
	}

	@Override
	public Action[] getActions()
	{
		Action[] superActions = super.getActions();
		Action[] allActions = new Action[superActions.length + customActions.size()];

		System.arraycopy(superActions, 0, allActions, 0, superActions.length);

		int index = superActions.length;
		for (Action action : customActions.values())
		{
			allActions[index++] = action;
		}

		return allActions;
	}

	/**
	 * Get a custom action by name
	 */
	public Action getCustomAction(String actionName)
	{
		return customActions.get(actionName);
	}

	/**
	 * Create a JTextPane configured for Notes Enhanced
	 */
	public JTextPane createTextPane()
	{
		JTextPane textPane = new JTextPane();
		textPane.setEditorKit(this);

		// Use our custom document
		textPane.setDocument(createDefaultDocument());

		textPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		textPane.setForeground(Color.WHITE);
		textPane.setCaretColor(Color.WHITE);
		textPane.setSelectedTextColor(Color.WHITE);

		initializeStyles(textPane.getStyledDocument(), textPane.getFont());

		// Override default copy/paste actions
		setupEnhancedCopyPaste(textPane);

		MutableAttributeSet inputAttrs = textPane.getInputAttributes();
		StyleConstants.setForeground(inputAttrs, Color.WHITE);

		return textPane;
	}

	/**
	 * Setup enhanced copy/paste functionality
	 */
	private void setupEnhancedCopyPaste(JTextPane textPane)
	{
		// Get the action map and input map
		ActionMap actionMap = textPane.getActionMap();

		// Override the default actions
		actionMap.put(DefaultEditorKit.copyAction, getCustomAction("copy-to-clipboard"));
		actionMap.put(DefaultEditorKit.pasteAction, getCustomAction("paste-from-clipboard"));
		actionMap.put(DefaultEditorKit.cutAction, getCustomAction("cut-to-clipboard"));

		actionMap.put("copy", getCustomAction("copy-to-clipboard"));
		actionMap.put("paste", getCustomAction("paste-from-clipboard"));
		actionMap.put("cut", getCustomAction("cut-to-clipboard"));
	}

	/**
	 * Initialize default styles for the document, preserving RuneLite's font family
	 */
	private void initializeStyles(StyledDocument doc, Font baseFont)
	{
		Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

		// Get RuneLite's font family and preserve it
		String fontFamily = baseFont != null ? baseFont.getFontName() : "Dialog";

		// Base style - preserve RuneLite font family, use normal size (16pt) as default
		Style base = doc.addStyle("base", defaultStyle);
		StyleConstants.setFontFamily(base, fontFamily);
		StyleConstants.setFontSize(base, FONT_SIZE_NORMAL);

		// Normal text style (16pt) - this is the default
		Style normal = doc.addStyle(STYLE_NORMAL, base);
		StyleConstants.setFontSize(normal, FONT_SIZE_NORMAL);

		// Smaller sizes
		Style smaller = doc.addStyle(STYLE_SMALLER, base);
		StyleConstants.setFontSize(smaller, FONT_SIZE_SMALLER);

		Style small = doc.addStyle(STYLE_SMALL, base);
		StyleConstants.setFontSize(small, FONT_SIZE_SMALL);

		Style big = doc.addStyle(STYLE_BIG, base);
		StyleConstants.setFontSize(big, FONT_SIZE_BIG);

		Style bigger = doc.addStyle(STYLE_BIGGER, base);
		StyleConstants.setFontSize(bigger, FONT_SIZE_BIGGER);

		Style huge = doc.addStyle(STYLE_HUGE, base);
		StyleConstants.setFontSize(huge, FONT_SIZE_HUGE);
	}


	/**
	 * Action to change font size while preserving font family
	 */
	public static class FontSizeAction extends StyledEditorKit.StyledTextAction
	{
		private final int fontSize;

		public FontSizeAction(String name, int fontSize)
		{
			super(name);
			this.fontSize = fontSize;
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			JEditorPane editor = getEditor(e);
			if (editor != null)
			{
				StyledEditorKit kit = getStyledEditorKit(editor);
				MutableAttributeSet attr = kit.getInputAttributes();

				// Preserve the current font family, only change size
				String currentFontFamily = StyleConstants.getFontFamily(attr);
				StyleConstants.setFontSize(attr, fontSize);
				if (currentFontFamily != null)
				{
					StyleConstants.setFontFamily(attr, currentFontFamily);
				}

				setCharacterAttributes(editor, attr, false);
			}
		}
	}

	/**
	 * Bold toggle action that preserves font family
	 */
	public static class BoldAction extends StyledEditorKit.StyledTextAction
	{
		public BoldAction()
		{
			super("toggle-bold");
			putValue(Action.NAME, "Bold");
			putValue(Action.SHORT_DESCRIPTION, "Toggle bold formatting");
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			JEditorPane editor = getEditor(e);
			if (editor != null)
			{
				StyledEditorKit kit = getStyledEditorKit(editor);
				MutableAttributeSet attr = kit.getInputAttributes();

				// Preserve font family
				String currentFontFamily = StyleConstants.getFontFamily(attr);
				boolean isBold = StyleConstants.isBold(attr);
				StyleConstants.setBold(attr, !isBold);
				if (currentFontFamily != null)
				{
					StyleConstants.setFontFamily(attr, currentFontFamily);
				}

				setCharacterAttributes(editor, attr, false);
			}
		}
	}

	/**
	 * Italic toggle action that preserves font family
	 */
	public static class ItalicAction extends StyledEditorKit.StyledTextAction
	{
		public ItalicAction()
		{
			super("toggle-italic");
			putValue(Action.NAME, "Italic");
			putValue(Action.SHORT_DESCRIPTION, "Toggle italic formatting");
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			JEditorPane editor = getEditor(e);
			if (editor != null)
			{
				StyledEditorKit kit = getStyledEditorKit(editor);
				MutableAttributeSet attr = kit.getInputAttributes();

				// Preserve font family
				String currentFontFamily = StyleConstants.getFontFamily(attr);
				boolean isItalic = StyleConstants.isItalic(attr);
				StyleConstants.setItalic(attr, !isItalic);
				if (currentFontFamily != null)
				{
					StyleConstants.setFontFamily(attr, currentFontFamily);
				}

				setCharacterAttributes(editor, attr, false);
			}
		}
	}

	/**
	 * Strikethrough toggle action that preserves font family
	 */
	public static class StrikeThroughAction extends StyledEditorKit.StyledTextAction
	{
		public StrikeThroughAction()
		{
			super("toggle-strikethrough");
			putValue(Action.NAME, "Strikethrough");
			putValue(Action.SHORT_DESCRIPTION, "Toggle strikethrough formatting");
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			JEditorPane editor = getEditor(e);
			if (editor != null)
			{
				StyledEditorKit kit = getStyledEditorKit(editor);
				MutableAttributeSet attr = kit.getInputAttributes();

				// Preserve font family
				String currentFontFamily = StyleConstants.getFontFamily(attr);
				boolean isStrikeThrough = StyleConstants.isStrikeThrough(attr);
				StyleConstants.setStrikeThrough(attr, !isStrikeThrough);
				if (currentFontFamily != null)
				{
					StyleConstants.setFontFamily(attr, currentFontFamily);
				}

				setCharacterAttributes(editor, attr, false);
			}
		}
	}

	/**
	 * Color action that preserves font family
	 */
	public static class ColorAction extends StyledEditorKit.StyledTextAction
	{
		public ColorAction()
		{
			super("set-color");
			putValue(Action.NAME, "Color");
			putValue(Action.SHORT_DESCRIPTION, "Set text color");
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			JEditorPane editor = getEditor(e);
			if (editor != null)
			{
				StyledEditorKit kit = getStyledEditorKit(editor);
				MutableAttributeSet attr = kit.getInputAttributes();

				// Get the color from the action value
				Color color = (Color) getValue("color");
				if (color != null)
				{
					// Preserve font family
					String currentFontFamily = StyleConstants.getFontFamily(attr);
					StyleConstants.setForeground(attr, color);
					if (currentFontFamily != null)
					{
						StyleConstants.setFontFamily(attr, currentFontFamily);
					}

					setCharacterAttributes(editor, attr, false);
				}
			}
		}
	}

	/**
	 * Undo action - will be configured with UndoManager by RichTextEditor
	 */
	@Setter
	public static class UndoAction extends AbstractAction
	{
		private UndoManager undoManager;

		public UndoAction()
		{
			super("undo");
			putValue(Action.NAME, "Undo");
			putValue(Action.SHORT_DESCRIPTION, "Undo last action");
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			if (undoManager != null && undoManager.canUndo())
			{
				try
				{
					undoManager.undo();
				}
				catch (Exception ex)
				{
					log.warn("Failed to undo", ex);
				}
			}
		}

		@Override
		public boolean isEnabled()
		{
			return undoManager != null && undoManager.canUndo();
		}
	}

	/**
	 * Redo action - will be configured with UndoManager by RichTextEditor
	 */
	@Setter
	public static class RedoAction extends AbstractAction
	{
		private UndoManager undoManager;

		public RedoAction()
		{
			super("redo");
			putValue(Action.NAME, "Redo");
			putValue(Action.SHORT_DESCRIPTION, "Redo last undone action");
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			if (undoManager != null && undoManager.canRedo())
			{
				try
				{
					undoManager.redo();
				}
				catch (Exception ex)
				{
					log.warn("Failed to redo", ex);
				}
			}
		}

		@Override
		public boolean isEnabled()
		{
			return undoManager != null && undoManager.canRedo();
		}
	}

	// Utility methods (unchanged)

	/**
	 * Utility method to apply a named style to the current selection while preserving font family
	 */
	public static void applyStyle(JTextPane textPane, String styleName)
	{
		StyledDocument doc = textPane.getStyledDocument();
		Style style = doc.getStyle(styleName);

		if (style != null)
		{
			int start = textPane.getSelectionStart();
			int length = textPane.getSelectionEnd() - start;

			// Get the current font family to preserve it
			String currentFontFamily;
			if (length > 0)
			{
				AttributeSet currentAttrs = doc.getCharacterElement(start).getAttributes();
				currentFontFamily = StyleConstants.getFontFamily(currentAttrs);
			}
			else
			{
				AttributeSet inputAttrs = textPane.getInputAttributes();
				currentFontFamily = StyleConstants.getFontFamily(inputAttrs);
			}

			// Create a copy of the style with preserved font family
			SimpleAttributeSet preservedStyle = new SimpleAttributeSet(style);
			if (currentFontFamily != null && !currentFontFamily.isEmpty())
			{
				StyleConstants.setFontFamily(preservedStyle, currentFontFamily);
			}

			if (length > 0)
			{
				doc.setCharacterAttributes(start, length, preservedStyle, false);
			}
			else
			{
				// Apply to current input attributes
				textPane.setCharacterAttributes(preservedStyle, false);
			}
		}
	}

	/**
	 * Get the current font size at the caret position
	 */
	public static int getCurrentFontSize(JTextPane textPane)
	{
		AttributeSet attrs = textPane.getCharacterAttributes();
		return StyleConstants.getFontSize(attrs);
	}

	/**
	 * Get the current text color at the caret position
	 */
	public static Color getCurrentTextColor(JTextPane textPane)
	{
		AttributeSet attrs = textPane.getCharacterAttributes();
		Color color = StyleConstants.getForeground(attrs);
		return color != null ? color : Color.WHITE; // Default to white if no color set
	}
}