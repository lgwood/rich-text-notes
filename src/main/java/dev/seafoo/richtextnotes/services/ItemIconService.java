package dev.seafoo.richtextnotes.services;

import dev.seafoo.richtextnotes.ui.components.AsyncImageIcon;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Service for managing item icons in notes
 */
@Slf4j
@Singleton
public class ItemIconService
{

	private static final int MAX_SEARCH_RESULTS = 60;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;


	/**
	 * Search items by name - main search method for toolbar functionality
	 * Uses ClientThread internally so it's safe to call from any thread
	 */
	public void searchItems(String query, Consumer<List<ItemSearchResult>> callback)
	{
		if (itemManager == null || clientThread == null)
		{
			log.warn("Item service not ready for searching");
			SwingUtilities.invokeLater(() -> callback.accept(new ArrayList<>()));
			return;
		}

		String search = query.toLowerCase().trim();
		if (search.isEmpty())
		{
			SwingUtilities.invokeLater(() -> callback.accept(new ArrayList<>()));
			return;
		}

		// Run search on client thread
		clientThread.invoke(() -> {
			try
			{
				List<ItemSearchResult> results = performItemSearch(search);

				// Return results on EDT
				SwingUtilities.invokeLater(() -> callback.accept(results));

			}
			catch (Exception e)
			{
				log.error("Error during item search", e);
				SwingUtilities.invokeLater(() -> callback.accept(new ArrayList<>()));
			}
		});
	}

	/**
	 * Core search logic based on ChatboxItemSearch.filterResults()
	 * Must be called on client thread
	 */
	private List<ItemSearchResult> performItemSearch(String search)
	{
		Map<Integer, ItemSearchResult> resultMap = new LinkedHashMap<>();
		Set<ItemIconData> seenIcons = new HashSet<>();

		try
		{
			// Iterate through items like ChatboxItemSearch does
			for (int i = 0; i < client.getItemCount() && resultMap.size() < MAX_SEARCH_RESULTS; i++)
			{
				try
				{
					net.runelite.api.ItemComposition itemComposition = itemManager.getItemComposition(itemManager.canonicalize(i));

					String name = itemComposition.getName().toLowerCase();

					// Same filtering logic as ChatboxItemSearch
					if (!"null".equals(name) && name.contains(search) && !resultMap.containsKey(itemComposition.getId()))
					{

						// Check for duplicate item images (same logic as ChatboxItemSearch)
						ItemIconData iconData = new ItemIconData(
							itemComposition.getInventoryModel(),
							itemComposition.getAmbient(),
							itemComposition.getContrast(),
							itemComposition.getColorToReplaceWith(),
							itemComposition.getTextureToReplaceWith()
						);

						if (seenIcons.contains(iconData))
						{
							continue; // Skip duplicate item images
						}

						seenIcons.add(iconData);
						ItemSearchResult result = new ItemSearchResult(
							itemComposition.getId(),
							itemComposition.getName(),
							itemComposition.getName().toLowerCase().replace(" ", "_")
						);
						resultMap.put(itemComposition.getId(), result);
					}

				}
				catch (Exception e)
				{
					log.error("Error in performItemSearch", e);
				}
			}

		}
		catch (Exception e)
		{
			log.error("Error in performItemSearch", e);
		}

		return new ArrayList<>(resultMap.values());
	}

	/**
	 * Get item image by item ID with default quantity of 1
	 */
	public AsyncBufferedImage getItemImageById(int itemId)
	{
		return getItemImageById(itemId, 1);
	}

	/**
	 * Get item image by item ID with specified quantity
	 */
	public AsyncBufferedImage getItemImageById(int itemId, int quantity)
	{
		if (itemManager == null)
		{
			return null;
		}

		try
		{
			// Ensure quantity is at least 1
			int validQuantity = Math.max(1, quantity);

			return itemManager.getImage(itemId, validQuantity, false);

		}
		catch (Exception e)
		{
			log.error("Failed to get image for item ID: {} with quantity: {}", itemId, quantity, e);
			return null;
		}
	}

