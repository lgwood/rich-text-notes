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

	private static final String DEFAULT_PROFILE = "default_profile";

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;


	private final Gson gson;
	private final Path baseDirectory;
	private final ConfigManager configManager;
	private String currentProfileDirectory;

	public FileStorageService(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson.newBuilder().setPrettyPrinting()
			.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
			.create();
		this.baseDirectory = RuneLite.RUNELITE_DIR.toPath().resolve(NOTES_ENHANCED_DIR);

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

	public String saveNote(String noteId, String rtfContent) throws IOException
	{
		if (noteId == null)
		{
			noteId = generateNoteId();
		}

		Path noteFile = getProfileDirectory().resolve(NOTES_SUBDIR).resolve(noteId + ".rtf");

		// Create backup if file exists
		if (Files.exists(noteFile))
		{
			createBackup(noteId);
		}

		Files.write(noteFile, rtfContent.getBytes(StandardCharsets.UTF_8));
		log.debug("Saved note: {} for profile: {}", noteId, getCurrentProfileName());
		return noteId;
	}

	public String loadNote(String noteId) throws IOException
	{
		Path noteFile = getProfileDirectory().resolve(NOTES_SUBDIR).resolve(noteId + ".rtf");

		if (!Files.exists(noteFile))
		{
			throw new FileNotFoundException("Note not found: " + noteId + " in profile: " + getCurrentProfileName());
		}

		return new String(Files.readAllBytes(noteFile), StandardCharsets.UTF_8);
	}

	public void deleteNote(String noteId) throws IOException
	{
		// Create backup before deletion
		createBackup(noteId);

		Path noteFile = getProfileDirectory().resolve(NOTES_SUBDIR).resolve(noteId + ".rtf");
		Path metadataFile = getProfileDirectory().resolve(NOTES_SUBDIR).resolve(noteId + ".json");

		Files.deleteIfExists(noteFile);
		Files.deleteIfExists(metadataFile);
		log.debug("Deleted note: {} from profile: {}", noteId, getCurrentProfileName());
	}

	/**
	 * Metadata operations
	 */

	public void saveNoteMetadata(String noteId, NoteMetadata metadata) throws IOException
	{
		Path metadataFile = getProfileDirectory().resolve(NOTES_SUBDIR).resolve(noteId + ".json");
		String json = gson.toJson(metadata);
		Files.write(metadataFile, json.getBytes(StandardCharsets.UTF_8));
	}

	public NoteMetadata loadNoteMetadata(String noteId) throws IOException
	{
		Path metadataFile = getProfileDirectory().resolve(NOTES_SUBDIR).resolve(noteId + ".json");

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
			.filter(path -> path.toString().endsWith(".rtf"))
			.map(path -> {
				String filename = path.getFileName().toString();
				return filename.substring(0, filename.length() - 4); // Remove .rtf extension
			})
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

	private void createBackup(String noteId) throws IOException
	{
		Path noteFile = getProfileDirectory().resolve(NOTES_SUBDIR).resolve(noteId + ".rtf");
		Path metadataFile = getProfileDirectory().resolve(NOTES_SUBDIR).resolve(noteId + ".json");

		if (!Files.exists(noteFile))
		{
			return; // Nothing to back up
		}

		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		Path backupDir = getProfileDirectory().resolve(BACKUP_SUBDIR).resolve(timestamp);
		Files.createDirectories(backupDir);

		Files.copy(noteFile, backupDir.resolve(noteId + ".rtf"));
		if (Files.exists(metadataFile))
		{
			Files.copy(metadataFile, backupDir.resolve(noteId + ".json"));
		}
	}

	public void createFullBackup() throws IOException
	{
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		Path backupDir = getProfileDirectory().resolve(BACKUP_SUBDIR).resolve("full_" + timestamp);
		Files.createDirectories(backupDir);

		// Copy all notes and metadata
		copyDirectoryRecursively(getProfileDirectory().resolve(NOTES_SUBDIR),
			backupDir.resolve(NOTES_SUBDIR));
		copyDirectoryRecursively(getProfileDirectory().resolve(NOTES_SUBDIR),
			backupDir.resolve(NOTES_SUBDIR));

		// Copy layout
		Path layoutFile = getProfileDirectory().resolve("layout.json");
		if (Files.exists(layoutFile))
		{
			Files.copy(layoutFile, backupDir.resolve("layout.json"));
		}

		log.debug("Created full backup for profile {}: {}", getCurrentProfileName(), backupDir);
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
		Path noteFile = getProfileDirectory().resolve(NOTES_SUBDIR).resolve(noteId + ".rtf");
		return Files.exists(noteFile);
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