package dev.seafoo.richtextnotes.services;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.seafoo.richtextnotes.models.EditorLayout;
import dev.seafoo.richtextnotes.models.NoteContentV2;
import dev.seafoo.richtextnotes.models.NoteMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.util.Filepath;

@Slf4j
public class FileStorageService
{
	private static final String NOTES_SUBDIR = "notes";
	private static final String BACKUP_SUBDIR = "backups";

	private static final String V2_EXT = ".note";
	private static final String V1_EXT = ".rtf";
	private static final String METADATA_EXT = ".json";

	private static final String DEFAULT_PROFILE = "default_profile";

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;


	private final Gson gson;
	private final Filepath baseDirectory;
	private final ConfigManager configManager;
	private String currentProfileDirectory;

	/**
	 * @param baseDirectory the plugin's data directory (Plugin.getPluginDirectory())
	 */
	public FileStorageService(ConfigManager configManager, Gson gson, Filepath baseDirectory)
	{
		this.configManager = configManager;
		this.gson = gson.newBuilder().setPrettyPrinting()
			.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
			.create();
		this.baseDirectory = baseDirectory;

		initializeDirectories();
		updateCurrentProfile();
	}

	private void initializeDirectories()
	{
		try
		{
			baseDirectory.createDirectories();
			log.debug("Initialized rich-text-notes directory structure at: {}", baseDirectory);
		}
		catch (IOException e)
		{
			log.error("Failed to initialize directory structure", e);
			throw new RuntimeException("Could not create notes directories", e);
		}
	}

	/**
	 * Profile Management Methods
	 */

	public String getCurrentProfileName()
	{
		if (configManager != null)
		{
			ConfigProfile profile = configManager.getProfile();
			if (profile != null)
			{
				return profile.getName();
			}
		}
		return DEFAULT_PROFILE;
	}

	public String getDisplayProfileName()
	{
		return getCurrentProfileName();
	}

	public String updateCurrentProfile()
	{
		String newProfileDir = determineProfileDirectory();

		if (!newProfileDir.equals(currentProfileDirectory))
		{
			String oldProfile = currentProfileDirectory;
			currentProfileDirectory = newProfileDir;
			createProfileDirectories();

			log.debug("Profile directory changed: {} -> {}", oldProfile, newProfileDir);
		}

		return getCurrentProfileName();
	}

	private String determineProfileDirectory()
	{
		if (configManager != null)
		{
			ConfigProfile profile = configManager.getProfile();
			if (profile != null)
			{
				// Use profile ID to ensure uniqueness even if names are similar
				return "profile_" + profile.getId() + "_" + sanitizeProfileName(profile.getName());
			}
		}
		return DEFAULT_PROFILE;
	}

	private void createProfileDirectories()
	{
		try
		{
			getNotesDirectory().createDirectories();
			getProfileDirectory().joinSegment(BACKUP_SUBDIR).createDirectories();

			log.debug("Created directories for profile: {}", currentProfileDirectory);

		}
		catch (IOException e)
		{
			log.error("Failed to create profile directories for: {}", currentProfileDirectory, e);
			throw new RuntimeException("Could not create profile directories", e);
		}

		try
		{
			backupBeforeV2();
		}
		catch (IOException e)
		{
			log.warn("Failed to back up notes before format change", e);
		}
	}

	private String sanitizeProfileName(String profileName)
	{
		if (profileName == null || profileName.trim().isEmpty())
		{
			return "unnamed";
		}

		// Remove invalid filename characters and limit length
		return profileName.replaceAll("[^a-zA-Z0-9_-]", "_")
			.substring(0, Math.min(profileName.length(), 30));
	}

	public Filepath getProfileDirectory()
	{
		if (currentProfileDirectory == null)
		{
			throw new IllegalStateException("No profile directory set");
		}
		return baseDirectory.joinSegment(currentProfileDirectory);
	}

	/**
	 * Note file operations
	 */

	/**
	 * Writes the note as v2. v1 (.rtf) files are only ever read, never written.
	 */
	public String saveNote(String noteId, NoteContentV2 contentV2) throws IOException
	{
		if (noteId == null)
		{
			noteId = generateNoteId();
		}

		writeAtomically(noteFile(noteId, V2_EXT), gson.toJson(contentV2));
		log.debug("Saved note: {} for profile: {}", noteId, getCurrentProfileName());
		return noteId;
	}

	/**
	 * Returns null if there's no readable .note (a v1 note, or a corrupt file), so callers fall back to the .rtf.
	 */
	public NoteContentV2 loadContentV2(String noteId) throws IOException
	{
		Filepath file = noteFile(noteId, V2_EXT);
		if (!file.exists())
		{
			return null;
		}
		try
		{
			return gson.fromJson(read(file), NoteContentV2.class);
		}
		catch (JsonParseException e)
		{
			log.warn("Could not read note: {}", file, e);
			return null;
		}
	}

