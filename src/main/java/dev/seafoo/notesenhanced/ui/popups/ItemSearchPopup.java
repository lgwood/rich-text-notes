package dev.seafoo.notesenhanced.ui.popups;

import dev.seafoo.notesenhanced.services.ItemIconService;
import dev.seafoo.notesenhanced.ui.components.AsyncImageIcon;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * Discord-style emoji picker popup for item selection
 */
@Slf4j
public class ItemSearchPopup extends JPopupMenu
{

	// Result callback
	private Consumer<ItemSearchResult> resultCallback;

	// UI Components
	private final JTextField searchField;
	private final JTextField quantityField;
	private final JPanel itemGridPanel;

	// Services
	private final ItemIconService itemIconService;

	// Category system
	private String currentCategory = "Search";
	private final java.util.List<CategoryItem> currentSearchResults = new ArrayList<>();

	// Grid settings
	private static final int GRID_COLUMNS = 6;
	private static final int ITEM_PADDING = 2;
	private static final int POPUP_WIDTH = 220;
	private static final int POPUP_HEIGHT = 260;

	public ItemSearchPopup(ItemIconService itemIconService)
	{
		this.itemIconService = itemIconService;
		this.itemGridPanel = new JPanel();
		this.searchField = new JTextField();
		this.quantityField = new JTextField("1");

		setupPopup();
		setupComponents();
		setupPopupHandling();
	}


