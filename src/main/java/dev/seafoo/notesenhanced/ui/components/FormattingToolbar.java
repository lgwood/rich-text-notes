package dev.seafoo.notesenhanced.ui.components;

import dev.seafoo.notesenhanced.services.ItemIconService;
import dev.seafoo.notesenhanced.ui.popups.ItemSearchPopup;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * Formatting toolbar for the Notes Enhanced RTF editor
 */
@Slf4j
public class FormattingToolbar extends JToolBar implements CaretListener
{

	private final JTextPane textPane;
	private final NotesEnhancedEditorKit editorKit;
	private final ItemIconService itemIconService;

	// Font size controls
	private JComboBox<FontSizeOption> fontSizeCombo;

	// Formatting toggle buttons
	private JToggleButton boldButton;
	private JToggleButton italicButton;
	private JToggleButton strikethroughButton;

	// Item icon button
	private JButton itemIconButton;

	// Color picker
	private JComboBox<ColorOption> colorCombo;

	// Item search popup - NEW FIELD
	private ItemSearchPopup itemSearchPopup;

	// Prevent recursive updates during caret events
	private boolean updatingToolbar = false;

	public FormattingToolbar(JTextPane textPane, NotesEnhancedEditorKit editorKit, ItemIconService itemIconService)
	{
		super("Formatting", JToolBar.HORIZONTAL);
		this.textPane = textPane;
		this.editorKit = editorKit;
		this.itemIconService = itemIconService;

		setupToolbar();
		setupButtons();

		// Listen for caret changes to update button states
		textPane.addCaretListener(this);

		// Initial update
		updateToolbarState();
	}

