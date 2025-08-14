package dev.seafoo.richtextnotes.ui.popups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;

public class TagEditDialog extends JDialog
{
	private final List<String> tags;
	@Getter
	private boolean okPressed = false;

	private JTextField newTagField;
	private DefaultListModel<String> tagListModel;
	private JList<String> tagList;
	private String noteTitle;

	public TagEditDialog(JComponent parent, List<String> currentTags, String noteTitle)
	{
		super(SwingUtilities.getWindowAncestor(parent), "Edit Tags", ModalityType.APPLICATION_MODAL);
		this.tags = new ArrayList<>(currentTags);
		this.noteTitle = noteTitle;

		setupDialog();
		setLocationRelativeTo(parent);
	}

	private void setupDialog()
	{
		setLayout(new BorderLayout());
		setSize(300, 400);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		// Create main panel
		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Title label
		JLabel titleLabel = new JLabel(noteTitle);
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
		mainPanel.add(titleLabel, BorderLayout.NORTH);

		// Center panel for tag list and controls
		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		centerPanel.setBorder(new EmptyBorder(10, 0, 10, 0));

		// Tag list
		tagListModel = new DefaultListModel<>();
		for (String tag : tags)
		{
			tagListModel.addElement(tag);
		}

		tagList = new JList<>(tagListModel);
		tagList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tagList.setForeground(Color.WHITE);
		tagList.setSelectionBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		tagList.setBorder(new EmptyBorder(5, 5, 5, 5));

		JScrollPane scrollPane = new JScrollPane(tagList);
		scrollPane.setPreferredSize(new Dimension(250, 200));
		scrollPane.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR),
			"Current Tags",
			0, 0, null, Color.WHITE
		));
		centerPanel.add(scrollPane, BorderLayout.CENTER);

		// Add tag panel
		JPanel addTagPanel = new JPanel(new BorderLayout());
		addTagPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		addTagPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

		newTagField = new JTextField();
		newTagField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		newTagField.setForeground(Color.WHITE);
		newTagField.setCaretColor(Color.WHITE);
		newTagField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR),
			new EmptyBorder(5, 5, 5, 5)
		));

		// Add tag on Enter key
		newTagField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					addTag();
				}
			}
		});

		JButton addButton = new JButton("Add Tag");
		addButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		addButton.setForeground(Color.WHITE);
		addButton.setFocusPainted(false);
		addButton.addActionListener(e -> addTag());

		addTagPanel.add(newTagField, BorderLayout.CENTER);
		addTagPanel.add(addButton, BorderLayout.EAST);
		centerPanel.add(addTagPanel, BorderLayout.SOUTH);

		mainPanel.add(centerPanel, BorderLayout.CENTER);

		// Button panel
		JPanel buttonPanel = new JPanel(new FlowLayout());
		buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton removeButton = new JButton("Remove Selected");
		removeButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		removeButton.setForeground(Color.WHITE);
		removeButton.setFocusPainted(false);
		removeButton.addActionListener(e -> removeSelectedTag());
		buttonPanel.add(removeButton);

		JButton okButton = new JButton("OK");
		okButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		okButton.setForeground(Color.WHITE);
		okButton.setFocusPainted(false);
		okButton.addActionListener(e -> {
			okPressed = true;
			dispose();
		});
		buttonPanel.add(okButton);

		JButton cancelButton = new JButton("Cancel");
		cancelButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cancelButton.setForeground(Color.WHITE);
		cancelButton.setFocusPainted(false);
		cancelButton.addActionListener(e -> dispose());
		buttonPanel.add(cancelButton);

		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		add(mainPanel);

		// Make the dialog look consistent with RuneLite
		getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	private void addTag()
	{
		String newTag = newTagField.getText().trim();
		if (!newTag.isEmpty() && !tags.contains(newTag))
		{
			tags.add(newTag);
			tagListModel.addElement(newTag);
			newTagField.setText("");
			newTagField.requestFocus();
		}
	}

	private void removeSelectedTag()
	{
		int selectedIndex = tagList.getSelectedIndex();
		if (selectedIndex >= 0)
		{
			String selectedTag = tagListModel.getElementAt(selectedIndex);
			tags.remove(selectedTag);
			tagListModel.removeElementAt(selectedIndex);
		}
	}

	public List<String> getTags()
	{
		return new ArrayList<>(tags);
	}
}
