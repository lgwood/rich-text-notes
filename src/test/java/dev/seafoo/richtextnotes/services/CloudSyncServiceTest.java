package dev.seafoo.richtextnotes.services;

import static org.junit.Assert.assertEquals;
import com.google.gson.Gson;
import dev.seafoo.richtextnotes.models.NoteContentV2;
import dev.seafoo.richtextnotes.models.NoteMetadata;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Two computers sharing one cloud. Each "start" is RuneLite starting up and pulling,
 * each "push" is the note being written to config (focus loss, tab close, ConfigSync).
 */
public class CloudSyncServiceTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static final LocalDateTime T = LocalDateTime.of(2026, 11, 1, 12, 0);

	private final Map<String, String> cloud = new HashMap<>();
	private Computer desktop;
	private Computer laptop;

	@Before
	public void setUp() throws IOException
	{
		desktop = new Computer();
		laptop = new Computer();
	}

	@Test
	public void editOnOneComputerReachesTheOther() throws Exception
	{
		String id = desktop.createSyncedNote("v1", 1);
		laptop.start();
		assertEquals("v1", laptop.text(id));

		desktop.edit(id, "v2", 2);
		desktop.push(id);
		laptop.start();

		assertEquals("v2", laptop.text(id));
		assertEquals(1, laptop.noteCount());
	}

	@Test
	public void unpushedEditIsUploadedOnNextStart() throws Exception
	{
		String id = desktop.createSyncedNote("v1", 1);
		laptop.start();

		laptop.edit(id, "laptop edit", 2); // closed before it was pushed
		laptop.start();
		desktop.start();

		assertEquals("laptop edit", desktop.text(id));
		assertEquals(1, desktop.noteCount());
	}

	@Test
	public void bothComputersEditedKeepsBoth() throws Exception
	{
		String id = desktop.createSyncedNote("v1", 1);
		laptop.start();

		desktop.edit(id, "desktop edit", 2);
		desktop.push(id);
		laptop.edit(id, "laptop edit", 3); // laptop was offline, so it didn't see the desktop edit
		laptop.start();

		assertEquals("laptop edit", laptop.text(id));
		assertEquals("desktop edit", laptop.text(laptop.conflictCopyOf("Note")));

		// The desktop then just takes the laptop's version; the desktop edit lives in the laptop's copy
		desktop.start();
		assertEquals("laptop edit", desktop.text(id));
		assertEquals(1, desktop.noteCount());
	}

	@Test
	public void overwrittenUploadKeepsBoth() throws Exception
	{
		String id = desktop.createSyncedNote("v1", 1);
		laptop.start();

		// Both open: the laptop never sees the desktop's upload and overwrites it
		desktop.edit(id, "desktop edit", 2);
		desktop.push(id);
		laptop.edit(id, "laptop edit", 3);
		laptop.push(id);

		desktop.start();
		assertEquals("desktop edit", desktop.text(id));
		assertEquals("laptop edit", desktop.text(desktop.conflictCopyOf("Note")));

		// The laptop gets the desktop's version and has no conflict copy of its own
		laptop.start();
		assertEquals("desktop edit", laptop.text(id));
		assertEquals(1, laptop.noteCount());
	}

	@Test
	public void timeZoneChangeIsNotAnEdit() throws Exception
	{
		String id = desktop.createSyncedNote("v1", 1);
		laptop.start();

		// The laptop moves to another time zone, which shifts its local date and time
		NoteMetadata metadata = laptop.storage.loadNoteMetadata(id);
		metadata.setLastModified(metadata.getLastModified().plusHours(5));
		laptop.storage.saveNoteMetadata(id, metadata);

		desktop.edit(id, "v2", 2);
		desktop.push(id);
		laptop.start();

		assertEquals("v2", laptop.text(id));
		assertEquals(1, laptop.noteCount());
	}

	@Test
	public void restartingWithoutChangesMakesNoCopies() throws Exception
	{
		String id = desktop.createSyncedNote("v1", 1);
		laptop.start();
		laptop.start();
		desktop.start();
		desktop.start();

		assertEquals("v1", laptop.text(id));
		assertEquals(1, laptop.noteCount());
		assertEquals(1, desktop.noteCount());
	}

	private class Computer
	{
		final FileStorageService storage;
		final CloudSyncService sync;

		Computer() throws IOException
		{
			storage = new FileStorageService(null, new Gson(), folder.newFolder().toPath());
			sync = new CloudSyncService(null, storage)
			{
				@Override
				protected String readConfig(String key)
				{
					return cloud.get(key);
				}

				@Override
				protected void writeConfig(String key, String value)
				{
					cloud.put(key, value);
				}

				@Override
				protected void deleteConfig(String key)
				{
					cloud.remove(key);
				}

				@Override
				protected List<String> configKeys(String wholeKeyPrefix)
				{
					return cloud.keySet().stream().map(key -> "richtextnotes." + key)
						.filter(key -> key.startsWith(wholeKeyPrefix)).collect(Collectors.toList());
				}

				@Override
				protected Long currentProfileId()
				{
					return 1L;
				}
			};
			sync.pull(); // RuneLite starting up
		}

		String createSyncedNote(String text, int minute) throws Exception
		{
			String id = storage.saveNote(null, content(text));
			NoteMetadata metadata = new NoteMetadata(id, "Note", T, new ArrayList<>());
			touch(metadata, minute);
			metadata.setSynced(true);
			storage.saveNoteMetadata(id, metadata);
			push(id);
			return id;
		}

		void edit(String id, String text, int minute) throws IOException
		{
			storage.saveNote(id, content(text));
			NoteMetadata metadata = storage.loadNoteMetadata(id);
			touch(metadata, minute);
			storage.saveNoteMetadata(id, metadata);
		}

		void push(String id) throws Exception
		{
			sync.upload(storage.loadNoteMetadata(id), storage.loadContentV2(id));
		}

		void start()
		{
			sync.pull();
		}

		String text(String id) throws IOException
		{
			return storage.loadContentV2(id).getText();
		}

		int noteCount() throws IOException
		{
			return storage.listNotes().size();
		}

		String conflictCopyOf(String title) throws IOException
		{
			List<NoteMetadata> copies = storage.listNotesWithMetadata().stream()
				.filter(m -> m.getTitle().equals(title + " (conflict)"))
				.collect(Collectors.toList());
			assertEquals("conflict copies of " + title, 1, copies.size());
			assertEquals("conflict copy is local only", false, copies.get(0).isSynced());
			return copies.get(0).getNoteId();
		}
	}

	// What updateModified does, at a fixed time
	private static void touch(NoteMetadata metadata, int minute)
	{
		metadata.setLastModified(T.plusMinutes(minute));
		metadata.setModifiedAt(T.plusMinutes(minute).toEpochSecond(ZoneOffset.UTC) * 1000);
	}

	private static NoteContentV2 content(String text)
	{
		NoteContentV2 content = new NoteContentV2();
		content.setText(text);
		return content;
	}
}
