package dev.seafoo.richtextnotes.services;

import dev.seafoo.richtextnotes.models.NoteContentV2;
import dev.seafoo.richtextnotes.models.NoteMetadata;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;

/**
 * Syncs opted-in notes through the RuneLite profile config, one config key per note, with limits
 * so it isn't general file storage. If a note changed both here and in the cloud, the cloud
 * version is kept as a separate "(conflict)" note.
 */
@Slf4j
public class CloudSyncService
{
	public static final int MAX_SYNCED_NOTES = 20;
	public static final int MAX_NOTE_SIZE = 8 * 1024; // characters, after compression

	private static final String CONFIG_GROUP = "richtextnotes";
	private static final String KEY_PREFIX = "sync_";

	private final ConfigManager configManager;
	private final FileStorageService storage;
	// Profile pull() last ran for. Uploads are skipped once RuneLite has switched to another profile.
	private Long syncedProfileId;

	public CloudSyncService(ConfigManager configManager, FileStorageService storage)
	{
		this.configManager = configManager;
		this.storage = storage;
	}

	public static class SyncLimitException extends Exception
	{
		SyncLimitException(String message)
		{
			super(message);
		}
	}

	/**
	 * What's stored in each config key.
	 */
	private static class CloudNote
	{
		long modifiedAt;
		// metadata.syncedVersion is the version the uploading computer had last synced
		NoteMetadata metadata;
		NoteContentV2 contentV2;
	}

	public void upload(NoteMetadata metadata, NoteContentV2 contentV2) throws SyncLimitException
	{
		if (!isCurrentProfile())
		{
			return;
		}

		String noteId = metadata.getNoteId();
		Set<String> syncedIds = getCloudNoteIds();
		if (!syncedIds.contains(noteId) && syncedIds.size() >= MAX_SYNCED_NOTES)
		{
			throw new SyncLimitException("You can sync up to " + MAX_SYNCED_NOTES + " notes.");
		}

		long version = version(metadata);
		if (version == metadata.getSyncedVersion() && syncedIds.contains(noteId))
		{
			return; // this version is already in the cloud
		}

		String encoded = encode(metadata, contentV2);
		if (encoded.length() > MAX_NOTE_SIZE)
		{
			throw new SyncLimitException("This note is too large to sync.");
		}

		writeConfig(KEY_PREFIX + noteId, encoded);
		metadata.setSyncedVersion(version);
		try
		{
			storage.saveNoteMetadata(noteId, metadata);
		}
		catch (IOException e)
		{
			log.warn("Failed to save synced version for note: {}", noteId, e);
		}
	}

	public int getSyncedNoteCount()
	{
		return getCloudNoteIds().size();
	}

	/**
	 * How much of MAX_NOTE_SIZE this note would use in the cloud.
	 */
	public int getCloudSize(NoteMetadata metadata, NoteContentV2 contentV2)
	{
		return encode(metadata, contentV2).length();
	}

	private String encode(NoteMetadata metadata, NoteContentV2 contentV2)
	{
		CloudNote cloudNote = new CloudNote();
		cloudNote.modifiedAt = version(metadata);
		cloudNote.metadata = metadata;
		cloudNote.contentV2 = contentV2;
		return compress(storage.getGson().toJson(cloudNote));
	}

	public void remove(String noteId)
	{
		if (!isCurrentProfile())
		{
			return;
		}
		deleteConfig(KEY_PREFIX + noteId);
	}