	/**
	 * Create a centered icon for use in text documents
	 * This centers the icon vertically relative to the text baseline
	 */
	public Icon createCenteredIcon(Image image)
	{
		if (image == null)
		{
			return null;
		}
		return new CenteredImageIcon(image);
	}


	/**
	 * Get a centered icon by item ID with specified quantity
	 * This is the recommended method for getting icons for text insertion
	 */
	public Icon getCenteredIconById(int itemId, int quantity)
	{
		AsyncBufferedImage itemImage = getItemImageById(itemId, quantity);
		return createCenteredIcon(itemImage);
	}

	/**
	 * Item search result
	 */
	@Getter
	public static class ItemSearchResult
	{
		private final int id;
		private final String name;
		private final String searchName; // underscore version for inserting

		public ItemSearchResult(int id, String name, String searchName)
		{
			this.id = id;
			this.name = name;
			this.searchName = searchName;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	/**
	 * Item icon data for duplicate detection (same as ChatboxItemSearch.ItemIcon)
	 */
	private static class ItemIconData
	{
		final int modelId;
		final int ambient;
		final int contrast;
		final short[] colorsToReplace;
		final short[] texturesToReplace;

		ItemIconData(int modelId, int ambient, int contrast, short[] colorsToReplace, short[] texturesToReplace)
		{
			this.modelId = modelId;
			this.ambient = ambient;
			this.contrast = contrast;
			this.colorsToReplace = colorsToReplace;
			this.texturesToReplace = texturesToReplace;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
			{
				return true;
			}
			if (obj == null || getClass() != obj.getClass())
			{
				return false;
			}
			ItemIconData that = (ItemIconData) obj;
			return modelId == that.modelId &&
				ambient == that.ambient &&
				contrast == that.contrast &&
				java.util.Arrays.equals(colorsToReplace, that.colorsToReplace) &&
				java.util.Arrays.equals(texturesToReplace, that.texturesToReplace);
		}

		@Override
		public int hashCode()
		{
			int result = modelId;
			result = 31 * result + ambient;
			result = 31 * result + contrast;
			result = 31 * result + java.util.Arrays.hashCode(colorsToReplace);
			result = 31 * result + java.util.Arrays.hashCode(texturesToReplace);
			return result;
		}
	}

	/**
	 * Centered image icon that adjusts vertical positioning for text alignment
	 */
	public static class CenteredImageIcon implements Icon
	{
		private final Icon baseIcon;
		private Component attachedComponent;

		public CenteredImageIcon(Image image)
		{
			// Use AsyncImageIcon if it's an AsyncBufferedImage, otherwise ImageIcon
			if (image instanceof AsyncBufferedImage)
			{
				this.baseIcon = new AsyncImageIcon((AsyncBufferedImage) image);
			}
			else
			{
				this.baseIcon = new ImageIcon(image);
			}
		}

		/**
		 * Attach to a component for repaint on load
		 */
		public void attachToComponent(Component component)
		{
			this.attachedComponent = component;
			if (baseIcon instanceof AsyncImageIcon)
			{
				((AsyncImageIcon) baseIcon).attachToComponent(component);
			}
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			// Ensure we're attached to the component being painted
			if (c != attachedComponent)
			{
				attachToComponent(c);
			}

			FontMetrics fm = c.getFontMetrics(c.getFont());
			int iconHeight = baseIcon.getIconHeight();
			int fontAscent = fm.getAscent();

			// Calculate vertical offset to center icon with text baseline
			int yOffset = fontAscent - (iconHeight / 2);
			// Additional adjustment for better visual centering
			yOffset += 8;

			baseIcon.paintIcon(c, g, x, y + yOffset);
		}

		@Override
		public int getIconWidth()
		{
			return baseIcon.getIconWidth();
		}

		@Override
		public int getIconHeight()
		{
			return baseIcon.getIconHeight();
		}
	}

}