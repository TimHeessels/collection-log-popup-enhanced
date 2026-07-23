package com.snakesteak.collectionlogpopupenhanced;

import com.snakesteak.collectionlogpopupenhanced.overlay.LeftPanelStat;
import com.snakesteak.collectionlogpopupenhanced.overlay.RightPanelStat;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityBasis;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("collection-log-popup-enhanced")
public interface CollectionLogPopupEnhancedConfig extends Config
{
	@ConfigSection(
		name = "Rarity",
		description = "How an item's rarity tier is determined",
		position = 0
	)
	String raritySection = "raritySection";

	@ConfigSection(
		name = "Overlay",
		description = "What the on-screen popup shows and for how long",
		position = 1
	)
	String overlaySection = "overlaySection";

	@ConfigSection(
		name = "Audio",
		description = "Sound effects played on new collection log unlocks",
		position = 2
	)
	String audioSection = "audioSection";

	@ConfigItem(
		keyName = "rarityBasis",
		name = "Rarity based on",
		description = "Which signal(s) determine an item's rarity tier: its GE/high alch value, how many players have obtained it, or both combined",
		section = raritySection
	)
	default RarityBasis rarityBasis()
	{
		return RarityBasis.COMBINATION;
	}

	@ConfigItem(
		keyName = "overlayDisplaySeconds",
		name = "Overlay display duration (seconds)",
		description = "How long each new collection log item stays on screen before advancing to the next queued item",
		section = overlaySection
	)
	default int overlayDisplaySeconds()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "leftPanelStat",
		name = "Bottom-left stat",
		description = "Preferred stat for the bottom-left of the overlay. Kill count falls back to Completion "
			+ "when there's no correlated kill for the item; every other stat is always available. For monsters "
			+ "with no official kill count, a trailing '*' marks a kill count tracked by this plugin (since it "
			+ "started watching that monster) rather than your true lifetime total",
		section = overlaySection
	)
	default LeftPanelStat leftPanelStat()
	{
		return LeftPanelStat.KILL_COUNT;
	}

	@ConfigItem(
		keyName = "rightPanelStat",
		name = "Bottom-right stat",
		description = "Preferred stat for the bottom-right of the overlay. Luck falls back to Value when there's "
			+ "no correlated kill for the item",
		section = overlaySection
	)
	default RightPanelStat rightPanelStat()
	{
		return RightPanelStat.LUCK;
	}

	@ConfigItem(
		keyName = "bulkUnlockSfx",
		name = "Bulk unlock SFX",
		description = "When multiple items unlock at once, play a single sound for the first item instead of one for each",
		section = audioSection
	)
	default boolean bulkUnlockSfx()
	{
		return false;
	}

	@ConfigItem(
		keyName = "soundEnabledCommon",
		name = "Play audio effect for common items",
		description = "Play a sound effect when a new Common-tier collection log item is detected",
		section = audioSection
	)
	default boolean soundEnabledCommon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundEnabledUncommon",
		name = "Play audio effect for uncommon items",
		description = "Play a sound effect when a new Uncommon-tier collection log item is detected",
		section = audioSection
	)
	default boolean soundEnabledUncommon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundEnabledRare",
		name = "Play audio effect for rare items",
		description = "Play a sound effect when a new Rare-tier collection log item is detected",
		section = audioSection
	)
	default boolean soundEnabledRare()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundEnabledVeryRare",
		name = "Play audio effect for very rare items",
		description = "Play a sound effect when a new Very rare-tier collection log item is detected",
		section = audioSection
	)
	default boolean soundEnabledVeryRare()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundEnabledPet",
		name = "Play audio effect for pets",
		description = "Play a sound effect when a new pet is detected",
		section = audioSection
	)
	default boolean soundEnabledPet()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "soundVolume",
		name = "Sound volume",
		description = "Volume of the collection log sound effects, 0-100",
		section = audioSection
	)
	default int soundVolume()
	{
		return 75;
	}
}
