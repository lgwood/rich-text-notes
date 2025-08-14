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
	private String rtfContent;
	private NoteMetadata metadata;
	public boolean isModified;
	private boolean isLoaded;

	public Note(String noteId, String rtfContent, NoteMetadata metadata)
	{
		this.noteId = noteId;
		this.rtfContent = rtfContent;
		this.metadata = metadata;
		this.isModified = false;
		this.isLoaded = true;
	}

	public void setRtfContent(String rtfContent)
	{
		if (!rtfContent.equals(this.rtfContent))
		{
			this.rtfContent = rtfContent;
			this.isModified = true;
			if (metadata != null)
			{
				metadata.updateModified();
			}
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