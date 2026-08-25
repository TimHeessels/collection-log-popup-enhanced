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
 * <p>Some messages also wrap the boss name in extra words the collection log doesn't track
 * separately - a qualifier before it ("Your subdued Wintertodt count is:"), a modifier after it
 * ("Your Yama success count is:", since Yama's kills are tracked as a "success count"), or a raid
 * difficulty suffix ("Your completed Tombs of Amascut: Expert Mode count is:"). Rather than listing
 * every such word, {@link #matchesSource} treats the source name as a match if it appears anywhere
 * in the boss text as a whole word/phrase.
 * <p>Doom of Mokhaiotl is a special case beyond even the above: only "deep delves" (delve 8+) are
 * counted at all - see {@link #DEEP_DELVE_PATTERN}. Every message below that (delve progress,
 * duration, personal best) carries no parseable count, so kill count is genuinely unavailable
 * before delve 8, same as the game's own HiScores - this is a real limitation, not something the
 * plugin can work around.
 */
@Singleton
public class KillCountTracker
{
	private static final Pattern KILL_COUNT_PATTERN =
		Pattern.compile("Your (?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? (?:kill )?count is: ?(?:<col=[0-9a-f]{6}>)?(?<kc>[0-9,]+)");
	private static final Pattern DEEP_DELVE_PATTERN =
		Pattern.compile("Deep delves completed: ?(?:<col=[0-9a-f]{6}>)?(?<kc>[0-9,]+)");
	private static final String ARTICLE_PREFIX = "the ";

	// Doom of Mokhaiotl's deep delve message never names the boss - there's only one thing in the
	// game it could refer to, so the source is hardcoded rather than parsed.
	private static final String DOOM_OF_MOKHAIOTL = "Doom of Mokhaiotl";

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
		Map.entry("Lunar Chest", "Moons of Peril"),
		Map.entry("TzTok-Jad", "The Fight Caves"),
		Map.entry("TzKal-Zuk", "The Inferno")
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
		if (matcher.find())
		{
			lastBoss = Text.removeTags(matcher.group("boss"));
			lastKillCount = Integer.parseInt(matcher.group("kc").replace(",", ""));
			return;
		}

		Matcher deepDelveMatcher = DEEP_DELVE_PATTERN.matcher(chatMessage.getMessage());
		if (deepDelveMatcher.find())
		{
			lastBoss = DOOM_OF_MOKHAIOTL;
			lastKillCount = Integer.parseInt(deepDelveMatcher.group("kc").replace(",", ""));
		}
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
			.anyMatch(source -> matchesSource(normalizedBoss, source) || matchesSource(normalizedAlias, source));
		if (!matches)
		{
			return null;
		}
		return new RecentKill(lastBoss, lastKillCount);
	}

	/**
	 * @return true if {@code boss} is exactly {@code source}, or {@code source} appears as a whole
	 *         word/phrase somewhere inside {@code boss} - covers messages that wrap the source name
	 *         in extra words the collection log doesn't track separately: a qualifier before it
	 *         ("subdued wintertodt", "completed chambers of xeric"), a modifier after it ("yama
	 *         success"), or a raid difficulty suffix ("chambers of xeric challenge mode", "tombs of
	 *         amascut: expert mode"). This is checked in addition to - not instead of - {@link
	 *         #BOSS_ALIASES}, which still handles names that are genuinely different words (e.g.
	 *         "tztok-jad" vs "the fight caves") rather than the same name plus filler.
	 */
	private static boolean matchesSource(String boss, String source)
	{
		if (boss.equals(source))
		{
			return true;
		}
		return Pattern.compile("(?<![a-z'])" + Pattern.quote(source) + "(?![a-z'])")
			.matcher(boss)
			.find();
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
