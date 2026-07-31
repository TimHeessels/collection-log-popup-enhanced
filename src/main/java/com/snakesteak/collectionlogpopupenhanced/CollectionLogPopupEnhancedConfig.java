package com.snakesteak.collectionlogpopupenhanced;

import com.snakesteak.collectionlogpopupenhanced.overlay.LeftPanelStat;
import com.snakesteak.collectionlogpopupenhanced.overlay.RightPanelStat;
import com.snakesteak.collectionlogpopupenhanced.overlay.ValueDisplayMode;
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
		description = "Rarity determined by cost, wiki comp%, or a calculcation of both",
		section = raritySection
	)
	default RarityBasis rarityBasis()
	{
		return RarityBasis.COMBINATION;
	}

	@ConfigItem(
		keyName = "overlayDisplaySeconds",
		name = "Overlay display duration (seconds)",
		description = "How long each new collection log item stays on screen",
		section = overlaySection
	)
	default int overlayDisplaySeconds()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "leftPanelStat",
		name = "Left statistic",
		description = "Which statistic to show on the left side of the panel",
		section = overlaySection
	)
	default LeftPanelStat leftPanelStat()
	{
		return LeftPanelStat.KILL_COUNT;
	}

	@ConfigItem(
		keyName = "valueDisplayMode",
		name = "Value shown as",
		description = "Prefer to display value statistic as high alch or G.E.",
		section = overlaySection
	)
	default ValueDisplayMode valueDisplayMode()
	{
		return ValueDisplayMode.GE_VALUE;
	}

	@ConfigItem(
		keyName = "rightPanelStat",
		name = "Right statistic",
		description = "Which statistic to show on the right side of the panel",
		section = overlaySection
	)
	default RightPanelStat rightPanelStat()
	{
		return RightPanelStat.DROP_RATE;
	}

	@ConfigItem(
		keyName = "bulkUnlockSfx",
		name = "Bulk unlock SFX",
		description = "When multiple items unlock at once, play a single sound instead",
		section = audioSection
	)
	default boolean bulkUnlockSfx()
	{
		return false;
	}

	@ConfigItem(
		keyName = "soundEnabledCommon",
		name = "Play audio effect for common items",
		description = "Play a sound effect when a new Common-tier collection log item is unlocked",
		section = audioSection
	)
	default boolean soundEnabledCommon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundEnabledUncommon",
		name = "Play audio effect for uncommon items",
		description = "Play a sound effect when a new Uncommon-tier collection log item is unlocked",
		section = audioSection
	)
	default boolean soundEnabledUncommon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundEnabledRare",
		name = "Play audio effect for rare items",
		description = "Play a sound effect when a new Rare-tier collection log item is unlocked",
		section = audioSection
	)
	default boolean soundEnabledRare()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundEnabledVeryRare",
		name = "Play audio effect for very rare items",
		description = "Play a sound effect when a new Very rare-tier collection log item is unlocked",
		section = audioSection
	)
	default boolean soundEnabledVeryRare()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundEnabledPet",
		name = "Play audio effect for pets",
		description = "Play a sound effect when a new pet is unlocked",
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
