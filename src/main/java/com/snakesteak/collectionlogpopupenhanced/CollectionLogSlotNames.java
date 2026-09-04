package com.snakesteak.collectionlogpopupenhanced;

/**
 * Maps a wiki dataset item name to the collection log slot name the unlock chat message uses.
 * <p>Shared by the completion and drop-rate datasets, which are both generated from the wiki and
 * both looked up by the name out of that message. See "This Plugin: Rarity Tiers" in AGENTS.md.
 */
public final class CollectionLogSlotNames
{
	private static final String PET_SUFFIX = " (pet)";

	private CollectionLogSlotNames()
	{
	}

	/**
	 * @return the slot name for a dataset item name carrying the wiki's " (pet)" disambiguator, or
	 *         {@code null} if it carries none. Gull is the only such entry - the log's slot, and so
	 *         the chat message, reads plain "Gull".
	 */
	public static String slotNameOrNull(String itemName)
	{
		int cut = itemName.length() - PET_SUFFIX.length();
		return cut > 0 && itemName.regionMatches(true, cut, PET_SUFFIX, 0, PET_SUFFIX.length())
			? itemName.substring(0, cut)
			: null;
	}
}
