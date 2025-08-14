package dev.seafoo.notesenhanced.ui.components.clipboard;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data container for Notes Enhanced clipboard operations
 * Stores text content, icon information, and formatting for proper copy/paste
 */
@Data
@NoArgsConstructor
public class NotesEnhancedClipboardData implements Serializable
{
	private static final long serialVersionUID = 2L; // Updated version

	// Plain text content (always available)
	private String plainText = "";

	// RTF content with formatting (if available)
	private String rtfContent = "";

	// Icon information for restoration
	private List<IconInfo> icons = new ArrayList<>();

	// Formatting information for text segments
	private List<FormattingRun> formatting = new ArrayList<>();

	/**
	 * Information about an icon in the clipboard content
	 */
	@Data
	@NoArgsConstructor
	public static class IconInfo implements Serializable
	{
		private static final long serialVersionUID = 1L;

		// Position in the text where this icon should be inserted
		public int position;

		// Item name (for display/fallback)
		public String itemName;

		// Item ID (for icon lookup)
		public int itemId;

		@Override
		public String toString()
		{
			return String.format("IconInfo{pos=%d, name='%s', id=%d}", position, itemName, itemId);
		}
	}

	/**
	 * Information about formatting for a segment of text
	 */
	@Data
	@NoArgsConstructor
	public static class FormattingRun implements Serializable
	{
		private static final long serialVersionUID = 1L;

		// Start position in the text
		public int startPosition;

		// Length of this formatting run
		public int length;

		// Formatting attributes
		public boolean bold = false;
		public boolean italic = false;
		public boolean strikethrough = false;
		public int fontSize = 16; // Default font size
		public Color textColor = Color.WHITE; // Default color

		@Override
		public String toString()
		{
			return String.format("FormattingRun{pos=%d, len=%d, bold=%s, italic=%s, strike=%s, size=%d}",
				startPosition, length, bold, italic, strikethrough, fontSize);
		}
	}
}