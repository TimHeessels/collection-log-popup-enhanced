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
		name = "Preview",
		description = "Preview the popup without waiting for an unlock",
		position = 0
	)
	String previewSection = "previewSection";

	@ConfigSection(
		name = "Rarity",
		description = "How an item's rarity tier is determined",
		position = 1
	)
	String raritySection = "raritySection";

	@ConfigSection(
		name = "Statistics",
		description = "Statistics in bottom left and right of popup",
		position = 2
	)
	String overlaySection = "overlaySection";

	@ConfigSection(
		name = "Audio",
		description = "Sound effects played on new collection log unlocks",
		position = 3
	)
	String audioSection = "audioSection";

	@ConfigSection(
		name = "Appearance",
		description = "Tune to your liking",
		position = 4
	)
	String appearanceSection = "appearanceSection";

	@ConfigSection(
		name = "Colours",
		description = "Border and background colour of the popup, per rarity tier",
		position = 5
	)
	String coloursSection = "coloursSection";

	@ConfigSection(
		name = "Miscellaneous",
		description = "Small tweaks",
		position = 6
	)
	String tweaksSection = "tweaksSection";

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
		keyName = "rarityBasis",
		name = "Rarity based on",
		description = "What the rarity tiers are based on",
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
		position = 0,
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
		position = 1,
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
		position = 2,
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
		position = 0,
		section = audioSection
	)
	default boolean bulkUnlockSfx()
	{
		return false;
	}

	@ConfigItem(
		keyName = "soundEnabledCommon",
		name = "Play audio for common unlocks",
		description = "Play a sound effect on common collection log slots",
		position = 1,
		section = audioSection
	)
	default boolean soundEnabledCommon()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "soundVolumeCommon",
		name = "Common volume",
		description = "Volume of the common-tier sound effect, 0-100",
		position = 2,
		section = audioSection
	)
	default int soundVolumeCommon()
	{
		return 65;
	}

	@ConfigItem(
		keyName = "soundEnabledUncommon",
		name = "Play audio for uncommon unlocks",
		description = "Play a sound effect on uncommon collection log slots",
		position = 3,
		section = audioSection
	)
	default boolean soundEnabledUncommon()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "soundVolumeUncommon",
		name = "Uncommon volume",
		description = "Volume of the uncommon-tier sound effect, 0-100",
		position = 4,
		section = audioSection
	)
	default int soundVolumeUncommon()
	{
		return 65;
	}

	@ConfigItem(
		keyName = "soundEnabledRare",
		name = "Play audio for rare unlocks",
		description = "Play a sound effect on rare collection log slots",
		position = 5,
		section = audioSection
	)
	default boolean soundEnabledRare()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "soundVolumeRare",
		name = "Rare volume",
		description = "Volume of the rare-tier sound effect, 0-100",
		position = 6,
		section = audioSection
	)
	default int soundVolumeRare()
	{
		return 65;
	}

	@ConfigItem(
		keyName = "soundEnabledVeryRare",
		name = "Play audio for very rare unlocks",
		description = "Play a sound effect on very rare collection log slots",
		position = 7,
		section = audioSection
	)
	default boolean soundEnabledVeryRare()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "soundVolumeVeryRare",
		name = "Very rare volume",
		description = "Volume of the very rare-tier sound effect, 0-100",
		position = 8,
		section = audioSection
	)
	default int soundVolumeVeryRare()
	{
		return 65;
	}

	@ConfigItem(
		keyName = "soundEnabledPet",
		name = "Play audio for pets",
		description = "Play a sound effect when a new pet is unlocked",
		position = 9,
		section = audioSection
	)
	default boolean soundEnabledPet()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "soundVolumePet",
		name = "Pet volume",
		description = "Volume of the pet sound effect, 0-100.",
		position = 10,
		section = audioSection
	)
	default int soundVolumePet()
	{
		return 65;
	}

	@ConfigItem(
		keyName = "showProgressBar",
		name = "Show progress bar",
		description = "Show your collection log progress in the center bar",
		position = 0,
		section = appearanceSection
	)
	default boolean showProgressBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayDisplaySeconds",
		name = "Overlay display duration (seconds)",
		description = "How long each new collection log item stays on screen",
		position = 1,
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
		position = 2,
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
		position = 3,
		section = appearanceSection
	)
	default TextRenderMode textRenderMode()
	{
		return TextRenderMode.CRISP;
	}

	@ConfigItem(
		keyName = "colourCommonTier",
		name = "Common",
		description = "Panel colour for common tier",
		position = 0,
		section = coloursSection
	)
	default Color colourCommonTier()
	{
		return new Color(0xFFFFFF);
	}

	@ConfigItem(
		keyName = "colourUncommonTier",
		name = "Uncommon",
		description = "Panel colour for uncommon tier",
		position = 1,
		section = coloursSection
	)
	default Color colourUncommonTier()
	{
		return new Color(0x1888C9);
	}

	@ConfigItem(
		keyName = "colourRareTier",
		name = "Rare",
		description = "Panel colour for rare tier",
		position = 2,
		section = coloursSection
	)
	default Color colourRareTier()
	{
		return new Color(0x8431A6);
	}

	@ConfigItem(
		keyName = "colourVeryRareTier",
		name = "Very rare",
		description = "Panel colour for very rare tier",
		position = 3,
		section = coloursSection
	)
	default Color colourVeryRareTier()
	{
		return new Color(0xB19F3B);
	}

	@ConfigItem(
		keyName = "colourPetTier",
		name = "Pet",
		description = "Panel colour for pets",
		position = 4,
		section = coloursSection
	)
	default Color colourPetTier()
	{
		return new Color(0xDC2367);
	}

	@Range(min = 10, max = 60)
	@ConfigItem(
		keyName = "backgroundDarkness",
		name = "Background darkness (%)",
		description = "How dark the panel background is relative to its tier colour",
		position = 5,
		section = coloursSection
	)
	default int backgroundDarkness()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "colourCaption",
		name = "Caption text",
		description = "Colour of the collection log slot caption",
		position = 6,
		section = coloursSection
	)
	default Color colourCaption()
	{
		return new Color(0xFF981F);
	}

	@ConfigItem(
		keyName = "colourStatLabel",
		name = "Statistic labels",
		description = "Colour of the bottom-corner statistic labels",
		position = 7,
		section = coloursSection
	)
	default Color colourStatLabel()
	{
		return new Color(0xDCDCD6);
	}

	@ConfigItem(
		keyName = "colourStatValue",
		name = "Statistic values",
		description = "Colour of the bottom-corner statistic values",
		position = 8,
		section = coloursSection
	)
	default Color colourStatValue()
	{
		return new Color(0xFFCD2D);
	}

	@ConfigItem(
    keyName = "delayCoxPopupUntilChest",
    name = "Delay CoX popups until chest",
    description = "Use in combination with a CoX censor plugin.",
    warning = "Please note that using this might break automatic collection log screenshotting for clogs from the CoX chest.",
    position = 0,
    section = tweaksSection
)
	default boolean delayCoxPopupUntilChest()
	{
		return false;
	}
	
}
