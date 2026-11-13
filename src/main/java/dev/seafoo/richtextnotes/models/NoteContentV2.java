package dev.seafoo.richtextnotes.models;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * v2 note format: text plus style spans and item icons, saved as .note files and to the cloud.
 */
@Data
public class NoteContentV2
{
	public static final int VERSION = 2;

	private int version = VERSION;
	private String text = "";
	private List<Span> spans = new ArrayList<>();
	private List<ItemIcon> icons = new ArrayList<>();

	/**
	 * A run of formatted text. Unset (null) fields are left out of the JSON.
	 */
	@Data
	public static class Span
	{
		private int start;
		private int length;
		private Boolean bold;
		private Boolean italic;
		private Boolean strike;
		private Integer size;
		private String color; // hex, e.g. "ff0000"
	}

	/**
	 * An item icon occupying a single character at {@code offset}.
	 */
	@Data
	public static class ItemIcon
	{
		private int offset;
		private int itemId;
		private int quantity;
		private String name;
	}
}
