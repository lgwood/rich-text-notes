package dev.seafoo.richtextnotes.ui.components.clipboard;

import dev.seafoo.richtextnotes.services.ItemIconService;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TextAction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnhancedPasteAction extends TextAction
{

	private final ItemIconService itemIconService;

	public EnhancedPasteAction(ItemIconService itemIconService)
	{
		super("paste-from-clipboard");
		this.itemIconService = itemIconService;
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		JTextComponent target = getTextComponent(e);
		if (target == null || !target.isEditable())
		{
			return;
		}

		try
		{
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

			// First try to get our custom RTF data
			if (clipboard.isDataFlavorAvailable(NotesEnhancedTransferable.NOTES_ENHANCED_FLAVOR))
			{
				pasteNotesEnhancedRtf(target, clipboard);
			}
			else
			{
				// Fallback to plain text paste (strips all formatting)
				pastePlainTextOnly(target, clipboard);
			}

		}
		catch (Exception ex)
		{
			log.error("Failed to paste content", ex);
			// Final fallback to default paste
			target.paste();
		}
	}

	/**
	 * Paste RTF content using the existing RTF import mechanism
	 * This automatically converts placeholders back to icons
	 */
	private void pasteNotesEnhancedRtf(JTextComponent target, Clipboard clipboard) throws Exception
	{
		NotesEnhancedClipboardData data = (NotesEnhancedClipboardData)
			clipboard.getData(NotesEnhancedTransferable.NOTES_ENHANCED_FLAVOR);

		if (!(target instanceof JTextPane))
		{
			// Just paste plain text if not a JTextPane
			target.replaceSelection(data.getPlainText());
			return;
		}

		JTextPane textPane = (JTextPane) target;
		String rtfContent = data.getRtfContent();

		if (rtfContent == null || rtfContent.trim().isEmpty())
		{
			// No RTF content, use plain text with default formatting
			pastePlainTextWithDefaultFormatting(textPane, data.getPlainText());
			return;
		}

		// Use RTF import mechanism - this is atomic and handles icon restoration automatically
		pasteRtfContent(textPane, rtfContent);
	}

	/**
	 * Paste RTF content using the existing RTF reader
	 * This automatically converts icon placeholders back to icons
	 */
	private void pasteRtfContent(JTextPane textPane, String rtfContent) throws Exception
	{
		// Get current selection
		int selectionStart = textPane.getSelectionStart();
		int selectionEnd = textPane.getSelectionEnd();

		// Create a temporary document to load the RTF into
		StyledDocument tempDoc = (StyledDocument) textPane.getEditorKit().createDefaultDocument();

		// Load RTF content into temporary document
		ByteArrayInputStream bais = new ByteArrayInputStream(rtfContent.getBytes(StandardCharsets.UTF_8));
		textPane.getEditorKit().read(bais, tempDoc, 0);

		// Remove the trailing newline that RTF reader adds
		int tempLength = tempDoc.getLength();
		if (tempLength > 0)
		{
			String lastChar = tempDoc.getText(tempLength - 1, 1);
			if ("\n".equals(lastChar))
			{
				tempDoc.remove(tempLength - 1, 1);
			}
		}

		// Remove selected text first if any
		if (selectionStart != selectionEnd)
		{
			textPane.getDocument().remove(selectionStart, selectionEnd - selectionStart);
		}

		// Insert content using a simpler approach - transfer content directly
		if (tempDoc.getLength() > 0)
		{
			// Use the document's own transfer mechanism instead of element copying
			try
			{
				// Get all content from temp doc with attributes
				String plainText = tempDoc.getText(0, tempDoc.getLength());

				// Insert character by character to preserve formatting
				StyledDocument targetDoc = textPane.getStyledDocument();
				for (int i = 0; i < plainText.length(); i++)
				{
					AttributeSet attrs = tempDoc.getCharacterElement(i).getAttributes();
					String ch = plainText.substring(i, i + 1);
					targetDoc.insertString(selectionStart + i, ch, attrs);
				}
			}
			catch (BadLocationException e)
			{
				log.error("Failed to insert styled content", e);
				// Fallback to plain text
				textPane.replaceSelection(tempDoc.getText(0, tempDoc.getLength()));
			}
		}
	}

	/**
	 * Paste plain text with default formatting
	 */
	private void pastePlainTextWithDefaultFormatting(JTextPane textPane, String text) throws BadLocationException
	{
		if (text == null || text.isEmpty())
		{
			return;
		}

		// Use replaceSelection which is atomic
		int caretPos = textPane.getCaretPosition();
		textPane.replaceSelection(text);

		// Apply default formatting to the inserted text
		int newCaretPos = textPane.getCaretPosition();
		int textLength = newCaretPos - caretPos;

		if (textLength > 0)
		{
			StyledDocument doc = textPane.getStyledDocument();
			SimpleAttributeSet attrs = new SimpleAttributeSet();

			// Get current font family
			Font currentFont = textPane.getFont();
			if (currentFont != null)
			{
				StyleConstants.setFontFamily(attrs, currentFont.getFontName());
			}

			// Apply default styling
			StyleConstants.setFontSize(attrs, 16);
			StyleConstants.setForeground(attrs, Color.WHITE);
			StyleConstants.setBold(attrs, false);
			StyleConstants.setItalic(attrs, false);
			StyleConstants.setStrikeThrough(attrs, false);

			// Apply to the pasted text
			doc.setCharacterAttributes(caretPos, textLength, attrs, true);
		}
	}

	/**
	 * Fallback for plain text from external sources
	 */
	private void pastePlainTextOnly(JTextComponent target, Clipboard clipboard) throws Exception
	{
		if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor))
		{
			String text = (String) clipboard.getData(DataFlavor.stringFlavor);

			if (target instanceof JTextPane)
			{
				pastePlainTextWithDefaultFormatting((JTextPane) target, text);
			}
			else
			{
				target.replaceSelection(text);
			}
		}
	}
}
