package dev.seafoo.richtextnotes.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import com.google.gson.Gson;
import dev.seafoo.richtextnotes.models.NoteContentV2;
import dev.seafoo.richtextnotes.ui.components.NotesEnhancedDocument;
import dev.seafoo.richtextnotes.ui.components.NotesEnhancedEditorKit;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.junit.Test;

public class NoteSerializerV2Test
{
	@Test
	public void formattingSurvivesRoundTrip() throws Exception
	{
		NotesEnhancedDocument doc = new NotesEnhancedDocument();
		doc.insertString(0, "plain bold italic struck big red\nsecond line", null);
		setStyle(doc, 6, 4, a -> StyleConstants.setBold(a, true));
		setStyle(doc, 11, 6, a -> StyleConstants.setItalic(a, true));
		setStyle(doc, 18, 6, a -> StyleConstants.setStrikeThrough(a, true));
		setStyle(doc, 25, 3, a -> StyleConstants.setFontSize(a, 24));
		setStyle(doc, 29, 3, a -> StyleConstants.setForeground(a, Color.RED));
		setStyle(doc, 6, 18, a -> StyleConstants.setForeground(a, Color.YELLOW));

		assertSameContent(doc, roundTrip(doc));
	}

	@Test
	public void iconsSurviveRoundTrip() throws Exception
	{
		NotesEnhancedDocument doc = new NotesEnhancedDocument();
		doc.insertString(0, "kill  x and  ", null);
		insertIcon(doc, 4, new NotesEnhancedDocument.IconData("Abyssal whip", 4151, 1));
		// Two adjacent icons sharing the same data end up in one element
		NotesEnhancedDocument.IconData shark = new NotesEnhancedDocument.IconData("Shark", 385, 10);
		insertIcon(doc, 11, shark);
		insertIcon(doc, 12, shark);

		NoteContentV2 content = NoteSerializerV2.fromDocument(doc);
		assertEquals(3, content.getIcons().size());
		assertSameContent(doc, roundTrip(doc));
	}

	@Test
	public void typingAfterAnIconIsText() throws Exception
	{
		// Real editor: typed text takes its attributes from the character before the caret
		JTextPane pane = new NotesEnhancedEditorKit(null).createTextPane();
		pane.getDocument().insertString(0, "Get ", null);
		insertIcon(pane.getStyledDocument(), 4, new NotesEnhancedDocument.IconData("Coins", 995, 1));
		pane.setCaretPosition(5);
		pane.replaceSelection("now");

		NoteContentV2 content = NoteSerializerV2.fromDocument(pane.getStyledDocument());
		assertEquals("Get  now", content.getText());
		assertEquals(1, content.getIcons().size());
		assertEquals(4, content.getIcons().get(0).getOffset());
	}

	@Test
	public void emptyNoteSurvivesRoundTrip() throws Exception
	{
		NotesEnhancedDocument doc = new NotesEnhancedDocument();
		assertSameContent(doc, roundTrip(doc));
	}

	/**
	 * Converts real v1 notes to v2: set RTN_NOTES_DIR to a folder of .rtf files (e.g. a copy of
	 * ~/.runelite/rich-text-notes/profile_.../notes). Skipped when not set.
	 */
	@Test
	public void realV1NotesSurviveConversionToV2() throws Exception
	{
		String dir = System.getenv("RTN_NOTES_DIR");
		assumeTrue(dir != null);

		File[] files = new File(dir).listFiles((d, name) -> name.endsWith(".rtf"));
		assumeTrue(files != null && files.length > 0);

		NotesEnhancedEditorKit kit = new NotesEnhancedEditorKit(new FakeIconService());
		int largest = 0;
		for (File file : files)
		{
			NotesEnhancedDocument doc = new NotesEnhancedDocument();
			try (InputStream in = new FileInputStream(file))
			{
				kit.read(in, doc, 0);
			}

			try
			{
				assertSameContent(doc, roundTrip(doc));
			}
			catch (AssertionError e)
			{
				throw new AssertionError(file.getName() + ": " + e.getMessage(), e);
			}

			String json = new Gson().toJson(NoteSerializerV2.fromDocument(doc));
			int compressed = gzipBase64Length(json);
			largest = Math.max(largest, compressed);
			System.out.printf("%s: rtf %d, json %d, cloud %d chars%n", file.getName(), file.length(), json.length(), compressed);
		}
		System.out.printf("Converted %d notes, largest cloud size %d chars%n", files.length, largest);
	}

