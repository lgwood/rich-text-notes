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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;

@Slf4j
public class FileStorageService
{
	private static final String NOTES_ENHANCED_DIR = "rich-text-notes";
	private static final String NOTES_SUBDIR = "notes";
	private static final String BACKUP_SUBDIR = "backups";

	private static final String V2_EXT = ".note";
	private static final String V1_EXT = ".rtf";
	private static final String METADATA_EXT = ".json";

	private static final String DEFAULT_PROFILE = "default_profile";

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;


	private final Gson gson;
	private final Path baseDirectory;
	private final ConfigManager configManager;
	private String currentProfileDirectory;

	public FileStorageService(ConfigManager configManager, Gson gson)
	{
		this(configManager, gson, RuneLite.RUNELITE_DIR.toPath().resolve(NOTES_ENHANCED_DIR));
	}

	// Separate base directory so tests don't touch the real notes folder
	FileStorageService(ConfigManager configManager, Gson gson, Path baseDirectory)
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
			Files.createDirectories(baseDirectory);
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
			Path profileDir = getProfileDirectory();
			Files.createDirectories(profileDir.resolve(NOTES_SUBDIR));
			Files.createDirectories(profileDir.resolve(BACKUP_SUBDIR));

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

	public Path getProfileDirectory()
	{
		if (currentProfileDirectory == null)
		{
			throw new IllegalStateException("No profile directory set");
		}
		return baseDirectory.resolve(currentProfileDirectory);
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
		Path file = noteFile(noteId, V2_EXT);
		if (!Files.exists(file))
		{
			return null;
		}
		try
		{
			return gson.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), NoteContentV2.class);
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
	private static void writeAtomically(Path file, String content) throws IOException
	{
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	public String loadRtfV1(String noteId) throws IOException
	{
		Path file = noteFile(noteId, V1_EXT);

		if (!Files.exists(file))
		{
			throw new FileNotFoundException("Note not found: " + noteId + " in profile: " + getCurrentProfileName());
		}

		return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
	}

	public void deleteNote(String noteId) throws IOException
	{
		// Create backup before deletion
		createBackup(noteId);

		Files.deleteIfExists(noteFile(noteId, V2_EXT));
		Files.deleteIfExists(noteFile(noteId, V1_EXT));
		Files.deleteIfExists(noteFile(noteId, METADATA_EXT));
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
		Path metadataFile = noteFile(noteId, METADATA_EXT);

		if (!Files.exists(metadataFile))
		{
			// Return default metadata if none exists
			return new NoteMetadata(noteId, "Untitled Note", LocalDateTime.now(), new ArrayList<>());
		}

		String json = new String(Files.readAllBytes(metadataFile), StandardCharsets.UTF_8);
		return gson.fromJson(json, NoteMetadata.class);
	}

	/**
	 * Layout persistence
	 */

	public void saveEditorLayout(EditorLayout layout) throws IOException
	{
		Path layoutFile = getProfileDirectory().resolve("layout.json");
		String json = gson.toJson(layout);
		Files.write(layoutFile, json.getBytes(StandardCharsets.UTF_8));
	}

	public EditorLayout loadEditorLayout() throws IOException
	{
		Path layoutFile = getProfileDirectory().resolve("layout.json");

		if (!Files.exists(layoutFile))
		{
			return new EditorLayout(); // Return default layout
		}

		String json = new String(Files.readAllBytes(layoutFile), StandardCharsets.UTF_8);
		return gson.fromJson(json, EditorLayout.class);
	}

	/**
	 * List operations
	 */

	public List<String> listNotes() throws IOException
	{
		Path notesDir = getProfileDirectory().resolve(NOTES_SUBDIR);

		if (!Files.exists(notesDir))
		{
			return new ArrayList<>();
		}

		return Files.list(notesDir)
			.map(path -> path.getFileName().toString())
			.filter(name -> name.endsWith(V2_EXT) || name.endsWith(V1_EXT))
			.map(name -> name.substring(0, name.lastIndexOf('.')))
			.distinct()
			.collect(Collectors.toList());
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
		Path backupDir = getProfileDirectory().resolve(BACKUP_SUBDIR).resolve(timestamp);
		Files.createDirectories(backupDir);

		for (String ext : new String[]{V2_EXT, V1_EXT, METADATA_EXT})
		{
			Path file = noteFile(noteId, ext);
			if (Files.exists(file))
			{
				Files.copy(file, backupDir.resolve(noteId + ext), StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	/**
	 * One-off copy of the notes folder the first time v2 runs,
	 * in case converting v1 notes to v2 goes wrong.
	 */
	private void backupBeforeV2() throws IOException
	{
		Path notesDir = getNotesDirectory();
		Path target = getProfileDirectory().resolve(BACKUP_SUBDIR).resolve("before-v2");
		if (Files.exists(target))
		{
			return;
		}

		List<String> files;
		try (Stream<Path> stream = Files.list(notesDir))
		{
			files = stream.map(path -> path.getFileName().toString()).collect(Collectors.toList());
		}
		boolean hasV1Notes = files.stream().anyMatch(name -> name.endsWith(V1_EXT));
		boolean hasV2Notes = files.stream().anyMatch(name -> name.endsWith(V2_EXT));
		if (hasV1Notes && !hasV2Notes)
		{
			copyDirectoryRecursively(notesDir, target);
			log.debug("Backed up notes before format change to: {}", target);
		}
	}

	private void copyDirectoryRecursively(Path source, Path target) throws IOException
	{
		if (!Files.exists(source))
		{
			return;
		}

		Files.createDirectories(target);
		Files.walk(source)
			.forEach(sourcePath -> {
				try
				{
					Path targetPath = target.resolve(source.relativize(sourcePath));
					if (Files.isDirectory(sourcePath))
					{
						Files.createDirectories(targetPath);
					}
					else
					{
						Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				catch (IOException e)
				{
					log.error("Failed to copy: {}", sourcePath, e);
				}
			});
	}

	private String generateNoteId()
	{
		return "note_" + System.currentTimeMillis() + "_" + new Random().nextInt(1000);
	}

	public boolean noteExists(String noteId)
	{
		return Files.exists(noteFile(noteId, V2_EXT)) || Files.exists(noteFile(noteId, V1_EXT));
	}

	private Path noteFile(String noteId, String ext)
	{
		return getNotesDirectory().resolve(noteId + ext);
	}

	public Gson getGson()
	{
		return gson;
	}

	public Path getNotesDirectory()
	{
		return getProfileDirectory().resolve(NOTES_SUBDIR);
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