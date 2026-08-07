package com.snakesteak.collectionlogpopupenhanced.rarity;

/**
 * Which tier of item (if any) the "Preview popup" config option should continuously show.
 */
public enum PreviewTier
{
	NONE("Off"),
	RANDOM("Random"),
	COMMON("Common"),
	UNCOMMON("Uncommon"),
	RARE("Rare"),
	VERY_RARE("Very rare"),
	PET("Pet");

	private final String label;

	PreviewTier(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
