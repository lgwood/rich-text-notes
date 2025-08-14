package dev.seafoo.richtextnotes.ui.popups;

import dev.seafoo.richtextnotes.models.NoteMetadata;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * Enhanced dialog for selecting notes with search, tag filtering, and sorting
 */
@Slf4j
public class NoteSelectionDialog extends JDialog
{

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

	// Data
	private final List<NoteMetadata> allNotes;
	private List<NoteMetadata> filteredNotes;
	private Set<String> allTags;
	@Getter
	private NoteMetadata selectedNote = null;
	// Public accessors
	@Getter
	private boolean okPressed = false;

	// UI Components
	private JTextField searchField;
	private JPanel tagFilterPanel;
	private JScrollPane tagFilterScrollPane;
	private Map<String, JCheckBox> tagCheckBoxes;
	private JTable notesTable;
	private NoteTableModel tableModel;
	private TableRowSorter<NoteTableModel> tableSorter;
	private JButton okButton;
	private JButton cancelButton;
	private JLabel resultCountLabel;

	// Search and filter state
	private String currentSearchText = "";
	private Set<String> selectedTags = new HashSet<>();

	public NoteSelectionDialog(JComponent parent, List<NoteMetadata> notes)
	{
		super(SwingUtilities.getWindowAncestor(parent), "Open Existing Note", ModalityType.APPLICATION_MODAL);
		this.allNotes = new ArrayList<>(notes);
		this.filteredNotes = new ArrayList<>(notes);

		extractAllTags();
		setupDialog();
		updateFilteredNotes();
		setLocationRelativeTo(parent);
	}

	private void extractAllTags()
	{
		allTags = new HashSet<>();
		for (NoteMetadata note : allNotes)
		{
			if (note.getTags() != null)
			{
				allTags.addAll(note.getTags());
			}
		}
	}

	private void setupDialog()
	{
		setLayout(new BorderLayout());
		setSize(800, 500);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setResizable(true);

		// Main panel
		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Top panel with search only
		JPanel topPanel = createTopPanel();
		mainPanel.add(topPanel, BorderLayout.NORTH);

		// Center panel with horizontal split: tags left, notes right
		JSplitPane splitPane = createMainSplitPane();
		mainPanel.add(splitPane, BorderLayout.CENTER);

		// Bottom panel with buttons
		JPanel bottomPanel = createBottomPanel();
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);

		add(mainPanel);

