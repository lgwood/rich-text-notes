package dev.seafoo.notesenhanced.models;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class EditorLayout
{
	// Multi-pane layout support
	@SerializedName("pane_groups")
	private List<PaneGroupLayout> paneGroups;

	@SerializedName("active_pane_group_id")
	private String activePaneGroupId;

	@SerializedName("active_note_id")
	private String activeNoteId;

	// Legacy fields (kept for potential future use)
	@SerializedName("window_width")
	private int windowWidth;

	@SerializedName("window_height")
	private int windowHeight;

	@SerializedName("search_history")
	private List<String> searchHistory;

	public EditorLayout()
	{
		this.paneGroups = new ArrayList<>();
		this.searchHistory = new ArrayList<>();
		this.windowWidth = 400;
		this.windowHeight = 600;
	}

	/**
	 * Add a pane group to the layout
	 */
	public void addPaneGroup(PaneGroupLayout paneGroup)
	{
		if (paneGroups == null)
		{
			paneGroups = new ArrayList<>();
		}
		paneGroups.add(paneGroup);
	}

	/**
	 * Get pane group by ID
	 */
	public PaneGroupLayout getPaneGroupById(String paneGroupId)
	{
		if (paneGroups == null)
		{
			return null;
		}

		return paneGroups.stream()
			.filter(pg -> paneGroupId.equals(pg.getPaneGroupId()))
			.findFirst()
			.orElse(null);
	}

	/**
	 * Remove pane group by ID
	 */
	public boolean removePaneGroup(String paneGroupId)
	{
		if (paneGroups == null)
		{
			return false;
		}

		return paneGroups.removeIf(pg -> paneGroupId.equals(pg.getPaneGroupId()));
	}

	/**
	 * Get total number of pane groups
	 */
	public int getPaneGroupCount()
	{
		return paneGroups != null ? paneGroups.size() : 0;
	}

	/**
	 * Get total number of notes across all panes
	 */
	public int getTotalNoteCount()
	{
		if (paneGroups == null)
		{
			return 0;
		}

		return paneGroups.stream()
			.mapToInt(pg -> pg.getNoteIds() != null ? pg.getNoteIds().size() : 0)
			.sum();
	}

	/**
	 * Check if layout has any content
	 */
	public boolean hasContent()
	{
		return getPaneGroupCount() > 0 && getTotalNoteCount() > 0;
	}

	/**
	 * Clear all layout data
	 */
	public void clear()
	{
		if (paneGroups != null)
		{
			paneGroups.clear();
		}
		activePaneGroupId = null;
		activeNoteId = null;
	}

	/**
	 * Layout information for a single pane group
	 */
	@Data
	@NoArgsConstructor
	public static class PaneGroupLayout
	{
		@SerializedName("pane_group_id")
		private String paneGroupId;

		@SerializedName("note_ids")
		private List<String> noteIds;

		@SerializedName("active_note_id")
		private String activeNoteId;

		@SerializedName("order_index")
		private int orderIndex; // For maintaining pane order

		public PaneGroupLayout(String paneGroupId, int orderIndex)
		{
			this.paneGroupId = paneGroupId;
			this.orderIndex = orderIndex;
			this.noteIds = new ArrayList<>();
		}

		/**
		 * Add a note ID to this pane group
		 */
		public void addNoteId(String noteId)
		{
			if (noteIds == null)
			{
				noteIds = new ArrayList<>();
			}
			if (!noteIds.contains(noteId))
			{
				noteIds.add(noteId);
			}
		}

		/**
		 * Remove a note ID from this pane group
		 */
		public void removeNoteId(String noteId)
		{
			if (noteIds != null)
			{
				noteIds.remove(noteId);
			}
		}

		/**
		 * Get the number of notes in this pane group
		 */
		public int getNoteCount()
		{
			return noteIds != null ? noteIds.size() : 0;
		}

		/**
		 * Check if this pane group has any notes
		 */
		public boolean hasNotes()
		{
			return getNoteCount() > 0;
		}
	}
}