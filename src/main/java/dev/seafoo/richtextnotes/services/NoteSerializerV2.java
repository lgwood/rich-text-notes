package dev.seafoo.richtextnotes.services;

import dev.seafoo.richtextnotes.models.NoteContentV2;
import dev.seafoo.richtextnotes.ui.components.NotesEnhancedDocument;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Converts between the editor's StyledDocument and NoteContentV2.
 * This is the only place that knows which Swing attributes a note keeps.
 */
public final class NoteSerializerV2
{
	private NoteSerializerV2()
	{
	}

	public static NoteContentV2 fromDocument(StyledDocument doc) throws BadLocationException
	{
		NoteContentV2 content = new NoteContentV2();
		content.setText(doc.getText(0, doc.getLength()));
		collect(doc.getDefaultRootElement(), doc.getLength(), content);
		return content;
	}

	private static void collect(Element element, int docLength, NoteContentV2 content)
	{
		if (!element.isLeaf())
		{
			for (int i = 0; i < element.getElementCount(); i++)
			{
				collect(element.getElement(i), docLength, content);
			}
			return;
		}

		int start = element.getStartOffset();
		// The last element covers Swing's implicit trailing newline, which isn't part of the text
		int end = Math.min(element.getEndOffset(), docLength);
		if (end <= start)
		{
			return;
		}

		AttributeSet attrs = element.getAttributes();

		Object iconData = attrs.getAttribute(NotesEnhancedDocument.ICON_ATTRIBUTE);
		if (iconData instanceof NotesEnhancedDocument.IconData)
		{
			NotesEnhancedDocument.IconData data = (NotesEnhancedDocument.IconData) iconData;
			// Adjacent icons can share one element, so record every character
			for (int offset = start; offset < end; offset++)
			{
				NoteContentV2.ItemIcon icon = new NoteContentV2.ItemIcon();
				icon.setOffset(offset);
				icon.setItemId(data.itemId);
				icon.setQuantity(data.itemQuantity);
				icon.setName(data.itemName);
				content.getIcons().add(icon);
			}
			return;
		}

		NoteContentV2.Span span = new NoteContentV2.Span();
		span.setStart(start);
		span.setLength(end - start);
		boolean hasStyle = false;

		if (Boolean.TRUE.equals(local(attrs, StyleConstants.Bold)))
		{
			span.setBold(true);
			hasStyle = true;
		}
		if (Boolean.TRUE.equals(local(attrs, StyleConstants.Italic)))
		{
			span.setItalic(true);
			hasStyle = true;
		}
		if (Boolean.TRUE.equals(local(attrs, StyleConstants.StrikeThrough)))
		{
			span.setStrike(true);
			hasStyle = true;
		}
		Object size = local(attrs, StyleConstants.FontSize);
		if (size instanceof Integer)
		{
			span.setSize((Integer) size);
			hasStyle = true;
		}
		Object color = local(attrs, StyleConstants.Foreground);
		if (color instanceof Color)
		{
			span.setColor(String.format("%06x", ((Color) color).getRGB() & 0xFFFFFF));
			hasStyle = true;
		}

		if (hasStyle)
		{
			content.getSpans().add(span);
		}
	}

	/**
	 * Only formatting set on the text itself. getAttribute would also return the document's
	 * default style, which Swing fills with the text pane's font and colour.
	 */
	private static Object local(AttributeSet attrs, Object key)
	{
		return attrs.isDefined(key) ? attrs.getAttribute(key) : null;
	}

	/**
	 * Replaces the document's contents with {@code content}.
	 * {@code iconService} may be null (e.g. in tests); icons then keep their data but have no image.
	 */
	public static void toDocument(NoteContentV2 content, StyledDocument doc, ItemIconService iconService)
		throws BadLocationException
	{
		String text = content.getText() != null ? content.getText() : "";
		doc.remove(0, doc.getLength());
		doc.insertString(0, text, null);

		// Spans and icons may come from another client, so skip anything missing or out of range
		List<NoteContentV2.Span> spans = content.getSpans() != null ? content.getSpans() : Collections.emptyList();
		List<NoteContentV2.ItemIcon> icons = content.getIcons() != null ? content.getIcons() : Collections.emptyList();
		for (NoteContentV2.Span span : spans)
		{
			if (span.getStart() < 0 || span.getLength() <= 0 || span.getStart() + span.getLength() > text.length())
			{
				continue;
			}

			SimpleAttributeSet attrs = new SimpleAttributeSet();
			if (Boolean.TRUE.equals(span.getBold()))
			{
				StyleConstants.setBold(attrs, true);
			}
			if (Boolean.TRUE.equals(span.getItalic()))
			{
				StyleConstants.setItalic(attrs, true);
			}
			if (Boolean.TRUE.equals(span.getStrike()))
			{
				StyleConstants.setStrikeThrough(attrs, true);
			}
			if (span.getSize() != null)
			{
				StyleConstants.setFontSize(attrs, span.getSize());
			}
			if (span.getColor() != null)
			{
				try
				{
					StyleConstants.setForeground(attrs, new Color(Integer.parseInt(span.getColor(), 16)));
				}
				catch (NumberFormatException ignored)
				{
				}
			}
			doc.setCharacterAttributes(span.getStart(), span.getLength(), attrs, false);
		}

		for (NoteContentV2.ItemIcon icon : icons)
		{
			if (icon.getOffset() < 0 || icon.getOffset() >= text.length())
			{
				continue;
			}

			SimpleAttributeSet attrs = new SimpleAttributeSet();
			Icon image = iconService != null ? iconService.getCenteredIconById(icon.getItemId(), icon.getQuantity()) : null;
			if (image != null)
			{
				StyleConstants.setIcon(attrs, image);
			}
			attrs.addAttribute(NotesEnhancedDocument.ICON_ATTRIBUTE,
				new NotesEnhancedDocument.IconData(icon.getName(), icon.getItemId(), icon.getQuantity()));
			doc.setCharacterAttributes(icon.getOffset(), 1, attrs, false);
		}
	}
}
