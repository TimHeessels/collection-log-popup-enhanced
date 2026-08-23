package com.snakesteak.collectionlogpopupenhanced;

import com.snakesteak.collectionlogpopupenhanced.overlay.LeftPanelStat;
import com.snakesteak.collectionlogpopupenhanced.overlay.RightPanelStat;
import com.snakesteak.collectionlogpopupenhanced.overlay.TextRenderMode;
import com.snakesteak.collectionlogpopupenhanced.overlay.ValueDisplayMode;
import com.snakesteak.collectionlogpopupenhanced.rarity.PreviewTier;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityBasis;
import java.awt.Color;
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
		name = "Statistics",
		description = "Statistics in bottom left and right of popup",
		position = 1
	)
	String overlaySection = "overlaySection";

	@ConfigSection(
		name = "Audio",
		description = "Sound effects played on new collection log unlocks",
		position = 2
	)
	String audioSection = "audioSection";

	@ConfigSection(
		name = "Appearance",
		description = "Tune to your liking",
		position = 3
	)
	String appearanceSection = "appearanceSection";

	@ConfigSection(
		name = "Colours",
		description = "Border and background colour of the popup, per rarity tier",
		position = 4
	)
	String coloursSection = "coloursSection";

	@ConfigSection(
		name = "Preview",
		description = "Preview the popup without waiting for an unlock",
		position = 5
	)
	String previewSection = "previewSection";

	@ConfigItem(
		keyName = "rarityBasis",
		name = "Rarity based on",
		description = "Whether an item's tier comes from its value, its wiki completion percentage, or a blend of both",
		position = 0,
		section = raritySection
	)
	default RarityBasis rarityBasis()
	{
		return RarityBasis.COMBINATION;
	}

	@ConfigItem(
		keyName = "valueUncommonThreshold",
		name = "Uncommon at (gp)",
		description = "Value threshold for uncommon tier",
		position = 1,
		section = raritySection
	)
	default int valueUncommonThreshold()
	{
		return 100_000;
	}

	@ConfigItem(
		keyName = "valueRareThreshold",
		name = "Rare at (gp)",
		description = "Value threshold for rare tier",
		position = 2,
		section = raritySection
	)
	default int valueRareThreshold()
	{
		return 1_000_000;
	}

	@ConfigItem(
		keyName = "valueVeryRareThreshold",
		name = "Very rare at (gp)",
		description = "Value threshold for very rare tier",
		position = 3,
		section = raritySection
	)
	default int valueVeryRareThreshold()
	{
		return 10_000_000;
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
		return 65;
	}

	@ConfigItem(
		keyName = "overlayDisplaySeconds",
		name = "Overlay display duration (seconds)",
		description = "How long each new collection log item stays on screen",
		section = appearanceSection
	)
	default int overlayDisplaySeconds()
	{
		return 6;
	}

	@Range(min = 50, max = 200)
	@ConfigItem(
		keyName = "overlayScalePercent",
		name = "Popup scale (%)",
		description = "Size of the popup",
		section = appearanceSection
	)
	default int overlayScalePercent()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "textRenderMode",
		name = "Text rendering",
		description = "How popup text is rendered",
		section = appearanceSection
	)
	default TextRenderMode textRenderMode()
	{
		return TextRenderMode.CRISP;
	}

	@ConfigItem(
		keyName = "previewTier",
		name = "Preview popup",
		description = "Shows a random item popup of the selected tier",
		position = 0,
		section = previewSection
	)
	default PreviewTier previewTier()
	{
		return PreviewTier.NONE;
	}

	@ConfigItem(
		keyName = "colourCommonTier",
		position = 0,
		name = "Common",
		description = "Panel color for common tier",
		section = coloursSection
	)
	default Color colourCommonTier()
	{
		return new Color(0xFFFFFF);
	}

	@ConfigItem(
		keyName = "colourUncommonTier",
		position = 1,
		name = "Uncommon",
		description = "Panel color for uncommon tier",
		section = coloursSection
	)
	default Color colourUncommonTier()
	{
		return new Color(0x1888C9);
	}

	@ConfigItem(
		keyName = "colourRareTier",
		position = 2,
		name = "Rare",
		description = "Panel color for rare tier.",
		section = coloursSection
	)
	default Color colourRareTier()
	{
		return new Color(0x8431A6);
	}

	@ConfigItem(
		keyName = "colourVeryRareTier",
		position = 3,
		name = "Very rare",
		description = "Panel color for very rare tier",
		section = coloursSection
	)
	default Color colourVeryRareTier()
	{
		return new Color(0xB19F3B);
	}

	@ConfigItem(
		keyName = "colourPetTier",
		position = 4,
		name = "Pet",
		description = "Panel color for pets.",
		section = coloursSection
	)
	default Color colourPetTier()
	{
		return new Color(0xDC2367);
	}

	@Range(min = 10, max = 60)
	@ConfigItem(
		keyName = "backgroundDarkness",
		position = 5,
		name = "Background darkness (%)",
		description = "How dark the panel background is relative to its tier colour. Lower is darker",
		section = coloursSection
	)
	default int backgroundDarkness()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "colourCaption",
		position = 6,
		name = "Caption text",
		description = "Colour of the Collection log slot caption",
		section = coloursSection
	)
	default Color colourCaption()
	{
		return new Color(0xFF981F);
	}

	@ConfigItem(
		keyName = "colourStatLabel",
		position = 7,
		name = "Statistic labels",
		description = "Colour of the bottom-corner statistic labels.",
		section = coloursSection
	)
	default Color colourStatLabel()
	{
		return new Color(0xDCDCD6);
	}

	@ConfigItem(
		keyName = "colourStatValue",
		position = 8,
		name = "Statistic values",
		description = "Colour of the bottom-corner statistic values.",
		section = coloursSection
	)
	default Color colourStatValue()
	{
		return new Color(0xFFCD2D);
	}
}
