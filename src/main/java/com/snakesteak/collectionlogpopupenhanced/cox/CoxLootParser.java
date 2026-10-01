package com.snakesteak.collectionlogpopupenhanced.cox;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.client.util.Text;

/**
 * Recognises CoX purples in chat and notification text. Matching is by item name rather than by
 * fixed offsets, since the raid party and clan broadcasts wrap names in colour tags that move about.
 * <p>Adapted from riktenx's cox-special-loot-hider (BSD-2-Clause).
 */
public final class CoxLootParser
{
	public static final List<String> UNIQUES = List.of(
		"Dexterous prayer scroll",
		"Arcane prayer scroll",
		"Twisted buckler",
		"Dragon hunter crossbow",
		"Dinh's bulwark",
		"Ancestral hat",
		"Ancestral robe top",
		"Ancestral robe bottom",
		"Dragon claws",
		"Elder maul",
		"Kodai insignia",
		"Twisted bow"
	);

	// Longest first, so "Ancestral robe bottom" can never be read as a shorter name it contains.
	private static final String[] UNIQUES_BY_LENGTH = UNIQUES.stream()
		.sorted(Comparator.comparingInt(String::length).reversed())
		.toArray(String[]::new);

	private static final String CENSORED = "???";

	private static final Pattern CLAN_BROADCAST = Pattern.compile(
		"^(.+?) received (?:special loot from a raid|a drop|a new collection log item): (.+)$",
		Pattern.CASE_INSENSITIVE);

	private static final String COLLECTION_LOG_GAME_MESSAGE = "New item added to your collection log:";
	private static final String VALUABLE_DROP_GAME_MESSAGE = "Valuable drop:";
	private static final String NOTIFICATION_PREFIX = "New item:";

	private CoxLootParser()
	{
	}

	public static boolean isUnique(String itemName)
	{
		return UNIQUES.contains(itemName);
	}

	/**
	 * @return the CoX purple named anywhere in {@code message}, or null if there isn't one
	 */
	public static String findUnique(String message)
	{
		if (message == null)
		{
			return null;
		}
		String stripped = Text.removeTags(message);
		return Arrays.stream(UNIQUES_BY_LENGTH).filter(stripped::contains).findFirst().orElse(null);
	}

	/**
	 * The raid party's own broadcast, "Name - Item", sent as a friends chat notification.
	 */
	public static Optional<LootBroadcast> parsePartyLoot(String message)
	{
		String item = findUnique(message);
		if (item == null)
		{
			return Optional.empty();
		}

		String stripped = Text.removeTags(message).trim();
		int index = stripped.indexOf(" - " + item);
		if (index <= 0)
		{
			return Optional.empty();
		}
		return Optional.of(new LootBroadcast(stripped.substring(0, index).trim(), item));
	}

	/**
	 * Clan broadcasts: special loot, valuable drop and new collection log item.
	 */
	public static Optional<LootBroadcast> parseClanBroadcast(String message)
	{
		Matcher matcher = CLAN_BROADCAST.matcher(Text.removeTags(message).trim());
		if (!matcher.matches())
		{
			return Optional.empty();
		}

		String item = findUnique(matcher.group(2));
		return item == null ? Optional.empty() : Optional.of(new LootBroadcast(matcher.group(1).trim(), item));
	}

	/**
	 * The local player's own game messages that name the item - the collection log line and the
	 * valuable drop line.
	 */
	public static boolean isOwnLootGameMessage(String message)
	{
		String stripped = Text.removeTags(message);
		return stripped.contains(COLLECTION_LOG_GAME_MESSAGE) || stripped.contains(VALUABLE_DROP_GAME_MESSAGE);
	}

	/**
	 * Replaces the item in a "Name - Item" party broadcast.
	 */
	public static String censorItem(String message, String item)
	{
		return message.replace(item, CENSORED);
	}

	/**
	 * Replaces everything after the first colon - the item and, where there is one, its coin value,
	 * which would give the item away just as well. A trailing colour close tag is kept so the line
	 * still renders in its original colour.
	 */
	public static String censorAfterColon(String message)
	{
		int colon = message.indexOf(':');
		if (colon < 0)
		{
			return message;
		}
		String suffix = message.endsWith("</col>") ? "</col>" : "";
		return message.substring(0, colon + 1) + " " + CENSORED + suffix;
	}

	/**
	 * @return the item named in the native popup's main text ("New item:" followed by the name),
	 *         or null if it isn't a CoX purple
	 */
	public static String notificationUnique(String notificationMainText)
	{
		if (notificationMainText == null)
		{
			return null;
		}
		String stripped = Text.removeTags(notificationMainText).trim();
		if (stripped.regionMatches(true, 0, NOTIFICATION_PREFIX, 0, NOTIFICATION_PREFIX.length()))
		{
			stripped = stripped.substring(NOTIFICATION_PREFIX.length()).trim();
		}
		return findUnique(stripped);
	}

	@Value
	public static class LootBroadcast
	{
		String player;
		String item;
	}
}
