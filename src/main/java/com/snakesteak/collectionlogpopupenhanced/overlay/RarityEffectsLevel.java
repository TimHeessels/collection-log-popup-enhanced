package com.snakesteak.collectionlogpopupenhanced.overlay;

import lombok.Getter;

/**
 * How much of {@link RarityEffects} to draw. The constant names are what get persisted to config, so
 * they must not be renamed without a migration.
 */
public enum RarityEffectsLevel
{
	OFF("Off", 0f),
	SUBTLE("Subtle", 0.75f),
	FULL("Full", 1f);

	private final String label;
	// Scales every effect's opacity, on top of the per-tier profile.
	@Getter
	private final float intensity;

	RarityEffectsLevel(String label, float intensity)
	{
		this.label = label;
		this.intensity = intensity;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
