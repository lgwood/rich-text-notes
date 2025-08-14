package dev.seafoo.richtextnotes.ui.popups;

import dev.seafoo.richtextnotes.services.ItemIconService;
import dev.seafoo.richtextnotes.ui.components.AsyncImageIcon;
import dev.seafoo.richtextnotes.ui.components.NotesEnhancedDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * Discord-style inline autocomplete for item icons
 * Triggers when user types ":" followed by at least 2 characters
 */
@Slf4j
public class InlineItemAutoComplete
{

	private static final Pattern TRIGGER_PATTERN = Pattern.compile(":([a-zA-Z0-9_]{2,})$");
	private static final int MAX_SUGGESTIONS = 32;
	private static final int ITEM_HEIGHT = 28;
	private static final int POPUP_WIDTH = 180;

	private final JTextPane textPane;
	private final ItemIconService itemIconService;

	// Popup components
	private JPopupMenu popup;
	private JList<SuggestionItem> suggestionList;
	private DefaultListModel<SuggestionItem> listModel;
	private int triggerPosition = -1;
	private boolean isShowingPopup = false;

	public InlineItemAutoComplete(JTextPane textPane, ItemIconService itemIconService)
	{
		this.textPane = textPane;
		this.itemIconService = itemIconService;

		setupPopup();
		attachListeners();
	}