	private void setupPopup()
	{
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR, 1),
			BorderFactory.createEmptyBorder(1, 1, 1, 1)
		));
		setFocusable(true);
	}

	private void setupComponents()
	{
		// Create main content panel
		JPanel mainContentPanel = new JPanel(new BorderLayout());
		mainContentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		mainContentPanel.setPreferredSize(new Dimension(POPUP_WIDTH, POPUP_HEIGHT));

		// Compact search field with quantity
		JPanel searchPanel = createCompactSearchPanel();
		mainContentPanel.add(searchPanel, BorderLayout.NORTH);

		// Content area
		JPanel contentPanel = new JPanel(new BorderLayout());
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Item grid
		setupItemGrid();
		JScrollPane gridScrollPane = new JScrollPane(itemGridPanel);
		gridScrollPane.setBorder(BorderFactory.createEmptyBorder());
		gridScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		gridScrollPane.getVerticalScrollBar().setUnitIncrement(12);
		gridScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		gridScrollPane.setPreferredSize(new Dimension(POPUP_WIDTH - 10, POPUP_HEIGHT - 10));
		contentPanel.add(gridScrollPane, BorderLayout.CENTER);

		mainContentPanel.add(contentPanel, BorderLayout.CENTER);

		// Add main content to popup menu
		add(mainContentPanel);

		// Start with search category
		showSearchCategory();
	}

	private JPanel createCompactSearchPanel()
	{
		JPanel searchPanel = new JPanel(new BorderLayout());
		searchPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchPanel.setBorder(new EmptyBorder(8, 8, 5, 8));

		// Create main search area
		JPanel searchInputPanel = new JPanel(new BorderLayout(5, 0));
		searchInputPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Search field (main area)
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setForeground(Color.WHITE);
		searchField.setCaretColor(Color.WHITE);
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR),
			new EmptyBorder(6, 8, 6, 8)
		));
		searchField.setFont(searchField.getFont().deriveFont(11f));

		// Placeholder text
		searchField.setText("Search items...");
		searchField.setForeground(Color.GRAY);

		searchField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				if ("Search items...".equals(searchField.getText()))
				{
					searchField.setText("");
					searchField.setForeground(Color.WHITE);
				}
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				if (searchField.getText().trim().isEmpty())
				{
					searchField.setText("Search items...");
					searchField.setForeground(Color.GRAY);
				}
			}
		});

		// Real-time search
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				performSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				performSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				performSearch();
			}
		});

		// Escape to close
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					setVisible(false);
				}
			}
		});

		searchInputPanel.add(searchField, BorderLayout.CENTER);

		// Quantity panel (right side)
		JPanel quantityPanel = new JPanel(new BorderLayout(3, 0));
		quantityPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel qtyLabel = new JLabel("Qty:");
		qtyLabel.setForeground(Color.WHITE);
		qtyLabel.setFont(qtyLabel.getFont().deriveFont(10f));
		quantityPanel.add(qtyLabel, BorderLayout.WEST);

		// Quantity field
		quantityField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		quantityField.setForeground(Color.WHITE);
		quantityField.setCaretColor(Color.WHITE);
		quantityField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR),
			new EmptyBorder(6, 6, 6, 6)
		));
		quantityField.setFont(quantityField.getFont().deriveFont(11f));
		quantityField.setPreferredSize(new Dimension(40, 25));
		quantityField.setHorizontalAlignment(JTextField.CENTER);

		// Validate quantity input - only allow numbers
		quantityField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyTyped(KeyEvent e)
			{
				char c = e.getKeyChar();
				if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE && c != KeyEvent.VK_DELETE)
				{
					e.consume();
				}
			}

			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					setVisible(false);
				}
			}
		});

		quantityPanel.add(quantityField, BorderLayout.CENTER);

		searchInputPanel.add(quantityPanel, BorderLayout.EAST);
		searchPanel.add(searchInputPanel, BorderLayout.CENTER);

		return searchPanel;
	}

	private void setupItemGrid()
	{
		itemGridPanel.setLayout(new GridBagLayout());
		itemGridPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		itemGridPanel.setBorder(new EmptyBorder(2, 4, 4, 4));
	}


	private void showSearchCategory()
	{
		String searchText = searchField.getText().trim();
		if (searchText.isEmpty() || "Search items...".equals(searchText))
		{
			showPlaceholder("Type to search...");
		}
	}


	private void showPlaceholder(String message)
	{
		itemGridPanel.removeAll();

		JLabel placeholder = new JLabel(message, JLabel.CENTER);
		placeholder.setForeground(Color.LIGHT_GRAY);
		placeholder.setFont(placeholder.getFont().deriveFont(Font.ITALIC, 11f));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.anchor = GridBagConstraints.CENTER;

		itemGridPanel.add(placeholder, gbc);
		itemGridPanel.revalidate();
		itemGridPanel.repaint();
	}

	private void displayItemsInGrid(java.util.List<CategoryItem> items)
	{
		itemGridPanel.removeAll();

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(ITEM_PADDING, ITEM_PADDING, ITEM_PADDING, ITEM_PADDING);

		int col = 0;
		int row = 0;

		for (CategoryItem item : items)
		{
			JButton itemButton = createItemButton(item);

			gbc.gridx = col;
			gbc.gridy = row;
			gbc.weightx = 0;
			gbc.weighty = 0;
			gbc.anchor = GridBagConstraints.CENTER;

			itemGridPanel.add(itemButton, gbc);

			col++;
			if (col >= GRID_COLUMNS)
			{
				col = 0;
				row++;
			}
		}

		// Add spacer to push items to top
		gbc.gridx = 0;
		gbc.gridy = row + 1;
		gbc.gridwidth = GRID_COLUMNS + 1;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		itemGridPanel.add(Box.createGlue(), gbc);

		itemGridPanel.revalidate();
		itemGridPanel.repaint();
	}

	private JButton createItemButton(CategoryItem item)
	{
		JButton button = new JButton();
		button.setPreferredSize(new Dimension(32, 28));
		button.setMinimumSize(new Dimension(32, 28));
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR, 1));
		button.setToolTipText(item.displayName);

		// Try to load icon with async handling
		try
		{
			net.runelite.client.util.AsyncBufferedImage itemImage =
				itemIconService.getItemImageById(item.itemId);
			if (itemImage != null)
			{
				// Use AsyncImageIcon and attach to button for proper loading
				AsyncImageIcon icon = AsyncImageIcon.createAndAttach(itemImage, button);
				button.setIcon(icon);
			}
			else
			{
				// Fallback text display
				button.setText(item.displayName.substring(0, Math.min(2, item.displayName.length())));
				button.setFont(button.getFont().deriveFont(9f));
				button.setForeground(Color.WHITE);
			}
		}
		catch (Exception e)
		{
			button.setText(item.displayName.substring(0, Math.min(2, item.displayName.length())));
			button.setFont(button.getFont().deriveFont(9f));
			button.setForeground(Color.WHITE);
		}

		// Click handler
		button.addActionListener(e -> selectItem(item));

		// Hover effect
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				button.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR, 1));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				button.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR, 1));
			}
		});

		return button;
	}

	private void performSearch()
	{
		String query = searchField.getText().trim();

		if (query.isEmpty() || "Search items...".equals(query))
		{
			if ("Search".equals(currentCategory))
			{
				showPlaceholder("Type to search...");
			}
			return;
		}

		if (itemIconService == null)
		{
			showPlaceholder("Service not ready...");
			return;
		}

		// Use ItemIconService for search
		itemIconService.searchItems(query, results -> {
			// Convert to CategoryItem list (limit for popup)
			currentSearchResults.clear();
			int count = 0;
			for (ItemIconService.ItemSearchResult result : results)
			{
				if (count >= 60)
				{
					break; // Limit results for popup
				}
				currentSearchResults.add(new CategoryItem(
					result.getName(),
					result.getId(),
					result.getSearchName()
				));
				count++;
			}

			// Only update grid if we're still in search mode
			if ("Search".equals(currentCategory))
			{
				if (currentSearchResults.isEmpty())
				{
					showPlaceholder("No items found");
				}
				else
				{
					displayItemsInGrid(currentSearchResults);
				}
			}
		});
	}

	private void selectItem(CategoryItem item)
	{
		if (resultCallback != null)
		{
			int quantity = parseQuantityFromField();
			resultCallback.accept(new ItemSearchResult(item.itemId, item.searchName, quantity));
		}
		setVisible(false);
	}

	/**
	 * Parse quantity from the quantity field, defaulting to 1 if invalid
	 */
	private int parseQuantityFromField()
	{
		try
		{
			String qtyText = quantityField.getText().trim();
			if (qtyText.isEmpty())
			{
				return 1;
			}

			int quantity = Integer.parseInt(qtyText);
			return Math.max(1, quantity); // Ensure minimum quantity of 1
		}
		catch (NumberFormatException e)
		{
			return 1; // Default to 1 if parsing fails
		}
	}

	private void setupPopupHandling()
	{
		addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
				// Focus the search field when popup becomes visible
				SwingUtilities.invokeLater(() -> {
					searchField.requestFocusInWindow();
					if ("Search items...".equals(searchField.getText()))
					{
						searchField.selectAll();
					}
				});
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
				// Reset state when popup closes
				currentSearchResults.clear();
				quantityField.setText("1"); // Reset quantity to 1
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
				// Handle cancel
			}
		});
	}

	/**
	 * Show the popup at the specified location
	 */
	public void showPopup(Component invoker, int x, int y, Consumer<ItemSearchResult> callback)
	{
		this.resultCallback = callback;

		// Reset state
		currentSearchResults.clear();
		searchField.setText("Search items...");
		searchField.setForeground(Color.GRAY);
		quantityField.setText("1"); // Reset quantity to 1

		// Start with search category
		showSearchCategory();

		// Show the popup menu
		show(invoker, x, y);
	}

	/**
	 * Result class for popup return value
	 */
	@Getter
	public static class ItemSearchResult
	{
		private final int itemId;
		private final String itemName;
		private final int quantity;

		public ItemSearchResult(int itemId, String itemName, int quantity)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity); // Ensure minimum quantity of 1
		}

	}

	/**
	 * Internal class for category items
	 */
	private static class CategoryItem
	{
		final String displayName;
		final int itemId;
		final String searchName;

		CategoryItem(String displayName, int itemId, String searchName)
		{
			this.displayName = displayName;
			this.itemId = itemId;
			this.searchName = searchName;
		}
	}
}