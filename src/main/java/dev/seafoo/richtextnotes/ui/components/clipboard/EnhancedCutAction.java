package dev.seafoo.richtextnotes.ui.components.clipboard;

import dev.seafoo.richtextnotes.ui.components.NotesEnhancedDocument;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TextAction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnhancedCutAction extends TextAction
{
	public EnhancedCutAction()
	{
		super("cut-to-clipboard");
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
			int selectionStart = target.getSelectionStart();
			int selectionEnd = target.getSelectionEnd();

			if (selectionStart == selectionEnd)
			{
				// Nothing selected, do nothing
				return;
			}

			if (!(target instanceof JTextPane))
			{
				// Fallback for non-JTextPane components
				target.cut();
				return;
			}

			JTextPane textPane = (JTextPane) target;

			// Extract RTF content using the same mechanism as copy
			String rtfContent = extractSelectionAsRtf(textPane, selectionStart, selectionEnd);
			String plainText = target.getSelectedText();

			// Create clipboard data
			NotesEnhancedClipboardData clipboardData = new NotesEnhancedClipboardData();
			clipboardData.setRtfContent(rtfContent);
			clipboardData.setPlainText(plainText != null ? plainText : "");

			// Create transferable
			NotesEnhancedTransferable transferable = new NotesEnhancedTransferable(clipboardData);

			// Set to system clipboard
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(transferable, null);

			// After successful copy, delete the selected text (this is atomic)
			target.replaceSelection("");

			log.debug("Enhanced cut completed using RTF export");

		}
		catch (Exception ex)
		{
			log.error("Failed to cut content", ex);
			// Fallback to default behavior
			target.cut();
		}
	}

	/**
	 * Extract selected content as RTF using manual RTF fragment building
	 * This avoids the extra \par tags from document-level RTF export
	 */
	private String extractSelectionAsRtf(JTextPane textPane, int start, int end)
	{
		try
		{
			StyledDocument doc = textPane.getStyledDocument();

			// First pass: collect all colors used in selection
			Map<Color, Integer> colorMap = new HashMap<>();
			collectColorsInRange(doc, start, end, colorMap);

			// Build minimal RTF fragment
			StringBuilder rtf = new StringBuilder();
			rtf.append("{\\rtf1\\ansi\\deff0 ");

			// Add minimal font table (preserve current font)
			Font currentFont = textPane.getFont();
			String fontName = currentFont != null ? currentFont.getFontName() : "Dialog";
			rtf.append("{\\fonttbl\\f0\\fmodern ").append(fontName).append(";}");

			// Add color table with all colors found
			if (!colorMap.isEmpty())
			{
				rtf.append("{\\colortbl;"); // First entry is default (auto)
				for (Map.Entry<Color, Integer> entry : colorMap.entrySet())
				{
					Color color = entry.getKey();
					rtf.append("\\red").append(color.getRed())
						.append("\\green").append(color.getGreen())
						.append("\\blue").append(color.getBlue()).append(";");
				}
				rtf.append("}");
			}

			// Extract content for the selection range
			extractRtfContentFragment(doc, start, end, rtf, colorMap);

			rtf.append("}");
			return rtf.toString();

		}
		catch (Exception e)
		{
			log.error("Failed to extract RTF fragment", e);
			return "";
		}
	}

	/**
	 * First pass: collect all colors used in the selection range
	 */
	private void collectColorsInRange(StyledDocument doc, int selStart, int selEnd, Map<Color, Integer> colorMap)
		throws BadLocationException
	{

		Element root = doc.getDefaultRootElement();
		collectColorsInElement(doc, root, selStart, selEnd, colorMap);
	}

	private void collectColorsInElement(StyledDocument doc, Element element, int selStart, int selEnd, Map<Color, Integer> colorMap)
	{

		if (element.isLeaf())
		{
			int elemStart = element.getStartOffset();
			int elemEnd = element.getEndOffset();

			// Check if this element overlaps with selection
			if (elemEnd <= selStart || elemStart >= selEnd)
			{
				return;
			}

			AttributeSet attrs = element.getAttributes();
			Color color = StyleConstants.getForeground(attrs);

			// Add color to map if not already present
			if (color != null && !colorMap.containsKey(color))
			{
				colorMap.put(color, colorMap.size() + 1); // Index starts at 1 (0 is auto)
			}
		}
		else
		{
			// Process child elements
			for (int i = 0; i < element.getElementCount(); i++)
			{
				collectColorsInElement(doc, element.getElement(i), selStart, selEnd, colorMap);
			}
		}
	}

	/**
	 * Extract RTF content for a specific range without document structure
	 */
	private void extractRtfContentFragment(StyledDocument doc, int selStart, int selEnd, StringBuilder rtf, Map<Color, Integer> colorMap)
		throws BadLocationException
	{

		Element root = doc.getDefaultRootElement();
		extractElementContentAsRtf(doc, root, selStart, selEnd, rtf, colorMap);
	}

	/**
	 * Recursively extract element content as RTF fragment
	 */
	private void extractElementContentAsRtf(StyledDocument doc, Element element,
											int selStart, int selEnd, StringBuilder rtf, Map<Color, Integer> colorMap)
		throws BadLocationException
	{

		if (element.isLeaf())
		{
			int elemStart = element.getStartOffset();
			int elemEnd = element.getEndOffset();

			// Check if this element overlaps with selection
			if (elemEnd <= selStart || elemStart >= selEnd)
			{
				return;
			}

			// Calculate overlap
			int overlapStart = Math.max(elemStart, selStart);
			int overlapEnd = Math.min(elemEnd, selEnd);

			AttributeSet attrs = element.getAttributes();

			// Check if this element contains an icon
			Object iconAttr = attrs.getAttribute(StyleConstants.IconAttribute);
			if (iconAttr != null)
			{
				Object iconData = attrs.getAttribute("item-icon-data");
				if (iconData instanceof NotesEnhancedDocument.IconData)
				{
					NotesEnhancedDocument.IconData data = (NotesEnhancedDocument.IconData) iconData;
					String placeholder = "{{ITEM:" + data.itemId + ":" + data.itemName + ":" + data.itemQuantity + "}}";
					rtf.append(escapeRtfText(placeholder));
				}
				else
				{
					rtf.append("[icon]"); // Fallback
				}
			}
			else
			{
				// Regular text element - add formatting codes
				String text = doc.getText(overlapStart, overlapEnd - overlapStart);
				if (!text.isEmpty())
				{
					// Add RTF formatting codes based on attributes
					appendRtfFormatting(attrs, rtf, colorMap);
					rtf.append(escapeRtfText(text));
					closeRtfFormatting(attrs, rtf);
				}
			}
		}
		else
		{
			// Process child elements
			for (int i = 0; i < element.getElementCount(); i++)
			{
				extractElementContentAsRtf(doc, element.getElement(i), selStart, selEnd, rtf, colorMap);
			}
		}
	}

	/**
	 * Add RTF formatting codes for the given attributes
	 */
	private void appendRtfFormatting(AttributeSet attrs, StringBuilder rtf, Map<Color, Integer> colorMap)
	{
		// Bold
		if (StyleConstants.isBold(attrs))
		{
			rtf.append("\\b ");
		}

		// Italic
		if (StyleConstants.isItalic(attrs))
		{
			rtf.append("\\i ");
		}

		// Strikethrough
		if (StyleConstants.isStrikeThrough(attrs))
		{
			rtf.append("\\strike ");
		}

		// Font size
		int fontSize = StyleConstants.getFontSize(attrs);
		if (fontSize > 0)
		{
			rtf.append("\\fs").append(fontSize * 2).append(" "); // RTF uses half-points
		}

		// Text color - use the color map index
		Color color = StyleConstants.getForeground(attrs);
		if (color != null && colorMap.containsKey(color))
		{
			int colorIndex = colorMap.get(color);
			rtf.append("\\cf").append(colorIndex).append(" ");
		}
	}

	/**
	 * Close RTF formatting codes for the given attributes
	 */
	private void closeRtfFormatting(AttributeSet attrs, StringBuilder rtf)
	{
		// Close in reverse order
		if (StyleConstants.isBold(attrs))
		{
			rtf.append("\\b0 ");
		}
		if (StyleConstants.isItalic(attrs))
		{
			rtf.append("\\i0 ");
		}
		if (StyleConstants.isStrikeThrough(attrs))
		{
			rtf.append("\\strike0 ");
		}
	}

	/**
	 * Escape special RTF characters
	 */
	private String escapeRtfText(String text)
	{
		return text.replace("\\", "\\\\")
			.replace("{", "\\{")
			.replace("}", "\\}")
			.replace("\n", "\\par") // Convert newlines to spaces instead of \par
			.replace("\r", "");
	}

}