	private void setupPopup()
	{
		// Create popup menu
		popup = new JPopupMenu();
		popup.setBackground(ColorScheme.DARK_GRAY_COLOR);
		popup.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR, 1),
			BorderFactory.createEmptyBorder(2, 2, 2, 2)
		));
		popup.setFocusable(false);

		// Create list model and list
		listModel = new DefaultListModel<>();
		suggestionList = new JList<>(listModel);
		suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		suggestionList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		suggestionList.setForeground(Color.WHITE);
		suggestionList.setFixedCellHeight(ITEM_HEIGHT + 4);
		suggestionList.setCellRenderer(new SuggestionCellRenderer());

		// Setup list selection
		suggestionList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 1)
				{
					insertSelectedItem();
				}
			}
		});

		// Add hover effect
		suggestionList.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int index = suggestionList.locationToIndex(e.getPoint());
				if (index >= 0)
				{
					suggestionList.setSelectedIndex(index);
				}
			}
		});

		// Wrap in scroll pane
		JScrollPane scrollPane = new JScrollPane(suggestionList);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new Dimension(POPUP_WIDTH, 200));

		popup.add(scrollPane);
	}

	private void attachListeners()
	{
		// Document listener for text changes
		textPane.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				SwingUtilities.invokeLater(() -> checkForTrigger());
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				SwingUtilities.invokeLater(() -> checkForTrigger());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				// Attribute changes, ignore
			}
		});

		// Key listener for navigation
		textPane.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (isShowingPopup)
				{
					handlePopupKeyPress(e);
				}
			}
		});

		// Focus listener to hide popup
		textPane.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				// Hide popup when focus lost (unless focus went to popup)
				if (!popup.isFocusOwner() && !suggestionList.isFocusOwner())
				{
					hidePopup();
				}
			}
		});

		// Caret listener to hide popup when cursor moves away
		textPane.addCaretListener(e -> {
			if (isShowingPopup && triggerPosition >= 0)
			{
				int caretPos = e.getDot();
				// Hide if caret moved before the trigger
				if (caretPos < triggerPosition)
				{
					hidePopup();
				}
			}
		});
	}

	private void checkForTrigger()
	{
		try
		{
			int caretPos = textPane.getCaretPosition();
			if (caretPos == 0)
			{
				hidePopup();
				return;
			}

			// Get text before caret
			Document doc = textPane.getDocument();

			// Look back to find the start of the current word/trigger
			int searchStart = Math.max(0, caretPos - 50); // Look back up to 50 chars
			String textBefore = doc.getText(searchStart, caretPos - searchStart);

			// Check for trigger pattern
			Matcher matcher = TRIGGER_PATTERN.matcher(textBefore);
			String matchedQuery = null;
			int matchStart = -1;

			while (matcher.find())
			{
				matchedQuery = matcher.group(1);
				matchStart = searchStart + matcher.start();
			}

			if (matchedQuery != null)
			{
				// Found a trigger
				triggerPosition = matchStart;
				performSearch(matchedQuery);
			}
			else
			{
				// No trigger found
				hidePopup();
			}

		}
		catch (BadLocationException e)
		{
			log.error("Error checking for trigger", e);
			hidePopup();
		}
	}

	private void performSearch(String query)
	{
		if (itemIconService == null)
		{
			hidePopup();
			return;
		}

		// Replace spaces with underscores for the search
		String searchQuery = query.replace("_", " ");

		// Search for items
		itemIconService.searchItems(searchQuery, this::updateSuggestions);
	}

	private void updateSuggestions(List<ItemIconService.ItemSearchResult> results)
	{
		listModel.clear();

		if (results.isEmpty())
		{
			hidePopup();
			return;
		}

		// Add results to list (limit to MAX_SUGGESTIONS)
		int count = 0;
		for (ItemIconService.ItemSearchResult result : results)
		{
			if (count >= MAX_SUGGESTIONS)
			{
				break;
			}
			listModel.addElement(new SuggestionItem(result));
			count++;
		}

		// Select first item
		if (!listModel.isEmpty())
		{
			suggestionList.setSelectedIndex(0);
			showPopup();
		}
	}

	private void showPopup()
	{
		if (isShowingPopup)
		{
			// Just revalidate if already showing
			popup.revalidate();
			popup.repaint();
			return;
		}

		try
		{
			// Calculate popup position
			Rectangle caretBounds = (Rectangle) textPane.modelToView2D(textPane.getCaretPosition());
			if (caretBounds != null && textPane.isShowing())
			{

				// Adjust size based on content
				int itemCount = Math.min(listModel.size(), MAX_SUGGESTIONS);
				int popupHeight = Math.min(itemCount * ITEM_HEIGHT + 4, 300);
				popup.setPreferredSize(new Dimension(POPUP_WIDTH, popupHeight));

				// Show popup
				popup.show(textPane, caretBounds.x, caretBounds.y + caretBounds.height + 2);
				isShowingPopup = true;
			}
		}
		catch (BadLocationException e)
		{
			log.error("Error showing popup", e);
		}
	}

	private void hidePopup()
	{
		if (isShowingPopup)
		{
			popup.setVisible(false);
			isShowingPopup = false;
			triggerPosition = -1;
			listModel.clear();
		}
	}

	private void handlePopupKeyPress(KeyEvent e)
	{
		switch (e.getKeyCode())
		{
			case KeyEvent.VK_UP:
				e.consume();
				moveSelection(-1);
				break;

			case KeyEvent.VK_DOWN:
				e.consume();
				moveSelection(1);
				break;

			case KeyEvent.VK_ENTER:
			case KeyEvent.VK_TAB:
				e.consume();
				insertSelectedItem();
				break;

			case KeyEvent.VK_ESCAPE:
				e.consume();
				hidePopup();
				break;
		}
	}

	private void moveSelection(int direction)
	{
		if (listModel.isEmpty())
		{
			return;
		}

		int currentIndex = suggestionList.getSelectedIndex();
		int newIndex = currentIndex + direction;

		// Wrap around
		if (newIndex < 0)
		{
			newIndex = listModel.size() - 1;
		}
		else if (newIndex >= listModel.size())
		{
			newIndex = 0;
		}

		suggestionList.setSelectedIndex(newIndex);
		suggestionList.ensureIndexIsVisible(newIndex);
	}

	private void insertSelectedItem()
	{
		SuggestionItem selected = suggestionList.getSelectedValue();
		if (selected == null)
		{
			hidePopup();
			return;
		}

		try
		{
			Document doc = textPane.getDocument();

			// Calculate positions
			int replaceStart = triggerPosition;
			int replaceEnd = textPane.getCaretPosition();

			// Remove the trigger text (including the colon)
			doc.remove(replaceStart, replaceEnd - replaceStart);

			// Default quantity for autocomplete insertions
			int defaultQuantity = 1;

			// Insert the item icon with quantity
			Icon icon = itemIconService.getCenteredIconById(selected.result.getId(), defaultQuantity);
			if (icon != null)
			{
				// Attach icon for repaint
				if (icon instanceof ItemIconService.CenteredImageIcon)
				{
					((ItemIconService.CenteredImageIcon) icon).attachToComponent(textPane);
				}

				// Create attributes with icon and metadata
				SimpleAttributeSet attrs = new SimpleAttributeSet();
				StyleConstants.setIcon(attrs, icon);

				// Store metadata for saving/loading including quantity
				attrs.addAttribute("item-icon-data",
					new NotesEnhancedDocument.IconData(selected.result.getName(), selected.result.getId(), defaultQuantity));

				// Insert icon
				doc.insertString(replaceStart, " ", attrs);

				// Add a space after the icon
				doc.insertString(replaceStart + 1, " ", null);
			}

		}
		catch (BadLocationException e)
		{
			log.error("Error inserting item", e);
		}
		finally
		{
			hidePopup();
		}
	}

	/**
	 * Clean up resources
	 */
	public void cleanup()
	{
		hidePopup();
	}

	/**
	 * Suggestion item wrapper
	 */
	private static class SuggestionItem
	{
		final ItemIconService.ItemSearchResult result;

		SuggestionItem(ItemIconService.ItemSearchResult result)
		{
			this.result = result;
		}

		@Override
		public String toString()
		{
			return result.getName();
		}
	}

	/**
	 * Custom cell renderer for suggestions - simplified to remove shortcut label
	 */
	private class SuggestionCellRenderer extends DefaultListCellRenderer
	{
		private final JPanel panel;
		private final JLabel iconLabel;
		private final JLabel nameLabel;

		public SuggestionCellRenderer()
		{
			panel = new JPanel(new BorderLayout(5, 2));
			panel.setBorder(new EmptyBorder(2, 5, 2, 5));

			iconLabel = new JLabel();
			iconLabel.setPreferredSize(new Dimension(32, 28 + 4));
			iconLabel.setHorizontalAlignment(JLabel.CENTER);

			nameLabel = new JLabel();
			nameLabel.setFont(nameLabel.getFont().deriveFont(11f));

			panel.add(iconLabel, BorderLayout.WEST);
			panel.add(nameLabel, BorderLayout.CENTER);

		}

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value,
													  int index, boolean isSelected, boolean cellHasFocus)
		{

			if (value instanceof SuggestionItem)
			{
				SuggestionItem item = (SuggestionItem) value;

				// Set name
				nameLabel.setText(item.result.getName());


				try
				{
					net.runelite.client.util.AsyncBufferedImage itemImage =
						itemIconService.getItemImageById(item.result.getId()); // Increased icon size
					if (itemImage != null)
					{
						AsyncImageIcon icon = AsyncImageIcon.createAndAttach(itemImage, iconLabel);
						iconLabel.setIcon(icon);
						iconLabel.setText("");
					}
					else
					{
						iconLabel.setIcon(null);
						iconLabel.setText("?");
						iconLabel.setForeground(Color.GRAY);
					}
				}
				catch (Exception e)
				{
					iconLabel.setIcon(null);
					iconLabel.setText("?");
					iconLabel.setForeground(Color.GRAY);
				}

				// Set colors based on selection
				if (isSelected)
				{
					panel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
					nameLabel.setForeground(Color.WHITE);
					if (iconLabel.getIcon() == null)
					{
						iconLabel.setForeground(Color.WHITE);
					}
				}
				else
				{
					panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
					nameLabel.setForeground(Color.LIGHT_GRAY);
					if (iconLabel.getIcon() == null)
					{
						iconLabel.setForeground(Color.GRAY);
					}
				}
			}

			return panel;
		}
	}
}