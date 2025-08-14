package dev.seafoo.richtextnotes.ui.components;

import dev.seafoo.richtextnotes.services.ItemIconService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom document that handles conversion between icons and text placeholders
 * for RTF saving/loading
 */
@Slf4j
public class NotesEnhancedDocument extends DefaultStyledDocument
{

	private static final String ICON_PLACEHOLDER_PREFIX = "{{ITEM:";
	private static final String ICON_PLACEHOLDER_SUFFIX = "}}";
	private static final String ICON_ATTRIBUTE = "item-icon-data";

	private boolean processingIcons = false;

	public NotesEnhancedDocument()
	{
		super();
	}


	/**
	 * Override fireUndoableEditUpdate to respect the undo tracking flag
	 */
	@Override
	protected void fireUndoableEditUpdate(UndoableEditEvent e)
	{
		if (!processingIcons)
		{
			super.fireUndoableEditUpdate(e);
		}
	}

	/**
	 * Convert all icons in the document to text placeholders
	 * Call this before saving to RTF
	 */
	public void convertIconsToPlaceholders()
	{
		if (processingIcons)
		{
			return;
		}

		processingIcons = true;
		try
		{
			List<IconPosition> iconPositions = findAllIcons();

			// Process in reverse order to maintain positions
			Collections.reverse(iconPositions);

			for (IconPosition iconPos : iconPositions)
			{
				try
				{
					// Remove the icon character
					remove(iconPos.position, 1);

					// Insert placeholder text with quantity
					String placeholder = createPlaceholder(iconPos.itemName, iconPos.itemId, iconPos.itemQuantity);
					insertString(iconPos.position, placeholder, null);

				}
				catch (BadLocationException e)
				{
					log.error("Failed to convert icon at position {}", iconPos.position, e);
				}
			}

			log.debug("Converted {} icons to placeholders", iconPositions.size());

		}
		finally
		{
			processingIcons = false;
		}
	}

	/**
	 * Convert all text placeholders back to icons
	 * Call this after loading from RTF
	 */
	public void convertPlaceholdersToIcons(ItemIconService itemIconService)
	{
		if (processingIcons || itemIconService == null)
		{
			return;
		}

		processingIcons = true;
		try
		{
			String text = getText(0, getLength());
			List<PlaceholderInfo> placeholders = findAllPlaceholders(text);

			// Process in reverse order to maintain positions
			Collections.reverse(placeholders);

			for (PlaceholderInfo placeholder : placeholders)
			{
				try
				{
					// Remove the placeholder text
					remove(placeholder.startPos, placeholder.length);

					// Insert icon using ItemIconService's centered icon with quantity
					SimpleAttributeSet attrs = new SimpleAttributeSet();

					// Get the centered icon from ItemIconService with quantity
					Icon centeredIcon = itemIconService.getCenteredIconById(placeholder.itemId, placeholder.itemQuantity);

					if (centeredIcon != null)
					{
						StyleConstants.setIcon(attrs, centeredIcon);

						// Store metadata for future conversion including quantity
						attrs.addAttribute(ICON_ATTRIBUTE,
							new IconData(placeholder.itemName, placeholder.itemId, placeholder.itemQuantity));

						insertString(placeholder.startPos, " ", attrs);

					}
					else
					{
						// If icon not found, keep the placeholder or insert item name with quantity
						String fallbackText = placeholder.itemQuantity > 1 ?
							"[" + placeholder.itemQuantity + "x " + placeholder.itemName + "]" :
							"[" + placeholder.itemName + "]";
						insertString(placeholder.startPos, fallbackText, null);
					}

				}
				catch (BadLocationException e)
				{
					log.error("Failed to restore icon for {}", placeholder.itemName, e);
				}
			}

			log.debug("Restored {} icons from placeholders", placeholders.size());

		}
		catch (BadLocationException e)
		{
			log.error("Failed to get document text", e);
		}
		finally
		{
			processingIcons = false;
		}
	}

	/**
	 * Find all icon positions in the document
	 */
	private List<IconPosition> findAllIcons()
	{
		List<IconPosition> positions = new ArrayList<>();

		Element root = getDefaultRootElement();
		scanElementForIcons(root, positions);

		return positions;
	}