	private static StyledDocument roundTrip(StyledDocument doc) throws Exception
	{
		// Go through JSON too, since that's what's actually stored
		Gson gson = new Gson();
		NoteContentV2 content = gson.fromJson(gson.toJson(NoteSerializerV2.fromDocument(doc)), NoteContentV2.class);
		NotesEnhancedDocument result = new NotesEnhancedDocument();
		NoteSerializerV2.toDocument(content, result, null);
		return result;
	}

	private static void assertSameContent(StyledDocument expected, StyledDocument actual) throws Exception
	{
		assertEquals("text", expected.getText(0, expected.getLength()), actual.getText(0, actual.getLength()));

		for (int i = 0; i < expected.getLength(); i++)
		{
			AttributeSet e = expected.getCharacterElement(i).getAttributes();
			AttributeSet a = actual.getCharacterElement(i).getAttributes();
			String at = "offset " + i;

			NotesEnhancedDocument.IconData expectedIcon = (NotesEnhancedDocument.IconData) e.getAttribute(NotesEnhancedDocument.ICON_ATTRIBUTE);
			NotesEnhancedDocument.IconData actualIcon = (NotesEnhancedDocument.IconData) a.getAttribute(NotesEnhancedDocument.ICON_ATTRIBUTE);
			assertEquals(at + " icon", expectedIcon == null, actualIcon == null);
			if (expectedIcon != null)
			{
				assertEquals(at + " icon id", expectedIcon.itemId, actualIcon.itemId);
				assertEquals(at + " icon quantity", expectedIcon.itemQuantity, actualIcon.itemQuantity);
				continue;
			}

			assertEquals(at + " bold", StyleConstants.isBold(e), StyleConstants.isBold(a));
			assertEquals(at + " italic", StyleConstants.isItalic(e), StyleConstants.isItalic(a));
			assertEquals(at + " strike", StyleConstants.isStrikeThrough(e), StyleConstants.isStrikeThrough(a));
			assertEquals(at + " size", e.getAttribute(StyleConstants.FontSize), a.getAttribute(StyleConstants.FontSize));
			assertEquals(at + " color", rgb(e.getAttribute(StyleConstants.Foreground)), rgb(a.getAttribute(StyleConstants.Foreground)));
		}
	}

	private static Integer rgb(Object color)
	{
		return color instanceof Color ? ((Color) color).getRGB() : null;
	}

	private interface Styler
	{
		void apply(SimpleAttributeSet attrs);
	}

	private static void setStyle(StyledDocument doc, int start, int length, Styler styler)
	{
		SimpleAttributeSet attrs = new SimpleAttributeSet();
		styler.apply(attrs);
		doc.setCharacterAttributes(start, length, attrs, false);
	}

	private static void insertIcon(StyledDocument doc, int offset, NotesEnhancedDocument.IconData data) throws Exception
	{
		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setIcon(attrs, new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)));
		attrs.addAttribute(NotesEnhancedDocument.ICON_ATTRIBUTE, data);
		doc.insertString(offset, " ", attrs);
	}

	private static int gzipBase64Length(String json) throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes))
		{
			gzip.write(json.getBytes("UTF-8"));
		}
		return (bytes.size() + 2) / 3 * 4;
	}

	/**
	 * Returns a blank image instead of loading real item sprites.
	 */
	private static class FakeIconService extends ItemIconService
	{
		@Override
		public Icon getCenteredIconById(int itemId, int quantity)
		{
			return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
		}
	}
}
