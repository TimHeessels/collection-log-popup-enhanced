package com.snakesteak.collectionlogpopupenhanced.cox;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.ImageCapture;

/**
 * Takes the collection log screenshot for a CoX purple once its popup shows at the chest, standing in
 * for the Screenshot plugin's own, which is skipped at raid end (see the plugin's native popup
 * handling). Same folder, file name and options as the Screenshot plugin, and only when that plugin
 * would have taken one itself.
 * <p>Adapted from riktenx's cox-special-loot-hider (BSD-2-Clause).
 */
@Singleton
public class CoxScreenshot
{
	private static final String SCREENSHOT_GROUP = "screenshot";
	private static final String SUB_DIRECTORY = "Collection Log";

	private final ImageCapture imageCapture;
	private final ConfigManager configManager;

	@Inject
	CoxScreenshot(ImageCapture imageCapture, ConfigManager configManager)
	{
		this.imageCapture = imageCapture;
		this.configManager = configManager;
	}

	/**
	 * @return true if the Screenshot plugin is on and set to capture collection log entries
	 */
	public boolean isEnabled()
	{
		return bool("runelite", "screenshotplugin", true) && bool(SCREENSHOT_GROUP, "collectionLogEntries", true);
	}

	public void capture(String itemName)
	{
		if (!isEnabled())
		{
			return;
		}

		imageCapture.takeScreenshot(SUB_DIRECTORY, "Collection log (" + itemName + ")",
			bool(SCREENSHOT_GROUP, "includeFrame", true),
			bool(SCREENSHOT_GROUP, "notifyWhenTaken", true),
			bool(SCREENSHOT_GROUP, "copyToClipboard", false));
	}

	private boolean bool(String group, String key, boolean defaultValue)
	{
		Boolean value = configManager.getConfiguration(group, key, Boolean.class);
		return value != null ? value : defaultValue;
	}
}