	/**
	 * Brings local notes up to date with the cloud for the current profile.
	 * Call before the notes panel loads notes, so no open editor gets overwritten.
	 */
	public void pull()
	{
		syncedProfileId = currentProfileId();
		Set<String> cloudIds = getCloudNoteIds();

		for (String noteId : cloudIds)
		{
			try
			{
				CloudNote cloudNote = download(noteId);
				if (cloudNote == null)
				{
					continue;
				}

				syncNote(noteId, cloudNote);
			}
			catch (Exception e)
			{
				log.warn("Failed to sync note: {}", noteId, e);
			}
		}

		// Missing from the cloud means it was deleted or unsynced elsewhere: keep it, just unsynced
		try
		{
			for (NoteMetadata metadata : storage.listNotesWithMetadata())
			{
				if (metadata.isSynced() && !cloudIds.contains(metadata.getNoteId()))
				{
					metadata.setSynced(false);
					storage.saveNoteMetadata(metadata.getNoteId(), metadata);
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to update sync state of local notes", e);
		}
	}

	private void syncNote(String noteId, CloudNote cloud) throws IOException, SyncLimitException
	{
		NoteMetadata local = storage.noteExists(noteId) ? storage.loadNoteMetadata(noteId) : null;
		if (local == null)
		{
			saveFromCloud(noteId, cloud);
			return;
		}

		long base = local.getSyncedVersion();
		long localVersion = version(local);
		boolean localEdited = localVersion != base;

		if (cloud.modifiedAt == base)
		{
			// Cloud hasn't changed since we last synced
			if (localEdited)
			{
				uploadLocal(noteId, local);
			}
			return;
		}

		if (!localEdited && cloud.metadata.getSyncedVersion() == base)
		{
			// Another computer edited our version; take it
			saveFromCloud(noteId, cloud);
			return;
		}

		// Both changed, or our last upload was overwritten: keep both versions
		saveConflictCopy(cloud);
		local.setSyncedVersion(cloud.modifiedAt);
		uploadLocal(noteId, local);
	}

	private void saveFromCloud(String noteId, CloudNote cloud) throws IOException
	{
		storage.createBackup(noteId);
		NoteMetadata metadata = cloud.metadata;
		metadata.setNoteId(noteId);
		metadata.setSynced(true);
		metadata.setSyncedVersion(cloud.modifiedAt);
		// lastModified is only for display; sync compares modifiedAt
		metadata.setModifiedAt(cloud.modifiedAt);
		metadata.setLastModified(fromEpochMillis(cloud.modifiedAt));
		storage.saveNote(noteId, cloud.contentV2);
		storage.saveNoteMetadata(noteId, metadata);
	}

	private void saveConflictCopy(CloudNote cloud) throws IOException
	{
		String copyId = storage.saveNote(null, cloud.contentV2);
		NoteMetadata metadata = cloud.metadata;
		metadata.setNoteId(copyId);
		metadata.setTitle(metadata.getTitle() + " (conflict)");
		metadata.setSynced(false);
		metadata.setSyncedVersion(0);
		metadata.setModifiedAt(cloud.modifiedAt);
		metadata.setLastModified(fromEpochMillis(cloud.modifiedAt));
		storage.saveNoteMetadata(copyId, metadata);
		log.info("Sync conflict: saved the cloud version as {}", copyId);
	}

	private void uploadLocal(String noteId, NoteMetadata local) throws IOException, SyncLimitException
	{
		NoteContentV2 content = storage.loadContentV2(noteId);
		if (content == null)
		{
			return;
		}
		local.setSynced(true);
		storage.saveNoteMetadata(noteId, local);
		upload(local, content);
	}

	private CloudNote download(String noteId)
	{
		String encoded = readConfig(KEY_PREFIX + noteId);
		if (encoded == null)
		{
			return null;
		}

		try
		{
			return storage.getGson().fromJson(decompress(encoded), CloudNote.class);
		}
		catch (Exception e)
		{
			log.warn("Could not read synced note: {}", noteId, e);
			return null;
		}
	}

	private Set<String> getCloudNoteIds()
	{
		String prefix = CONFIG_GROUP + "." + KEY_PREFIX;
		return configKeys(prefix).stream()
			.map(key -> key.substring(prefix.length()))
			.collect(Collectors.toCollection(HashSet::new));
	}

	// Config access, overridable so tests can run without a RuneLite client

	protected String readConfig(String key)
	{
		return configManager.getConfiguration(CONFIG_GROUP, key);
	}

	protected void writeConfig(String key, String value)
	{
		configManager.setConfiguration(CONFIG_GROUP, key, value);
	}

	protected void deleteConfig(String key)
	{
		configManager.unsetConfiguration(CONFIG_GROUP, key);
	}

	protected List<String> configKeys(String wholeKeyPrefix)
	{
		return configManager.getConfigurationKeys(wholeKeyPrefix);
	}

	protected Long currentProfileId()
	{
		ConfigProfile profile = configManager.getProfile();
		return profile != null ? profile.getId() : null;
	}

	private boolean isCurrentProfile()
	{
		return Objects.equals(syncedProfileId, currentProfileId());
	}

	/**
	 * Unix ms of the note's last change. Older notes without modifiedAt fall back to lastModified.
	 */
	private static long version(NoteMetadata metadata)
	{
		if (metadata.getModifiedAt() != 0)
		{
			return metadata.getModifiedAt();
		}
		LocalDateTime time = metadata.getLastModified();
		return time == null ? 0 : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private static LocalDateTime fromEpochMillis(long millis)
	{
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
	}

	private static String compress(String json)
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes))
		{
			gzip.write(json.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e); // can't happen writing to memory
		}
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private static String decompress(String encoded) throws IOException
	{
		byte[] compressed = Base64.getDecoder().decode(encoded);
		try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed)))
		{
			return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
