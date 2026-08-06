package com.snakesteak.collectionlogpopupenhanced.sound;

import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays a short sound effect when a new collection log item is detected, one clip per
 * {@link RarityTier} - the same tier-cue approach other RuneLite audio plugins use, built on the
 * client's shared {@link AudioPlayer} rather than a plugin-owned audio line.
 *
 * <p>Users can override any tier's bundled clip by placing a same-named .wav in
 * {@link #SOUNDS_DIR}, mirroring the override pattern core's own Notifier uses for the
 * notification sound.
 */
@Slf4j
@Singleton
public class SoundManager
{
	private static final File SOUNDS_DIR = new File(RuneLite.RUNELITE_DIR, "collection-log-popup-enhanced/sounds");
	private static final Map<RarityTier, String> SOUND_FILENAME_BY_TIER = new EnumMap<>(RarityTier.class);

	static
	{
		SOUND_FILENAME_BY_TIER.put(RarityTier.COMMON, "common.wav");
		SOUND_FILENAME_BY_TIER.put(RarityTier.UNCOMMON, "uncommon.wav");
		SOUND_FILENAME_BY_TIER.put(RarityTier.RARE, "rare.wav");
		SOUND_FILENAME_BY_TIER.put(RarityTier.VERY_RARE, "very_rare.wav");
		SOUND_FILENAME_BY_TIER.put(RarityTier.PET, "pet.wav");
	}

	private final AudioPlayer audioPlayer;
	private final CollectionLogPopupEnhancedConfig config;

	@Inject
	public SoundManager(AudioPlayer audioPlayer, CollectionLogPopupEnhancedConfig config)
	{
		this.audioPlayer = audioPlayer;
		this.config = config;

		if (!SOUNDS_DIR.mkdirs() && !SOUNDS_DIR.isDirectory())
		{
			log.warn("Failed to create custom sounds directory {}", SOUNDS_DIR);
		}
	}

	public void play(RarityTier tier)
	{
		if (!isEnabled(tier) || config.soundVolume() <= 0)
		{
			return;
		}

		String filename = SOUND_FILENAME_BY_TIER.get(tier);

		try
		{
			File override = new File(SOUNDS_DIR, filename);
			if (override.isFile())
			{
				if (isPlayableLine(override))
				{
					audioPlayer.play(override, gain(config.soundVolume()));
					return;
				}

				log.warn("Custom sound {} has an unsupported audio format, falling back to the bundled sound. "
					+ "Use uncompressed 16-bit PCM .wav", override);
			}

			String resource = "/sounds/" + filename;
			if (SoundManager.class.getResource(resource) == null)
			{
				log.debug("No sound bundled for tier {} ({})", tier, resource);
				return;
			}

			audioPlayer.play(SoundManager.class, resource, gain(config.soundVolume()));
		}
		catch (Exception e)
		{
			log.warn("Failed to play {} sound", tier, e);
		}
	}

	// AudioPlayer opens the file's format directly on a Clip line with no conversion, so formats
	// the mixer can't open natively (e.g. 32-bit float WAVs) throw LineUnavailableException deep in
	// javax.sound.sampled instead of failing cleanly - checked upfront to avoid that.
	private static boolean isPlayableLine(File file)
	{
		try
		{
			AudioFormat format = AudioSystem.getAudioFileFormat(file).getFormat();
			return AudioSystem.isLineSupported(new DataLine.Info(Clip.class, format));
		}
		catch (Exception e)
		{
			return false;
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
