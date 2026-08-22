package com.snakesteak.collectionlogpopupenhanced.rarity;

/**
 * Which signal(s) {@link RarityResolver} ranks an item's percentile against.
 * The labels are display-only; the constant names are what get persisted to config, so they must
 * not be renamed without a migration (RuneLite serializes enums by constant name, not by label).
 */
public enum RarityBasis
{
	VALUE("Value"),
	// Labelled by the signal it uses rather than "Rarity" - every basis produces a rarity tier, so
	// "Rarity" said nothing about what made this one different. Matches the "Wiki Comp%" stat label
	// the popup already shows.
	RARITY("Wiki Comp%"),
	COMBINATION("Combination");

	private final String label;

	RarityBasis(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