		// Make dialog look consistent with RuneLite
		getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Set initial focus
		SwingUtilities.invokeLater(() -> searchField.requestFocus());
	}

	private JPanel createTopPanel()
	{
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

		// Search panel only
		JPanel searchPanel = createSearchPanel();
		topPanel.add(searchPanel, BorderLayout.CENTER);

		return topPanel;
	}

	private JSplitPane createMainSplitPane()
	{
		// Left panel: Tag filters
		JPanel leftPanel = createTagFilterPanel();
		leftPanel.setPreferredSize(new Dimension(220, 0));
		leftPanel.setMinimumSize(new Dimension(180, 0));

		// Right panel: Notes table
		JPanel rightPanel = createNotesPanel();

		// Create split pane
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
		splitPane.setDividerLocation(220);
		splitPane.setResizeWeight(0.0); // Give all extra space to the right panel
		splitPane.setContinuousLayout(true);
		splitPane.setOneTouchExpandable(false);
		splitPane.setBorder(BorderFactory.createEmptyBorder());
		splitPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		splitPane.setDividerSize(6);

		return splitPane;
	}

	private JPanel createSearchPanel()
	{
		JPanel searchPanel = new JPanel(new BorderLayout());
		searchPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel searchLabel = new JLabel("Search:");
		searchLabel.setForeground(Color.WHITE);
		searchLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
		searchPanel.add(searchLabel, BorderLayout.WEST);

		searchField = new JTextField();
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setForeground(Color.WHITE);
		searchField.setCaretColor(Color.WHITE);
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR),
			new EmptyBorder(5, 8, 5, 8)
		));

		// Real-time search
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}
		});

		searchPanel.add(searchField, BorderLayout.CENTER);

		// Clear search button
		JButton clearButton = new JButton("Clear");
		clearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clearButton.setForeground(Color.WHITE);
		clearButton.setFocusPainted(false);
		clearButton.addActionListener(e -> {
			searchField.setText("");
			searchField.requestFocus();
		});
		searchPanel.add(clearButton, BorderLayout.EAST);

		return searchPanel;
	}

	private JPanel createTagFilterPanel()
	{
		JPanel container = new JPanel(new BorderLayout());
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);
		container.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR),
			"Filter by Tags",
			TitledBorder.DEFAULT_JUSTIFICATION,
			TitledBorder.DEFAULT_POSITION,
			null,
			Color.WHITE
		));

		// Tag count label
		JLabel tagCountLabel = new JLabel(allTags.size() + " available tags");
		tagCountLabel.setForeground(Color.LIGHT_GRAY);
		tagCountLabel.setFont(tagCountLabel.getFont().deriveFont(10f));
		tagCountLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
		container.add(tagCountLabel, BorderLayout.NORTH);

		// Tag checkboxes panel
		JPanel tagCheckboxContainer = new JPanel();
		tagCheckboxContainer.setLayout(new BoxLayout(tagCheckboxContainer, BoxLayout.Y_AXIS));
		tagCheckboxContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		tagFilterPanel = new JPanel(new BorderLayout());
		tagFilterPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tagFilterPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

		tagCheckBoxes = new HashMap<>();

		// Create "All Tags" checkbox
		JCheckBox allTagsCheckBox = new JCheckBox("(All Tags)");
		allTagsCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		allTagsCheckBox.setForeground(Color.WHITE);
		allTagsCheckBox.setSelected(true);
		allTagsCheckBox.addActionListener(e -> {
			if (allTagsCheckBox.isSelected())
			{
				// Uncheck all individual tags
				selectedTags.clear();
				for (JCheckBox cb : tagCheckBoxes.values())
				{
					cb.setSelected(false);
				}
			}
			updateFilteredNotes();
		});
		tagCheckboxContainer.add(allTagsCheckBox);

		if (!allTags.isEmpty())
		{
			// Add separator
			JSeparator separator = new JSeparator();
			separator.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			separator.setForeground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			tagCheckboxContainer.add(Box.createVerticalStrut(3));
			tagCheckboxContainer.add(separator);
			tagCheckboxContainer.add(Box.createVerticalStrut(3));

			// Sort tags alphabetically
			List<String> sortedTags = new ArrayList<>(allTags);
			sortedTags.sort(String.CASE_INSENSITIVE_ORDER);

			// Create individual tag checkboxes
			for (String tag : sortedTags)
			{
				JCheckBox tagCheckBox = new JCheckBox(tag);
				tagCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				tagCheckBox.setForeground(Color.WHITE);
				tagCheckBox.addActionListener(e -> {
					// Update selected tags
					if (tagCheckBox.isSelected())
					{
						selectedTags.add(tag);
						allTagsCheckBox.setSelected(false);
					}
					else
					{
						selectedTags.remove(tag);
						if (selectedTags.isEmpty())
						{
							allTagsCheckBox.setSelected(true);
						}
					}
					updateFilteredNotes();
				});

				tagCheckBoxes.put(tag, tagCheckBox);
				tagCheckboxContainer.add(tagCheckBox);
			}
		}

		// Add the checkbox container to the NORTH of the BorderLayout
		tagFilterPanel.add(tagCheckboxContainer, BorderLayout.NORTH);

		// Scroll pane for tags
		tagFilterScrollPane = new JScrollPane(tagFilterPanel);
		tagFilterScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		tagFilterScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		tagFilterScrollPane.setBorder(BorderFactory.createEmptyBorder());

		container.add(tagFilterScrollPane, BorderLayout.CENTER);

		return container;
	}

	private JPanel createNotesPanel()
	{
		JPanel notesPanel = new JPanel(new BorderLayout());
		notesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		notesPanel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR),
			"Notes",
			TitledBorder.DEFAULT_JUSTIFICATION,
			TitledBorder.DEFAULT_POSITION,
			null,
			Color.WHITE
		));

		// Result count label
		resultCountLabel = new JLabel();
		resultCountLabel.setForeground(Color.WHITE);
		resultCountLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
		notesPanel.add(resultCountLabel, BorderLayout.NORTH);

		// Notes table
		tableModel = new NoteTableModel();
		notesTable = new JTable(tableModel);

		// Enable column sorting
		tableSorter = new TableRowSorter<>(tableModel);
		notesTable.setRowSorter(tableSorter);

		// Configure table appearance
		notesTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		notesTable.setForeground(Color.WHITE);
		notesTable.setSelectionBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		notesTable.setSelectionForeground(Color.WHITE);
		notesTable.setGridColor(ColorScheme.DARK_GRAY_COLOR);
		notesTable.setRowHeight(24);
		notesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Configure column widths
		notesTable.getColumnModel().getColumn(0).setPreferredWidth(200); // Title
		notesTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Created
		notesTable.getColumnModel().getColumn(2).setPreferredWidth(120); // Modified
		notesTable.getColumnModel().getColumn(3).setPreferredWidth(150); // Tags

		// Custom cell renderer for better appearance
		DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer()
		{
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
														   boolean hasFocus, int row, int column)
			{
				super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

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

				setBorder(new EmptyBorder(2, 5, 2, 5));

				return this;
			}
		};

		for (int i = 0; i < notesTable.getColumnCount(); i++)
		{
			notesTable.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
		}

		// Configure table header
		notesTable.getTableHeader().setBackground(ColorScheme.DARK_GRAY_COLOR);
		notesTable.getTableHeader().setForeground(Color.WHITE);
		notesTable.getTableHeader().setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR));

		// Double-click to select
		notesTable.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					selectNote();
				}
			}
		});

		// Selection change listener
		notesTable.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
			{
				int selectedRow = notesTable.getSelectedRow();
				okButton.setEnabled(selectedRow >= 0);
			}
		});

		JScrollPane tableScrollPane = new JScrollPane(notesTable);
		tableScrollPane.setBorder(BorderFactory.createEmptyBorder());

		notesPanel.add(tableScrollPane, BorderLayout.CENTER);

		return notesPanel;
	}

	private JPanel createBottomPanel()
	{
		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bottomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

		okButton = new JButton("Open Note");
		okButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		okButton.setForeground(Color.WHITE);
		okButton.setFocusPainted(false);
		okButton.setEnabled(false);
		okButton.addActionListener(e -> selectNote());
		bottomPanel.add(okButton);

		cancelButton = new JButton("Cancel");
		cancelButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cancelButton.setForeground(Color.WHITE);
		cancelButton.setFocusPainted(false);
		cancelButton.addActionListener(e -> dispose());
		bottomPanel.add(cancelButton);

		return bottomPanel;
	}

	private void onSearchChanged()
	{
		currentSearchText = searchField.getText().trim().toLowerCase();
		updateFilteredNotes();
	}

	private void updateFilteredNotes()
	{
		// Apply filters
		filteredNotes = allNotes.stream()
			.filter(this::matchesSearchFilter)
			.filter(this::matchesTagFilter)
			.collect(Collectors.toList());

		// Update table
		tableModel.fireTableDataChanged();

		// Update result count
		updateResultCount();

		// Clear selection if needed
		if (notesTable.getSelectedRow() >= filteredNotes.size())
		{
			notesTable.clearSelection();
		}
	}

	private boolean matchesSearchFilter(NoteMetadata note)
	{
		if (currentSearchText.isEmpty())
		{
			return true;
		}

		String title = note.getTitle() != null ? note.getTitle().toLowerCase() : "";
		return title.contains(currentSearchText);
	}

	private boolean matchesTagFilter(NoteMetadata note)
	{
		if (selectedTags.isEmpty())
		{
			return true; // "All Tags" mode
		}

		if (note.getTags() == null || note.getTags().isEmpty())
		{
			return false; // Note has no tags but we're filtering by tags
		}

		// Check if note has at least one of the selected tags
		return note.getTags().stream().anyMatch(selectedTags::contains);
	}

	private void updateResultCount()
	{
		String countText = String.format("Showing %d of %d notes", filteredNotes.size(), allNotes.size());
		if (!currentSearchText.isEmpty() || !selectedTags.isEmpty())
		{
			countText += " (filtered)";
		}
		resultCountLabel.setText(countText);
	}

	private void selectNote()
	{
		int selectedRow = notesTable.getSelectedRow();
		if (selectedRow >= 0)
		{
			// Convert view row to model row (accounts for sorting)
			int modelRow = notesTable.convertRowIndexToModel(selectedRow);
			if (modelRow >= 0 && modelRow < filteredNotes.size())
			{
				selectedNote = filteredNotes.get(modelRow);
				okPressed = true;
				dispose();
			}
		}
	}

	// Table model
	private class NoteTableModel extends AbstractTableModel
	{
		private final String[] columnNames = {"Title", "Created", "Modified", "Tags"};

		@Override
		public int getRowCount()
		{
			return filteredNotes.size();
		}

		@Override
		public int getColumnCount()
		{
			return columnNames.length;
		}

		@Override
		public String getColumnName(int column)
		{
			return columnNames[column];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			if (rowIndex >= filteredNotes.size())
			{
				return "";
			}

			NoteMetadata note = filteredNotes.get(rowIndex);

			switch (columnIndex)
			{
				case 0: // Title
					return note.getTitle() != null ? note.getTitle() : "Untitled Note";
				case 1: // Created
					return note.getCreatedDate() != null ?
						note.getCreatedDate().format(DATE_FORMATTER) : "";
				case 2: // Modified
					return note.getLastModified() != null ?
						note.getLastModified().format(DATE_FORMATTER) : "";
				case 3: // Tags
					if (note.getTags() == null || note.getTags().isEmpty())
					{
						return "";
					}
					return String.join(", ", note.getTags());
				default:
					return "";
			}
		}

		@Override
		public Class<?> getColumnClass(int columnIndex)
		{
			return String.class;
		}

	}
}