package com.snakesteak.collectionlogpopupenhanced.rarity;

/**
 * Which signal(s) {@link RarityResolver} ranks an item's percentile against.
 */
public enum RarityBasis
{
	VALUE("Value"),
	RARITY("Rarity"),
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