	private void setupToolbar()
	{
		setFloatable(false);
		setRollover(true);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(0, 0, 3, 0));
		setPreferredSize(new Dimension(0, 35));
	}

	private void setupButtons()
	{


		// Bold button
		boldButton = createToggleButton("B", "Bold (" + getShortcutText("B", false) + ")", true);
		boldButton.addActionListener(e -> editorKit.getCustomAction("toggle-bold").actionPerformed(
			new ActionEvent(textPane, ActionEvent.ACTION_PERFORMED, "toggle-bold")));
		add(boldButton);

		// Italic button
		italicButton = createToggleButton("I", "Italic (" + getShortcutText("I ", false) + ")", false);
		italicButton.setFont(italicButton.getFont().deriveFont(Font.ITALIC));
		italicButton.addActionListener(e -> editorKit.getCustomAction("toggle-italic").actionPerformed(
			new ActionEvent(textPane, ActionEvent.ACTION_PERFORMED, "toggle-italic")));
		add(italicButton);

		// Strikethrough button
		strikethroughButton = createToggleButton("<html><s>S</s></html>", "Strikethrough (" + getShortcutText("S", true) + ")", false);
		strikethroughButton.addActionListener(e -> editorKit.getCustomAction("toggle-strikethrough").actionPerformed(
			new ActionEvent(textPane, ActionEvent.ACTION_PERFORMED, "toggle-strikethrough")));
		add(strikethroughButton);

		addSeparator();

		// Font size dropdown
		createFontSizeCombo();
		add(fontSizeCombo);

		// Color picker
		createColorCombo();
		add(colorCombo);

		addSeparator();

		// Item icon button
		String itemDisplayIcon = "¤";
		if (getFont().canDisplay('⚔'))
		{
			itemDisplayIcon = "⚔";
		}
		itemIconButton = createButton(itemDisplayIcon, "Insert Item Icon");
		itemIconButton.addActionListener(e -> insertItemIcon());
		itemIconButton.setEnabled(itemIconService != null);
		add(itemIconButton);

		// Add flexible space to push remaining buttons to the right
		add(Box.createHorizontalGlue());
	}

	private void createFontSizeCombo()
	{
		FontSizeOption[] options = {
			new FontSizeOption("Smaller", NotesEnhancedEditorKit.FONT_SIZE_SMALLER, "font-size-smaller"),
			new FontSizeOption("Small", NotesEnhancedEditorKit.FONT_SIZE_SMALL, "font-size-small"),
			new FontSizeOption("Normal", NotesEnhancedEditorKit.FONT_SIZE_NORMAL, "font-size-normal"),
			new FontSizeOption("Big", NotesEnhancedEditorKit.FONT_SIZE_BIG, "font-size-big"),
			new FontSizeOption("Bigger", NotesEnhancedEditorKit.FONT_SIZE_BIGGER, "font-size-bigger"),
			new FontSizeOption("Huge", NotesEnhancedEditorKit.FONT_SIZE_HUGE, "font-size-huge")
		};

		fontSizeCombo = new JComboBox<>(options);
		fontSizeCombo.setSelectedIndex(2); // Default to "Normal" (16pt)
		fontSizeCombo.setMaximumSize(new Dimension(55, 25));
		fontSizeCombo.setPreferredSize(new Dimension(55, 25));
		fontSizeCombo.setMinimumSize(new Dimension(55, 25));


		fontSizeCombo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
														  boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

				if (value instanceof FontSizeOption)
				{
					FontSizeOption option = (FontSizeOption) value;
					setText(option.getDisplayName());
					setFont(getFont().deriveFont((float) option.getFontSize() - index));

					if (index == -1)
					{
						setText("aA");
						setHorizontalAlignment(SwingConstants.CENTER);
					}
					else
					{
						setHorizontalAlignment(SwingConstants.LEFT);
					}

					if (isSelected)
					{
						setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
						setForeground(Color.WHITE);

					}
					else
					{
						setBackground(ColorScheme.DARKER_GRAY_COLOR);
						setForeground(Color.WHITE);
					}
				}

				return this;
			}
		});

		fontSizeCombo.addActionListener(e -> {
			if (updatingToolbar)
			{
				return;
			}

			FontSizeOption selected = (FontSizeOption) fontSizeCombo.getSelectedItem();
			if (selected != null)
			{
				Action action = editorKit.getCustomAction(selected.getActionName());
				if (action != null)
				{
					action.actionPerformed(new ActionEvent(textPane, ActionEvent.ACTION_PERFORMED,
						selected.getActionName()));
					textPane.requestFocus();
				}
			}
		});
	}

	private void createColorCombo()
	{
		ColorOption[] options = {
			new ColorOption("", Color.WHITE),
			new ColorOption("", Color.YELLOW),
			new ColorOption("", Color.RED),
			new ColorOption("", Color.GREEN),
			new ColorOption("", Color.CYAN),
			new ColorOption("", Color.MAGENTA)
		};

		colorCombo = new JComboBox<>(options);
		colorCombo.setSelectedIndex(0); // Default to white
		colorCombo.setMaximumSize(new Dimension(44, 25));
		colorCombo.setPreferredSize(new Dimension(44, 25));
		colorCombo.setMinimumSize(new Dimension(44, 25));


		colorCombo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
														  boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

				if (value instanceof ColorOption)
				{
					ColorOption option = (ColorOption) value;

					// Create a color swatch
					setIcon(createColorSwatch(option.getColor()));
					setText("");

					if (isSelected)
					{
						setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
						setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
					}
					else
					{
						setBackground(ColorScheme.DARKER_GRAY_COLOR);
						setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR, 1));
					}
				}

				return this;
			}
		});

		colorCombo.setPrototypeDisplayValue(new ColorOption("", Color.WHITE));

		colorCombo.addActionListener(e -> {
			if (updatingToolbar)
			{
				return;
			}

			ColorOption selected = (ColorOption) colorCombo.getSelectedItem();
			if (selected != null)
			{
				Action action = editorKit.getCustomAction("set-color");
				if (action != null)
				{
					// Set the color in the action
					action.putValue("color", selected.getColor());
					action.actionPerformed(new ActionEvent(textPane, ActionEvent.ACTION_PERFORMED, "set-color"));
					textPane.requestFocus();
				}
			}
		});
	}

	private Icon createColorSwatch(Color color)
	{
		int width = 16;
		int height = 16;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();

		// Fill with the color
		g2d.setColor(color);
		g2d.fillRect(0, 0, width, height);

		// Add border
		g2d.setColor(Color.DARK_GRAY);
		g2d.drawRect(0, 0, width - 1, height - 1);

		g2d.dispose();
		return new ImageIcon(image);
	}

	private JToggleButton createToggleButton(String text, String tooltip, boolean bold)
	{
		JToggleButton button = new JToggleButton(text);
		button.setToolTipText(tooltip);
		button.setPreferredSize(new Dimension(20, 25));
		button.setMaximumSize(new Dimension(20, 25));
		button.setMinimumSize(new Dimension(20, 25));
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(Color.WHITE);

		if (bold)
		{
			button.setFont(button.getFont().deriveFont(Font.BOLD));
		}

		// Custom styling for toggle state
		button.addItemListener(e -> {
			if (button.isSelected())
			{
				button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}
			else
			{
				button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		return button;
	}

	private JButton createButton(String text, String tooltip)
	{
		JButton button = new JButton(text);
		button.setToolTipText(tooltip);
		button.setPreferredSize(new Dimension(25, 25));
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(Color.WHITE);

		// Hover effect
		button.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		return button;
	}

	@Override
	public void caretUpdate(CaretEvent e)
	{
		SwingUtilities.invokeLater(this::updateToolbarState);
	}

	private void updateToolbarState()
	{
		if (textPane == null)
		{
			return;
		}

		updatingToolbar = true;

		try
		{
			// Update toggle button states based on current character attributes
			var attrs = textPane.getCharacterAttributes();

			boldButton.setSelected(StyleConstants.isBold(attrs));
			italicButton.setSelected(StyleConstants.isItalic(attrs));
			strikethroughButton.setSelected(StyleConstants.isStrikeThrough(attrs));

			// Update font size combo
			int currentSize = NotesEnhancedEditorKit.getCurrentFontSize(textPane);
			updateFontSizeCombo(currentSize);

			// Update color combo
			Color currentColor = NotesEnhancedEditorKit.getCurrentTextColor(textPane);
			updateColorCombo(currentColor);

			// Update item icon button state
			if (itemIconService != null)
			{
				itemIconButton.setEnabled(true);
			}

		}
		catch (Exception ex)
		{
			log.warn("Error updating toolbar state", ex);
		}
		finally
		{
			updatingToolbar = false;
		}
	}

	private void updateFontSizeCombo(int currentSize)
	{
		// Find the closest matching font size option
		FontSizeOption bestMatch = null;
		int bestDistance = Integer.MAX_VALUE;

		for (int i = 0; i < fontSizeCombo.getItemCount(); i++)
		{
			FontSizeOption option = fontSizeCombo.getItemAt(i);
			int distance = Math.abs(option.getFontSize() - currentSize);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				bestMatch = option;
			}
		}

		if (bestMatch != null)
		{
			fontSizeCombo.setSelectedItem(bestMatch);
		}
	}

	private void updateColorCombo(Color currentColor)
	{
		if (currentColor == null)
		{
			currentColor = Color.WHITE; // Default to white
		}

		// Find exact matching color option
		for (int i = 0; i < colorCombo.getItemCount(); i++)
		{
			ColorOption option = colorCombo.getItemAt(i);
			if (option.getColor().equals(currentColor))
			{
				colorCombo.setSelectedItem(option);
				return;
			}
		}

		// If no exact match found, default to white
		colorCombo.setSelectedIndex(0);
	}

	private void insertItemIcon()
	{
		if (itemIconService == null)
		{
			JOptionPane.showMessageDialog(this,
				"Item icon service is not ready. Please wait a moment and try again.",
				"Service Not Ready",
				JOptionPane.WARNING_MESSAGE);
			return;
		}

		// Show the item search popup
		showItemSearchDialog();
	}


	private void showItemSearchDialog()
	{
		// Create popup if not exists
		if (itemSearchPopup == null)
		{
			itemSearchPopup = new ItemSearchPopup(itemIconService);
		}

		// Show popup at button location
		itemSearchPopup.showPopup(itemIconButton, 0, itemIconButton.getHeight(), result -> {
			if (result != null)
			{
				insertSelectedItem(result.getItemName(), result.getItemId(), result.getQuantity());
			}
		});
	}

	private void insertSelectedItem(String itemName, int itemId, int quantity)
	{
		try
		{
			// Use the quantity from the popup, ensuring it's at least 1
			int validQuantity = Math.max(1, quantity);

			// Use ItemIconService to get a properly centered icon with quantity
			Icon centeredIcon = itemIconService.getCenteredIconById(itemId, validQuantity);

			if (centeredIcon != null)
			{
				// Attach icon to text pane for repaint on load
				if (centeredIcon instanceof ItemIconService.CenteredImageIcon)
				{
					((ItemIconService.CenteredImageIcon) centeredIcon).attachToComponent(textPane);
				}

				// Create attributes with icon and metadata
				SimpleAttributeSet attrs = new SimpleAttributeSet();
				StyleConstants.setIcon(attrs, centeredIcon);

				// Store metadata for saving/loading including quantity
				attrs.addAttribute("item-icon-data",
					new NotesEnhancedDocument.IconData(itemName, itemId, validQuantity));

				// Insert the icon
				textPane.getDocument().insertString(textPane.getCaretPosition(), " ", attrs);

				log.debug("Inserted centered icon for {} (ID: {}) with quantity: {}", itemName, itemId, validQuantity);
			}
			else
			{
				JOptionPane.showMessageDialog(this,
					"Could not load icon for: " + itemName,
					"Icon Not Found",
					JOptionPane.WARNING_MESSAGE);
			}

		}
		catch (Exception ex)
		{
			log.error("Failed to insert item icon", ex);
			JOptionPane.showMessageDialog(this,
				"Error inserting icon: " + ex.getMessage(),
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	private String getShortcutText(String key, boolean shift)
	{
		int shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
		String modifier = shortcutKeyMask == KeyEvent.CTRL_DOWN_MASK ? "Ctrl" : "Cmd";
		String shiftText = shift ? "Shift+" : "";
		return modifier + "+" + shiftText + key;
	}

	/**
	 * Cleanup method to remove listeners - UPDATED METHOD
	 */
	public void cleanup()
	{
		if (textPane != null)
		{
			textPane.removeCaretListener(this);
		}
		if (itemSearchPopup != null)
		{
			itemSearchPopup.setVisible(false);
			itemSearchPopup = null;
		}
	}

	/**
	 * Color option for the combo box
	 */
	@Getter
	private static class ColorOption
	{
		private final String displayName;
		private final Color color;

		public ColorOption(String displayName, Color color)
		{
			this.displayName = displayName;
			this.color = color;
		}

		@Override
		public String toString()
		{
			return displayName;
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
			ColorOption that = (ColorOption) obj;
			return color.equals(that.color) && displayName.equals(that.displayName);
		}

		@Override
		public int hashCode()
		{
			return displayName.hashCode() * 31 + color.hashCode();
		}
	}

	/**
	 * Font size option for the combo box
	 */
	@Getter
	private static class FontSizeOption
	{
		private final String displayName;
		private final int fontSize;
		private final String actionName;

		public FontSizeOption(String displayName, int fontSize, String actionName)
		{
			this.displayName = displayName;
			this.fontSize = fontSize;
			this.actionName = actionName;
		}

		@Override
		public String toString()
		{
			return displayName;
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
			FontSizeOption that = (FontSizeOption) obj;
			return fontSize == that.fontSize && displayName.equals(that.displayName);
		}

		@Override
		public int hashCode()
		{
			return displayName.hashCode() * 31 + fontSize;
		}
	}
}