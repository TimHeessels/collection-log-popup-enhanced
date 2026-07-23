package com.snakesteak.collectionlogpopupenhanced.sound;

import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays a short sound effect when a new collection log item is detected, one clip per
 * {@link RarityTier} - the same tier-cue approach other RuneLite audio plugins use, built on the
 * client's shared {@link AudioPlayer} rather than a plugin-owned audio line.
 */
@Slf4j
@Singleton
public class SoundManager
{
	private static final Map<RarityTier, String> SOUND_RESOURCE_BY_TIER = new EnumMap<>(RarityTier.class);

	static
	{
		SOUND_RESOURCE_BY_TIER.put(RarityTier.COMMON, "/sounds/common.wav");
		SOUND_RESOURCE_BY_TIER.put(RarityTier.UNCOMMON, "/sounds/uncommon.wav");
		SOUND_RESOURCE_BY_TIER.put(RarityTier.RARE, "/sounds/rare.wav");
		SOUND_RESOURCE_BY_TIER.put(RarityTier.VERY_RARE, "/sounds/very_rare.wav");
		SOUND_RESOURCE_BY_TIER.put(RarityTier.PET, "/sounds/pet.wav");
	}

	private final AudioPlayer audioPlayer;
	private final CollectionLogPopupEnhancedConfig config;

	@Inject
	public SoundManager(AudioPlayer audioPlayer, CollectionLogPopupEnhancedConfig config)
	{
		this.audioPlayer = audioPlayer;
		this.config = config;
	}

	public void play(RarityTier tier)
	{
		if (!isEnabled(tier) || config.soundVolume() <= 0)
		{
			return;
		}

		String resource = SOUND_RESOURCE_BY_TIER.get(tier);
		if (SoundManager.class.getResource(resource) == null)
		{
			log.debug("No sound bundled for tier {} ({})", tier, resource);
			return;
		}

		try
		{
			audioPlayer.play(SoundManager.class, resource, gain(config.soundVolume()));
		}
		catch (IOException | UnsupportedAudioFileException | LineUnavailableException e)
		{
			log.warn("Failed to play {} sound", tier, e);
		}
	}

	// AudioPlayer's gain is a dB attenuation from the clip's mastered volume (0 dB = unchanged),
	// not a 0-1 linear multiplier, so the linear 0-100 config slider is log-scaled to match.
	private static float gain(int volumePercent)
	{
		return 20f * (float) Math.log10(Math.min(100, volumePercent) / 100f);
	}

	private boolean isEnabled(RarityTier tier)
	{
		switch (tier)
		{
			case COMMON:
				return config.soundEnabledCommon();
			case UNCOMMON:
				return config.soundEnabledUncommon();
			case RARE:
				return config.soundEnabledRare();
			case VERY_RARE:
				return config.soundEnabledVeryRare();
			case PET:
				return config.soundEnabledPet();
			default:
				return false;
		}
	}
}
