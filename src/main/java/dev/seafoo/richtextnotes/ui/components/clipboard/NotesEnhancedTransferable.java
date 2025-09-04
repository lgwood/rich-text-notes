package dev.seafoo.richtextnotes.ui.components.clipboard;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Transferable implementation for Notes Enhanced clipboard operations
 */
@Slf4j
public class NotesEnhancedTransferable implements Transferable
{

	// Custom data flavor for our enhanced clipboard data
	public static final DataFlavor NOTES_ENHANCED_FLAVOR;

	// Standard data flavors
	private static final DataFlavor[] SUPPORTED_FLAVORS;

	static
	{
		DataFlavor customFlavor;
		customFlavor = new DataFlavor(
			"application/x-rich-text-notes-content",
			"Rich Text Notes Content"
		);
		NOTES_ENHANCED_FLAVOR = customFlavor;

		// Define supported flavors in order of preference
		SUPPORTED_FLAVORS = new DataFlavor[]{
			NOTES_ENHANCED_FLAVOR,      // Our custom format (highest priority)
			DataFlavor.stringFlavor     // Plain text (fallback)
		};
	}

	private final NotesEnhancedClipboardData clipboardData;

	public NotesEnhancedTransferable(NotesEnhancedClipboardData clipboardData)
	{
		this.clipboardData = clipboardData != null ? clipboardData : new NotesEnhancedClipboardData();
	}

	@Override
	public DataFlavor[] getTransferDataFlavors()
	{
		return SUPPORTED_FLAVORS.clone();
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor)
	{
		for (DataFlavor supportedFlavor : SUPPORTED_FLAVORS)
		{
			if (supportedFlavor.equals(flavor))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
	{
		if (!isDataFlavorSupported(flavor))
		{
			throw new UnsupportedFlavorException(flavor);
		}

		try
		{
			// Return our custom data if requested
			if (NOTES_ENHANCED_FLAVOR.equals(flavor))
			{
				return clipboardData;
			}

			// Return plain text for standard text flavor
			if (DataFlavor.stringFlavor.equals(flavor))
			{
				String plainText = clipboardData.getPlainText();
				return plainText != null ? plainText : "";
			}

			// Should not reach here due to isDataFlavorSupported check
			throw new UnsupportedFlavorException(flavor);

		}
		catch (Exception e)
		{
			log.error("Error getting transfer data for flavor: " + flavor, e);

			// Fallback to plain text if something goes wrong
			if (DataFlavor.stringFlavor.equals(flavor))
			{
				return clipboardData.getPlainText() != null ? clipboardData.getPlainText() : "";
			}

			throw new IOException("Failed to get transfer data", e);
		}
	}
}