	private void scanElementForIcons(Element element, List<IconPosition> positions)
	{
		if (element.isLeaf())
		{
			AttributeSet attrs = element.getAttributes();

			// Check if this element has an icon
			Object icon = attrs.getAttribute(StyleConstants.IconAttribute);
			if (icon != null)
			{
				// Try to get our custom icon data
				Object iconData = attrs.getAttribute(ICON_ATTRIBUTE);

				if (iconData instanceof IconData)
				{
					IconData data = (IconData) iconData;
					positions.add(new IconPosition(
						element.getStartOffset(),
						data.itemName,
						data.itemId,
						data.itemQuantity
					));
				}
				else
				{
					// Fallback - try to extract from icon filename or use default
					positions.add(new IconPosition(
						element.getStartOffset(),
						"unknown_item",
						0,
						1 // Default quantity
					));
				}
			}
		}
		else
		{
			// Recursively check child elements
			for (int i = 0; i < element.getElementCount(); i++)
			{
				scanElementForIcons(element.getElement(i), positions);
			}
		}
	}

	/**
	 * Find all placeholders in text
	 */
	private List<PlaceholderInfo> findAllPlaceholders(String text)
	{
		List<PlaceholderInfo> placeholders = new ArrayList<>();

		int searchStart = 0;
		while (searchStart < text.length())
		{
			int start = text.indexOf(ICON_PLACEHOLDER_PREFIX, searchStart);
			if (start == -1)
			{
				break;
			}

			int end = text.indexOf(ICON_PLACEHOLDER_SUFFIX, start);
			if (end == -1)
			{
				break;
			}

			end += ICON_PLACEHOLDER_SUFFIX.length();

			// Parse the placeholder
			String placeholder = text.substring(start, end);
			PlaceholderInfo info = parsePlaceholder(placeholder, start);

			if (info != null)
			{
				placeholders.add(info);
			}

			searchStart = end;
		}

		return placeholders;
	}

	private PlaceholderInfo parsePlaceholder(String placeholder, int position)
	{
		// Format: {{ITEM:itemId:itemName:itemQuantity}}
		if (!placeholder.startsWith(ICON_PLACEHOLDER_PREFIX) ||
			!placeholder.endsWith(ICON_PLACEHOLDER_SUFFIX))
		{
			return null;
		}

		String content = placeholder.substring(
			ICON_PLACEHOLDER_PREFIX.length(),
			placeholder.length() - ICON_PLACEHOLDER_SUFFIX.length()
		);

		String[] parts = content.split(":", 3);
		if (parts.length < 2)
		{
			return null;
		}

		try
		{
			int itemId = Integer.parseInt(parts[0]);
			String itemName = parts[1];
			int itemQuantity = 1; // Default quantity

			// Parse quantity if present
			if (parts.length >= 3)
			{
				try
				{
					itemQuantity = Integer.parseInt(parts[2]);
					if (itemQuantity < 1)
					{
						itemQuantity = 1;
					}
				}
				catch (NumberFormatException e)
				{
					log.warn("Invalid quantity in placeholder: {}, using default quantity 1", placeholder);
				}
			}

			return new PlaceholderInfo(
				position,
				placeholder.length(),
				itemName,
				itemId,
				itemQuantity
			);
		}
		catch (NumberFormatException e)
		{
			log.warn("Invalid placeholder format: {}", placeholder);
			return null;
		}
	}

	private String createPlaceholder(String itemName, int itemId, int itemQuantity)
	{
		return ICON_PLACEHOLDER_PREFIX + itemId + ":" + itemName + ":" + itemQuantity + ICON_PLACEHOLDER_SUFFIX;
	}

	// Helper classes

	private static class IconPosition
	{
		final int position;
		final String itemName;
		final int itemId;
		final int itemQuantity;

		IconPosition(int position, String itemName, int itemId, int itemQuantity)
		{
			this.position = position;
			this.itemName = itemName;
			this.itemId = itemId;
			this.itemQuantity = itemQuantity;
		}
	}

	private static class PlaceholderInfo
	{
		final int startPos;
		final int length;
		final String itemName;
		final int itemId;
		final int itemQuantity;

		PlaceholderInfo(int startPos, int length, String itemName, int itemId, int itemQuantity)
		{
			this.startPos = startPos;
			this.length = length;
			this.itemName = itemName;
			this.itemId = itemId;
			this.itemQuantity = itemQuantity;
		}
	}

	public static class IconData
	{
		public final String itemName;
		public final int itemId;
		public final int itemQuantity;

		public IconData(String itemName, int itemId, int itemQuantity)
		{
			this.itemName = itemName;
			this.itemId = itemId;
			this.itemQuantity = Math.max(1, itemQuantity); // Ensure minimum quantity of 1
		}
	}
}