package dev.seafoo.richtextnotes.models;

import com.google.gson.annotations.SerializedName;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteMetadata
{
	@SerializedName("id")
	private String noteId;


	@SerializedName("title")
	private String title;

	@SerializedName("created")
	private LocalDateTime createdDate;

	@SerializedName("modified")
	private LocalDateTime lastModified;

	@SerializedName("tags")
	private List<String> tags;

	@SerializedName("category")
	private String category;

	@SerializedName("pinned")
	private boolean pinned;

	@SerializedName("color")
	private String color; // For color-coding notes

	// Constructor for basic metadata
	public NoteMetadata(String noteId, String title, LocalDateTime createdDate, List<String> tags)
	{
		this.noteId = noteId;
		this.title = title;
		this.createdDate = createdDate;
		this.lastModified = createdDate;
		this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
		this.category = null;
		this.pinned = false;
		this.color = null;
	}

	public void addTag(String tag)
	{
		if (tags == null)
		{
			tags = new ArrayList<>();
		}
		if (!tags.contains(tag))
		{
			tags.add(tag);
		}
	}

	public void removeTag(String tag)
	{
		if (tags != null)
		{
			tags.remove(tag);
		}
	}

	public boolean hasTag(String tag)
	{
		return tags != null && tags.contains(tag);
	}

	public void updateModified()
	{
		this.lastModified = LocalDateTime.now();
	}
}