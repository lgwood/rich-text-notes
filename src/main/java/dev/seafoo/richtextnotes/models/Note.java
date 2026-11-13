package dev.seafoo.richtextnotes.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
public class Note
{
	private String noteId;
	// Null for notes that haven't been saved as v2 yet; those load from rtfV1
	private NoteContentV2 contentV2;
	private String rtfV1;
	private NoteMetadata metadata;
	public boolean isModified;
	private boolean isLoaded;

	public Note(String noteId, NoteContentV2 contentV2, String rtfV1, NoteMetadata metadata)
	{
		this.noteId = noteId;
		this.contentV2 = contentV2;
		this.rtfV1 = rtfV1;
		this.metadata = metadata;
		this.isModified = false;
		this.isLoaded = true;
	}

	public void setContentV2(NoteContentV2 contentV2)
	{
		this.contentV2 = contentV2;
		if (metadata != null)
		{
			metadata.updateModified();
		}
	}

	public void markSaved()
	{
		this.isModified = false;
	}

	public String getDisplayTitle()
	{
		if (metadata != null && metadata.getTitle() != null && !metadata.getTitle().trim().isEmpty())
		{
			return metadata.getTitle();
		}
		return "Untitled Note";
	}
}
