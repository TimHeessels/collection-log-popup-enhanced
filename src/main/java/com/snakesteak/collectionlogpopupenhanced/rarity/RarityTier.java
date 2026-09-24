package com.snakesteak.collectionlogpopupenhanced.rarity;

public enum RarityTier
{
	COMMON("Common"),
	UNCOMMON("Uncommon"),
	RARE("Rare"),
	VERY_RARE("Very Rare"),
	PET("Pet");

	private final String label;

	RarityTier(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}
}
