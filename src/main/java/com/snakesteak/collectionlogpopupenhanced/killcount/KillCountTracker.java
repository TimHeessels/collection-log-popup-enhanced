package com.snakesteak.collectionlogpopupenhanced.killcount;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Stores the kill count from the most recent "Your X count is: N" chat message, for the plugin to
 * attach to an unlock from the same kill.
 * <p>See "This Plugin: Kill Count Correlation" in AGENTS.md for why correlation is by name rather
 * than by time, and for the chat-wording quirks the patterns below handle.
 */
@Singleton
public class KillCountTracker
{
	// "count " is left outside the post group on purpose, so that group holds only the verb.
	private static final Pattern KILL_COUNT_PATTERN = Pattern.compile(
		"Your (?<pre>completion count for |subdued |completed )?"
			+ "(?<boss>.+?) "
			+ "(?<post>(?:kill|harvest|completion|success|Total Ticket) )?(?:count )?"
			+ "is: ?(?<kc>[0-9,]+)",
		Pattern.CASE_INSENSITIVE);

	// Activities that don't use the "count is:" form. Their source is never named in the message, so
	// it's hardcoded below - each string must match the collection log tab name exactly.
	private static final Pattern DEEP_DELVE_PATTERN =
		Pattern.compile("Deep delves completed: ?(?<kc>[0-9,]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern RIFTS_CLOSED_PATTERN =
		Pattern.compile("Amount of Rifts you have closed: ?(?<kc>[0-9,]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern SEPULCHRE_FLOOR_PATTERN =
		Pattern.compile("completed Floor [0-9] of the Hallowed Sepulchre! Total completions: ?(?<kc>[0-9,]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern SEPULCHRE_COFFIN_PATTERN =
		Pattern.compile("opened the Grand Hallowed Coffin ?(?<kc>[0-9,]+)", Pattern.CASE_INSENSITIVE);
	// The trailing "rumours for the Hunter Guild" is what keeps this off other "You have completed
	// N ..." messages, such as the clue scroll tally.
	private static final Pattern HUNTER_RUMOUR_PATTERN = Pattern.compile(
		"completed (?<kc>[0-9,]+) rumours? for the Hunter Guild",
		Pattern.CASE_INSENSITIVE);

	private static final String ARTICLE_PREFIX = "the ";

	private static final String DOOM_OF_MOKHAIOTL = "Doom of Mokhaiotl";
	private static final String GUARDIANS_OF_THE_RIFT = "Guardians of the Rift";
	private static final String HALLOWED_SEPULCHRE = "Hallowed Sepulchre";
	private static final String HUNTER_GUILD = "Hunter Guild";

	// The Sepulchre appears twice on purpose: floors and coffins are counted separately but share a
	// tab, so whichever message arrived last is the one that gets attached.
	private static final List<FixedSource> FIXED_SOURCES = List.of(
		new FixedSource(DEEP_DELVE_PATTERN, DOOM_OF_MOKHAIOTL, KillCountKind.DEEP_DELVES),
		new FixedSource(RIFTS_CLOSED_PATTERN, GUARDIANS_OF_THE_RIFT, KillCountKind.RIFTS),
		new FixedSource(SEPULCHRE_FLOOR_PATTERN, HALLOWED_SEPULCHRE, KillCountKind.FLOORS),
		new FixedSource(SEPULCHRE_COFFIN_PATTERN, HALLOWED_SEPULCHRE, KillCountKind.COFFINS),
		new FixedSource(HUNTER_RUMOUR_PATTERN, HUNTER_GUILD, KillCountKind.RUMOURS)
	);

	// Chat message name -> collection log tab name, where the two are genuinely different words.
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

	// Whole-word match, so a boss merely containing "chest" doesn't qualify as a chest opening.
	private static final Pattern CHEST_SUFFIX_PATTERN =
		Pattern.compile("(?<![a-z])chests?$", Pattern.CASE_INSENSITIVE);

	// Both forms the game emits: the <col=...> tag and the @mes_hl_red@ macro. The two render
	// identically in the chatbox, so a pattern written against one silently never matches the other
	// - found by reading raw messages in a client log. See AGENTS.md.
	private static final Pattern COLOUR_MARKUP_PATTERN = Pattern.compile("<[^<>]*>|@[a-zA-Z0-9_]+@");

	private String lastBoss;
	private int lastKillCount;
	private KillCountKind lastKind;

	// HOPPING is excluded on purpose - a hop keeps the same character, and deferred loot legitimately
	// survives one. See AGENTS.md.
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState state = gameStateChanged.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST)
		{
			reset();
		}
	}

	// Also called from the plugin's shutDown: Guice hands back this same instance on re-enable, so
	// unregistering from the event bus alone would leave the old count waiting.
	public void reset()
	{
		lastBoss = null;
		lastKillCount = 0;
		lastKind = null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		// Every count message observed in game arrives on one of these two. Widening this gate is not
		// the fix for a count that isn't picked up - check the colour markup first. See AGENTS.md.
		if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE
			&& chatMessage.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		String message = COLOUR_MARKUP_PATTERN.matcher(chatMessage.getMessage()).replaceAll("");

		Matcher matcher = KILL_COUNT_PATTERN.matcher(message);
		if (matcher.find())
		{
			String boss = Text.removeTags(matcher.group("boss"));
			lastBoss = boss;
			lastKillCount = parseCount(matcher.group("kc"));
			lastKind = kindOf(matcher.group("pre"), matcher.group("post"), boss);
			return;
		}

		for (FixedSource fixedSource : FIXED_SOURCES)
		{
			Matcher fixedMatcher = fixedSource.getPattern().matcher(message);
			if (fixedMatcher.find())
			{
				lastBoss = fixedSource.getSource();
				lastKillCount = parseCount(fixedMatcher.group("kc"));
				lastKind = fixedSource.getKind();
				return;
			}
		}
	}

	/**
	 * @return what the count represents. Anything unrecognised falls back to
	 *         {@link KillCountKind#KILLS}.
	 */
	private static KillCountKind kindOf(String pre, String post, String boss)
	{
		if (pre != null)
		{
			switch (pre.trim())
			{
				case "subdued":
					return KillCountKind.SUBDUES;
				case "completed":
				case "completion count for":
					return KillCountKind.COMPLETIONS;
				default:
					break;
			}
		}

		if (post != null)
		{
			switch (post.trim())
			{
				case "harvest":
					return KillCountKind.HARVESTS;
				case "completion":
					return KillCountKind.COMPLETIONS;
				case "success":
					return KillCountKind.SUCCESSES;
				case "Total Ticket":
					return KillCountKind.TICKETS;
				default:
					return KillCountKind.KILLS;
			}
		}

		// No verb at all. Chests are the only activities that word it this way, and matching on the
		// name rather than a per-boss table picks up a new one for free.
		return CHEST_SUFFIX_PATTERN.matcher(boss).find() ? KillCountKind.CHESTS : KillCountKind.KILLS;
	}

	private static int parseCount(String count)
	{
		return Integer.parseInt(count.replace(",", ""));
	}

	/**
	 * @param candidateSources every collection log tab the item appears on
	 * @return the most recent kill count if its boss name matches any of {@code candidateSources},
	 *         else null. Matching is case-insensitive, alias- and article-aware.
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
		return new RecentKill(lastBoss, lastKillCount, lastKind);
	}

	// Substring-with-word-boundaries rather than equality: the collection log doesn't track raid
	// difficulty suffixes separately ("tombs of amascut: expert mode"), and "corrupted gauntlet"
	// shares a tab with the base activity. Complements BOSS_ALIASES rather than replacing it.
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

	// Only the definite article is stripped - no dataset tab name starts with "A "/"An ".
	private static String normalize(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.startsWith(ARTICLE_PREFIX) ? lower.substring(ARTICLE_PREFIX.length()) : lower;
	}

	@Value
	private static class FixedSource
	{
		Pattern pattern;
		String source;
		KillCountKind kind;
	}

	@Value
	public static class RecentKill
	{
		String source;
		int killCount;
		KillCountKind kind;
	}
}
