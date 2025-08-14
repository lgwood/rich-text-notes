package dev.seafoo.notesenhanced.ui.components;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * ImageIcon wrapper that properly handles AsyncBufferedImage loading
 * and triggers component repaints when the image loads
 */
public class AsyncImageIcon extends ImageIcon
{
	private final AsyncBufferedImage asyncImage;
	private final List<Component> componentsToRepaint = new ArrayList<>();

	public AsyncImageIcon(AsyncBufferedImage asyncImage)
	{
		super(asyncImage);
		this.asyncImage = asyncImage;

		// Register for load notification
		if (asyncImage != null)
		{
			asyncImage.onLoaded(() -> SwingUtilities.invokeLater(this::repaintComponents));
		}
	}

	/**
	 * Attach this icon to a component that should be repainted when the image loads
	 */
	public void attachToComponent(Component component)
	{
		if (component != null && !componentsToRepaint.contains(component))
		{
			componentsToRepaint.add(component);
			// If already loaded, repaint immediately
			if (asyncImage != null)
			{
				asyncImage.onLoaded(() -> SwingUtilities.invokeLater(() -> {
					component.repaint();
					// For text components, also revalidate
					if (component instanceof JTextComponent)
					{
						component.revalidate();
					}
				}));
			}
		}
	}

	private void repaintComponents()
	{
		for (Component component : componentsToRepaint)
		{
			if (component != null)
			{
				component.repaint();
				// For text components, also revalidate to ensure proper layout
				if (component instanceof JTextComponent)
				{
					component.revalidate();
				}
			}
		}
	}

	/**
	 * Create an AsyncImageIcon and automatically attach it to the component
	 */
	public static AsyncImageIcon createAndAttach(AsyncBufferedImage asyncImage, Component component)
	{
		if (asyncImage == null)
		{
			return null;
		}
		AsyncImageIcon icon = new AsyncImageIcon(asyncImage);
		icon.attachToComponent(component);
		return icon;
	}

}