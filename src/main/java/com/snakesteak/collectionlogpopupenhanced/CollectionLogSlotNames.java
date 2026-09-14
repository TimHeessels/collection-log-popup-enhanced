package com.snakesteak.collectionlogpopupenhanced;

/**
 * Bridges the wiki dataset's item names and the collection log slot names the unlock chat message
 * uses, in both directions.
 * <p>Shared by the completion and drop-rate datasets, which are both generated from the wiki and
 * both looked up by the name out of that message. See "This Plugin: Rarity Tiers" in AGENTS.md.
 */
public final class CollectionLogSlotNames
{
	private static final String PET_SUFFIX = " (pet)";

	// The charge-state suffix the log puts on a slot whose item is stored uncharged. The dataset
	// carries it on 11 entries (Scythe of Vitur, Tumeken's shadow, ...), always *instead of* the
	// plain name rather than alongside it - so where an entry is stored plain and the game sends the
	// suffixed form, as for Eye of Ayak, an exact lookup misses. Found by reading a client log.
	private static final String UNCHARGED_SUFFIX = " (uncharged)";

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

	/**
	 * The reverse of {@link #slotNameOrNull}: a name out of the chat message reduced to the one the
	 * dataset is keyed on, for a lookup that has already missed on the message's own wording.
	 *
	 * @return the dataset name for a slot name carrying a charge-state suffix the dataset does not,
	 *         or {@code null} if it carries none
	 */
	public static String datasetNameOrNull(String slotName)
	{
		int cut = slotName.length() - UNCHARGED_SUFFIX.length();
		return cut > 0 && slotName.regionMatches(true, cut, UNCHARGED_SUFFIX, 0, UNCHARGED_SUFFIX.length())
			? slotName.substring(0, cut)
			: null;
	}
}