	/**
	 * Writes to a temporary file first, so a crash mid-write can't leave a half-written note.
	 */
	private static void writeAtomically(Filepath file, String content) throws IOException
	{
		Filepath temp = file.getParent().joinSegment(file.getFileName() + ".tmp");
		temp.write(content);
		temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static String read(Filepath file) throws IOException
	{
		try (InputStream in = file.openInputStream())
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	public String loadRtfV1(String noteId) throws IOException
	{
		Filepath file = noteFile(noteId, V1_EXT);

		if (!file.exists())
		{
			throw new IOException("Note not found: " + noteId + " in profile: " + getCurrentProfileName());
		}

		return read(file);
	}

	public void deleteNote(String noteId) throws IOException
	{
		// Create backup before deletion
		createBackup(noteId);

		noteFile(noteId, V2_EXT).deleteIfExists();
		noteFile(noteId, V1_EXT).deleteIfExists();
		noteFile(noteId, METADATA_EXT).deleteIfExists();
		log.debug("Deleted note: {} from profile: {}", noteId, getCurrentProfileName());
	}

	/**
	 * Metadata operations
	 */

	public void saveNoteMetadata(String noteId, NoteMetadata metadata) throws IOException
	{
		writeAtomically(noteFile(noteId, METADATA_EXT), gson.toJson(metadata));
	}

	public NoteMetadata loadNoteMetadata(String noteId) throws IOException
	{
		Filepath metadataFile = noteFile(noteId, METADATA_EXT);

		if (!metadataFile.exists())
		{
			// Return default metadata if none exists
			return new NoteMetadata(noteId, "Untitled Note", LocalDateTime.now(), new ArrayList<>());
		}

		return gson.fromJson(read(metadataFile), NoteMetadata.class);
	}

	/**
	 * Layout persistence
	 */

	public void saveEditorLayout(EditorLayout layout) throws IOException
	{
		getProfileDirectory().joinSegment("layout.json").write(gson.toJson(layout));
	}

	public EditorLayout loadEditorLayout() throws IOException
	{
		Filepath layoutFile = getProfileDirectory().joinSegment("layout.json");

		if (!layoutFile.exists())
		{
			return new EditorLayout(); // Return default layout
		}

		return gson.fromJson(read(layoutFile), EditorLayout.class);
	}

	/**
	 * List operations
	 */

	public List<String> listNotes() throws IOException
	{
		return listFileNames(getNotesDirectory()).stream()
			.filter(name -> name.endsWith(V2_EXT) || name.endsWith(V1_EXT))
			.map(name -> name.substring(0, name.lastIndexOf('.')))
			.distinct()
			.collect(Collectors.toList());
	}

	private static List<String> listFileNames(Filepath directory) throws IOException
	{
		if (!directory.isDirectory())
		{
			return new ArrayList<>();
		}

		try (Stream<Filepath> files = directory.walk(1))
		{
			return files.filter(Filepath::isFile)
				.map(Filepath::getFileName)
				.collect(Collectors.toList());
		}
	}

	public List<NoteMetadata> listNotesWithMetadata() throws IOException
	{
		List<String> noteIds = listNotes();
		List<NoteMetadata> metadataList = new ArrayList<>();

		for (String noteId : noteIds)
		{
			try
			{
				metadataList.add(loadNoteMetadata(noteId));
			}
			catch (IOException e)
			{
				log.warn("Could not load metadata for note: {} in profile: {}", noteId, getCurrentProfileName(), e);
				// Add default metadata for notes without metadata files
				metadataList.add(new NoteMetadata(noteId, "Untitled Note", LocalDateTime.now(), new ArrayList<>()));
			}
		}

		return metadataList;
	}

	/**
	 * Backup operations
	 */

	/**
	 * Copies a note's files to a timestamped backup folder. Used before deleting a note
	 * or overwriting it with a newer copy from the cloud.
	 */
	public void createBackup(String noteId) throws IOException
	{
		if (!noteExists(noteId))
		{
			return; // Nothing to back up
		}

		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		Filepath backupDir = getProfileDirectory().joinSegment(BACKUP_SUBDIR).joinSegment(timestamp);
		backupDir.createDirectories();

		for (String ext : new String[]{V2_EXT, V1_EXT, METADATA_EXT})
		{
			Filepath file = noteFile(noteId, ext);
			if (file.exists())
			{
				file.copyTo(backupDir.joinSegment(noteId + ext), StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	/**
	 * One-off copy of the notes folder the first time v2 runs,
	 * in case converting v1 notes to v2 goes wrong.
	 */
	private void backupBeforeV2() throws IOException
	{
		Filepath notesDir = getNotesDirectory();
		Filepath target = getProfileDirectory().joinSegment(BACKUP_SUBDIR).joinSegment("before-v2");
		if (target.exists())
		{
			return;
		}

		List<String> files = listFileNames(notesDir);
		boolean hasV1Notes = files.stream().anyMatch(name -> name.endsWith(V1_EXT));
		boolean hasV2Notes = files.stream().anyMatch(name -> name.endsWith(V2_EXT));
		if (hasV1Notes && !hasV2Notes)
		{
			// The notes folder only holds files, no subfolders
			target.createDirectories();
			for (String name : files)
			{
				notesDir.joinSegment(name).copyTo(target.joinSegment(name), StandardCopyOption.REPLACE_EXISTING);
			}
			log.debug("Backed up notes before format change to: {}", target);
		}
	}

	private String generateNoteId()
	{
		return "note_" + System.currentTimeMillis() + "_" + new Random().nextInt(1000);
	}

	public boolean noteExists(String noteId)
	{
		return noteFile(noteId, V2_EXT).exists() || noteFile(noteId, V1_EXT).exists();
	}

	private Filepath noteFile(String noteId, String ext)
	{
		return getNotesDirectory().joinSegment(noteId + ext);
	}

	public Gson getGson()
	{
		return gson;
	}

	public Filepath getNotesDirectory()
	{
		return getProfileDirectory().joinSegment(NOTES_SUBDIR);
	}

	private static class LocalDateTimeTypeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime>
	{
		@Override
		public JsonElement serialize(LocalDateTime localDateTime, Type type, JsonSerializationContext context)
		{
			// Serialize as ISO string
			return new JsonPrimitive(localDateTime.format(DATE_TIME_FORMATTER));
		}

		@Override
		public LocalDateTime deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context)
			throws JsonParseException
		{
			try
			{
				// Handle the case where it's already a primitive string
				if (jsonElement.isJsonPrimitive())
				{
					String dateTimeString = jsonElement.getAsString();
					return LocalDateTime.parse(dateTimeString, DATE_TIME_FORMATTER);
				}

				// Handle the complex object format that's causing the issue
				if (jsonElement.isJsonObject())
				{
					JsonObject dateTimeObj = jsonElement.getAsJsonObject();

					// Check if it has the nested structure: {"date": {...}, "time": {...}}
					if (dateTimeObj.has("date") && dateTimeObj.has("time"))
					{
						JsonObject dateObj = dateTimeObj.getAsJsonObject("date");
						JsonObject timeObj = dateTimeObj.getAsJsonObject("time");

						int year = dateObj.get("year").getAsInt();
						int month = dateObj.get("month").getAsInt();
						int day = dateObj.get("day").getAsInt();

						int hour = timeObj.get("hour").getAsInt();
						int minute = timeObj.get("minute").getAsInt();
						int second = timeObj.get("second").getAsInt();
						int nano = timeObj.has("nano") ? timeObj.get("nano").getAsInt() : 0;

						return LocalDateTime.of(year, month, day, hour, minute, second, nano);
					}

					// Handle direct object format: {"year": 2025, "month": 8, ...}
					if (dateTimeObj.has("year") && dateTimeObj.has("month"))
					{
						int year = dateTimeObj.get("year").getAsInt();
						int month = dateTimeObj.get("month").getAsInt();
						int day = dateTimeObj.get("day").getAsInt();
						int hour = dateTimeObj.has("hour") ? dateTimeObj.get("hour").getAsInt() : 0;
						int minute = dateTimeObj.has("minute") ? dateTimeObj.get("minute").getAsInt() : 0;
						int second = dateTimeObj.has("second") ? dateTimeObj.get("second").getAsInt() : 0;
						int nano = dateTimeObj.has("nano") ? dateTimeObj.get("nano").getAsInt() : 0;

						return LocalDateTime.of(year, month, day, hour, minute, second, nano);
					}
				}

				// Fallback - try to parse as string
				String dateTimeString = jsonElement.getAsString();
				return LocalDateTime.parse(dateTimeString, DATE_TIME_FORMATTER);

			}
			catch (DateTimeParseException e)
			{
				log.warn("Failed to parse LocalDateTime from: {}, using current time", jsonElement, e);
				return LocalDateTime.now();
			}
			catch (Exception e)
			{
				log.error("Unexpected error parsing LocalDateTime from: {}, using current time", jsonElement, e);
				return LocalDateTime.now();
			}
		}
	}

}