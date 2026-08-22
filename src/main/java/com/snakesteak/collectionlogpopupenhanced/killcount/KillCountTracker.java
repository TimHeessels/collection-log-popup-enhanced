package com.snakesteak.collectionlogpopupenhanced.killcount;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Tracks the most recently seen boss kill count (or chest opening count) from the "Your X ...
 * count is: N." chat message, so
 * {@link com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedPlugin} can attach it
 * to a collection log item that unlocked from the same kill/opening. The count message and the
 * collection log message are separate and, for some bosses (e.g. ones looted by searching the
 * corpse), can be arbitrarily far apart in time - so correlation is done by matching the stored
 * boss name against the item's known source(s), not by proximity.
 * The chat-parsed name doesn't always match the collection log's source name verbatim: some tabs
 * carry a leading "The " that the kill count message omits (handled by stripping it in
 * {@link #normalize}), and others name an individual monster or chest that the collection log
 * groups under a different, shared name (e.g. "Dagannoth Rex" vs the "Dagannoth Kings" tab) -
 * handled via {@link #BOSS_ALIASES}. Add new cases there as they're found.
 */
@Singleton
public class KillCountTracker
{
	private static final Pattern KILL_COUNT_PATTERN =
		Pattern.compile("Your (?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? (?:kill )?count is: ?(?:<col=[0-9a-f]{6}>)?(?<kc>[0-9,]+)");
	private static final String ARTICLE_PREFIX = "the ";

	// Chat message name -> collection log tab name, for the cases where they genuinely diverge
	// beyond just the leading article (see the class javadoc).
	private static final Map<String, String> BOSS_ALIASES = Map.ofEntries(
		Map.entry("Dagannoth Rex", "Dagannoth Kings"),
		Map.entry("Dagannoth Prime", "Dagannoth Kings"),
		Map.entry("Dagannoth Supreme", "Dagannoth Kings"),
		Map.entry("Callisto", "Callisto and Artio"),
		Map.entry("Artio", "Callisto and Artio"),
		Map.entry("Venenatis", "Venenatis and Spindel"),
		Map.entry("Spindel", "Venenatis and Spindel"),
		Map.entry("Vet'ion", "Vet'ion and Calvar'ion"),
		Map.entry("Calvar'ion", "Vet'ion and Calvar'ion"),
		Map.entry("Branda the Fire Queen", "Royal Titans"),
		Map.entry("Eldric the Ice King", "Royal Titans"),
		Map.entry("Barrows chest", "Barrows Chests"),
		Map.entry("Lunar Chest", "Moons of Peril")
	);

	private String lastBoss;
	private int lastKillCount;

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		Matcher matcher = KILL_COUNT_PATTERN.matcher(chatMessage.getMessage());
		if (!matcher.find())
		{
			return;
		}

		lastBoss = Text.removeTags(matcher.group("boss"));
		lastKillCount = Integer.parseInt(matcher.group("kc").replace(",", ""));
	}

	/**
	 * @param candidateSources the collection log source(s) (boss/activity names) that the item being
	 *                          resolved is known to come from
	 * @return the most recently seen kill count, if its boss name matches one of {@code
	 *         candidateSources} (case-insensitive, alias- and article-aware - see the class javadoc)
	 *         - null if nothing has been seen yet, or the most recent kill count belongs to an
	 *         unrelated source.
	 */
	public RecentKill killCountFor(Collection<String> candidateSources)
	{
		if (lastBoss == null)
		{
			return null;
		}
		String normalizedBoss = normalize(lastBoss);
		String normalizedAlias = normalize(BOSS_ALIASES.getOrDefault(lastBoss, lastBoss));
		boolean matches = candidateSources.stream()
			.map(KillCountTracker::normalize)
			.anyMatch(source -> source.equals(normalizedBoss) || source.equals(normalizedAlias));
		if (!matches)
		{
			return null;
		}
		return new RecentKill(lastBoss, lastKillCount);
	}

	/**
	 * @return {@code name} lowercased with a leading "The " stripped, if present - so e.g. "The Mad
	 *         Angel" and "Mad Angel" compare equal. Only the definite article is stripped: no dataset
	 *         tab name starts with "A "/"An ", so handling those would just be unused generality.
	 */
	private static String normalize(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.startsWith(ARTICLE_PREFIX) ? lower.substring(ARTICLE_PREFIX.length()) : lower;
	}

	@Value
	public static class RecentKill
	{
		String source;
		int killCount;
	}
}
