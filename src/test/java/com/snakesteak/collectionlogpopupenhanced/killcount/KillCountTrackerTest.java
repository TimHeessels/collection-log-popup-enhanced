package com.snakesteak.collectionlogpopupenhanced.killcount;

import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KillCountTrackerTest
{
	private KillCountTracker tracker;

	@Before
	public void before()
	{
		tracker = new KillCountTracker();
	}

	private void fireMessage(String message)
	{
		fireMessage(message, ChatMessageType.GAMEMESSAGE);
	}

	private void fireMessage(String message, ChatMessageType type)
	{
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(type);
		chatMessage.setMessage(message);
		tracker.onChatMessage(chatMessage);
	}

	private void fireGameState(GameState state)
	{
		GameStateChanged gameStateChanged = new GameStateChanged();
		gameStateChanged.setGameState(state);
		tracker.onGameStateChanged(gameStateChanged);
	}

	@Test
	public void parsesBossKillCount()
	{
		fireMessage("Your Zulrah kill count is: <col=ff0000>41</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Zulrah"));
		assertEquals("Zulrah", kill.getSource());
		assertEquals(41, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void parsesBarrowsChestCount()
	{
		fireMessage("Your Barrows chest count is: <col=ff0000>128</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Barrows chest"));
		assertEquals("Barrows chest", kill.getSource());
		assertEquals(128, kill.getKillCount());
		assertEquals(KillCountKind.CHESTS, kill.getKind());
	}

	@Test
	public void parsesLunarChestCount()
	{
		fireMessage("Your Lunar Chest count is: <col=ff0000>320</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Lunar Chest"));
		assertEquals("Lunar Chest", kill.getSource());
		assertEquals(320, kill.getKillCount());
		assertEquals(KillCountKind.CHESTS, kill.getKind());
	}

	@Test
	public void ignoresUnrelatedMessages()
	{
		fireMessage("You have completed 1 out of 8 medium clue scrolls.");

		assertNull(tracker.killCountFor(List.of("Zulrah")));
	}

	@Test
	public void doesNotReturnKillCountForUnrelatedSource()
	{
		fireMessage("Your Zulrah kill count is: <col=ff0000>41</col>.");

		assertNull(tracker.killCountFor(List.of("Vorkath")));
	}

	@Test
	public void staysValidAcrossAnArbitraryDelay()
	{
		// Some bosses (e.g. ones looted by searching the corpse) can have a long, player-timed gap
		// between the kill count message and the collection log message - the lookup shouldn't expire
		// based on time/ticks, only on source name matching.
		fireMessage("Your Maggot King kill count is: <col=ff0000>350</col>.");
		fireMessage("Fight duration: <col=ff0000>1:58.2</col>. Personal best: 1:21.0");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Maggot King"));
		assertEquals("Maggot King", kill.getSource());
		assertEquals(350, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void matchesSourceWithLeadingArticle()
	{
		// The collection log tab is "The Mad Angel", but the kill count message omits the article.
		fireMessage("Your Mad Angel kill count is: <col=ff0000>35</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Mad Angel"));
		assertEquals("Mad Angel", kill.getSource());
		assertEquals(35, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void matchesOtherArticlePrefixedSources()
	{
		fireMessage("Your Nightmare kill count is: <col=ff0000>12</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Nightmare"));
		assertEquals("Nightmare", kill.getSource());
		assertEquals(12, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void matchesDagannothKingsVariantAgainstGroupedTab()
	{
		fireMessage("Your Dagannoth Rex kill count is: <col=ff0000>5</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Dagannoth Kings"));
		assertEquals("Dagannoth Rex", kill.getSource());
		assertEquals(5, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void matchesWildernessDuoBossAgainstGroupedTab()
	{
		fireMessage("Your Callisto kill count is: <col=ff0000>2</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Callisto and Artio"));
		assertEquals("Callisto", kill.getSource());
		assertEquals(2, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void matchesBarrowsChestAgainstJsonTabName()
	{
		// The dataset's tab is "Barrows Chests" (plural) - the chat message says "Barrows chest".
		fireMessage("Your Barrows chest count is: <col=ff0000>128</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Barrows Chests"));
		assertEquals("Barrows chest", kill.getSource());
		assertEquals(128, kill.getKillCount());
		assertEquals(KillCountKind.CHESTS, kill.getKind());
	}

	@Test
	public void matchesLunarChestAgainstMoonsOfPerilTab()
	{
		// The dataset has no "Lunar Chest" tab at all - Moons of Peril loot is tracked under
		// "Moons of Peril", while the chat message names the chest itself.
		fireMessage("Your Lunar Chest count is: <col=ff0000>320</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Moons of Peril"));
		assertEquals("Lunar Chest", kill.getSource());
		assertEquals(320, kill.getKillCount());
		assertEquals(KillCountKind.CHESTS, kill.getKind());
	}

	@Test
	public void doesNotFalselyMatchUnrelatedGroupedBoss()
	{
		fireMessage("Your Vet'ion kill count is: <col=ff0000>7</col>.");

		assertNull(tracker.killCountFor(List.of("Callisto and Artio")));
	}

	@Test
	public void parsesYamaSuccessCountAsKillCount()
	{
		// Yama's kills are tracked as a "success count" rather than a "kill count" - see
		// https://oldschool.runescape.wiki/w/Yama#Trivia
		fireMessage("Your Yama success count is: <col=ff0000>241</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Yama"));
		assertEquals("Yama", kill.getSource());
		assertEquals(241, kill.getKillCount());
		assertEquals(KillCountKind.SUCCESSES, kill.getKind());
	}

	@Test
	public void parsesSubduedWintertodtCount()
	{
		fireMessage("Your subdued Wintertodt count is: <col=ff0000>12</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Wintertodt"));
		assertEquals("Wintertodt", kill.getSource());
		assertEquals(12, kill.getKillCount());
		assertEquals(KillCountKind.SUBDUES, kill.getKind());
	}

	@Test
	public void parsesCompletedChambersOfXericCount()
	{
		fireMessage("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Chambers of Xeric"));
		assertEquals("Chambers of Xeric", kill.getSource());
		assertEquals(5, kill.getKillCount());
		assertEquals(KillCountKind.COMPLETIONS, kill.getKind());
	}

	@Test
	public void matchesChambersOfXericChallengeModeAgainstBaseTab()
	{
		// Challenge Mode isn't tracked as a separate collection log source - the difficulty suffix
		// must not prevent the match.
		fireMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>3</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Chambers of Xeric"));
		assertEquals("Chambers of Xeric Challenge Mode", kill.getSource());
		assertEquals(3, kill.getKillCount());
		assertEquals(KillCountKind.COMPLETIONS, kill.getKind());
	}

	@Test
	public void matchesTheatreOfBloodHardModeAgainstBaseTab()
	{
		fireMessage("Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>2</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Theatre of Blood"));
		assertEquals("Theatre of Blood: Hard Mode", kill.getSource());
		assertEquals(2, kill.getKillCount());
		assertEquals(KillCountKind.COMPLETIONS, kill.getKind());
	}

	@Test
	public void matchesTombsOfAmascutEntryModeAgainstBaseTab()
	{
		fireMessage("Your completed Tombs of Amascut: Entry Mode count is: <col=ff0000>1</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Tombs of Amascut"));
		assertEquals("Tombs of Amascut: Entry Mode", kill.getSource());
		assertEquals(1, kill.getKillCount());
		assertEquals(KillCountKind.COMPLETIONS, kill.getKind());
	}

	@Test
	public void matchesTombsOfAmascutExpertModeAgainstBaseTab()
	{
		fireMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>107</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Tombs of Amascut"));
		assertEquals("Tombs of Amascut: Expert Mode", kill.getSource());
		assertEquals(107, kill.getKillCount());
		assertEquals(KillCountKind.COMPLETIONS, kill.getKind());
	}

	@Test
	public void matchesFightCavesAgainstTzTokJadAlias()
	{
		fireMessage("Your TzTok-Jad kill count is: <col=ff0000>99</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Fight Caves"));
		assertEquals("TzTok-Jad", kill.getSource());
		assertEquals(99, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void matchesInfernoAgainstTzKalZukAlias()
	{
		fireMessage("Your TzKal-Zuk kill count is: <col=ff0000>3</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Inferno"));
		assertEquals("TzKal-Zuk", kill.getSource());
		assertEquals(3, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void matchesGauntletCompletionCount()
	{
		fireMessage("Your Gauntlet completion count is: <col=ff0000>10</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Gauntlet"));
		assertEquals("Gauntlet", kill.getSource());
		assertEquals(10, kill.getKillCount());
		assertEquals(KillCountKind.COMPLETIONS, kill.getKind());
	}

	@Test
	public void matchesCorruptedGauntletCompletionCountAgainstSameTab()
	{
		// The dataset has no separate "Corrupted Gauntlet" tab - both variants track under
		// "The Gauntlet".
		fireMessage("Your Corrupted Gauntlet completion count is: <col=ff0000>4</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Gauntlet"));
		assertEquals("Corrupted Gauntlet", kill.getSource());
		assertEquals(4, kill.getKillCount());
		assertEquals(KillCountKind.COMPLETIONS, kill.getKind());
	}

	@Test
	public void doesNotFalselyMatchUnrelatedSourceViaWholeWordSearch()
	{
		fireMessage("Your Vet'ion kill count is: <col=ff0000>7</col>.");

		assertNull(tracker.killCountFor(List.of("Callisto and Artio")));
	}

	@Test
	public void parsesDoomOfMokhaiotlDeepDelveCount()
	{
		fireMessage("Deep delves completed: <col=ff0000>42</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Doom of Mokhaiotl"));
		assertEquals("Doom of Mokhaiotl", kill.getSource());
		assertEquals(42, kill.getKillCount());
		assertEquals(KillCountKind.DEEP_DELVES, kill.getKind());
	}

	@Test
	public void ignoresDelveProgressMessagesBelowDeepDelveThreshold()
	{
		// Delve levels 1-7 don't carry any parseable count - matches the game's own HiScores, which
		// don't show a kill count until 5 deep delves either. Not something the plugin can work around.
		fireMessage("Delve level: 8 duration: 2:59. Personal best: 1:10");
		fireMessage("Delve level 1 - 8 duration: 16:25. Personal best: 9:51");

		assertNull(tracker.killCountFor(List.of("Doom of Mokhaiotl")));
	}

	@Test
	public void parsesHesporiHarvestCount()
	{
		fireMessage("Your Hespori harvest count is: <col=ff0000>12</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hespori"));
		assertEquals("Hespori", kill.getSource());
		assertEquals(12, kill.getKillCount());
		assertEquals(KillCountKind.HARVESTS, kill.getKind());
	}

	@Test
	public void parsesBarbarianAssaultTicketCount()
	{
		fireMessage("Your Barbarian Assault Total Ticket count is: <col=ff0000>50</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Barbarian Assault"));
		assertEquals("Barbarian Assault", kill.getSource());
		assertEquals(50, kill.getKillCount());
		assertEquals(KillCountKind.TICKETS, kill.getKind());
	}

	@Test
	public void parsesCompletionCountForPrefixForm()
	{
		// The one message that puts "completion count" *before* the name - unparseable before the
		// pre/post groups were split out.
		fireMessage("Your completion count for the Fight Caves is: <col=ff0000>2</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Fight Caves"));
		assertEquals("the Fight Caves", kill.getSource());
		assertEquals(2, kill.getKillCount());
		assertEquals(KillCountKind.COMPLETIONS, kill.getKind());
	}

	@Test
	public void parsesUncolouredCountMessage()
	{
		// Not every count message is coloured, so the col tags stay optional throughout the pattern.
		fireMessage("Your Zulrah kill count is: 41.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Zulrah"));
		assertEquals("Zulrah", kill.getSource());
		assertEquals(41, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void parsesUncolouredSubduedCountMessage()
	{
		fireMessage("Your subdued Wintertodt count is: 12.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Wintertodt"));
		assertEquals("Wintertodt", kill.getSource());
		assertEquals(12, kill.getKillCount());
		assertEquals(KillCountKind.SUBDUES, kill.getKind());
	}

	@Test
	public void parsesThousandsSeparatedCount()
	{
		fireMessage("Your Zulrah kill count is: <col=ff0000>1,337</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Zulrah"));
		assertEquals(1337, kill.getKillCount());
	}

	@Test
	public void doesNotLabelABossMerelyContainingChestAsChests()
	{
		// "chest" only marks a chest opening as a whole trailing word - a name that happens to end in
		// those letters is still a kill.
		fireMessage("Your Chesty kill count is: <col=ff0000>3</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Chesty"));
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void parsesGuardiansOfTheRiftClosedCount()
	{
		fireMessage("Amount of Rifts you have closed: <col=ff0000>153</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Guardians of the Rift"));
		assertEquals("Guardians of the Rift", kill.getSource());
		assertEquals(153, kill.getKillCount());
		assertEquals(KillCountKind.RIFTS, kill.getKind());
	}

	@Test
	public void parsesHallowedSepulchreFloorCount()
	{
		fireMessage("You have completed Floor 5 of the Hallowed Sepulchre! Total completions: <col=ff0000>84</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hallowed Sepulchre"));
		assertEquals("Hallowed Sepulchre", kill.getSource());
		assertEquals(84, kill.getKillCount());
		assertEquals(KillCountKind.FLOORS, kill.getKind());
	}

	@Test
	public void parsesGrandHallowedCoffinCount()
	{
		fireMessage("You have opened the Grand Hallowed Coffin <col=ff0000>27</col> times!");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hallowed Sepulchre"));
		assertEquals("Hallowed Sepulchre", kill.getSource());
		assertEquals(27, kill.getKillCount());
		assertEquals(KillCountKind.COFFINS, kill.getKind());
	}

	@Test
	public void parsesGrandHallowedCoffinSingularCount()
	{
		fireMessage("You have opened the Grand Hallowed Coffin <col=ff0000>1</col> time!");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hallowed Sepulchre"));
		assertEquals(1, kill.getKillCount());
		assertEquals(KillCountKind.COFFINS, kill.getKind());
	}

	@Test
	public void parsesHunterGuildRumourCount()
	{
		// Confirmed in game to arrive on the game channel, like every other count message.
		fireMessage("You have completed <col=ff0000>210</col> rumours for the Hunter Guild.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hunter Guild"));
		assertEquals("Hunter Guild", kill.getSource());
		assertEquals(210, kill.getKillCount());
		assertEquals(KillCountKind.RUMOURS, kill.getKind());
	}

	@Test
	public void parsesSingularHunterGuildRumourCount()
	{
		fireMessage("You have completed <col=ff0000>1</col> rumour for the Hunter Guild.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hunter Guild"));
		assertEquals(1, kill.getKillCount());
		assertEquals(KillCountKind.RUMOURS, kill.getKind());
	}

	@Test
	public void doesNotMatchClueScrollTallyAsHunterRumours()
	{
		// Shares the "You have completed N ..." opening with the rumour message - the trailing
		// "rumours for the Hunter Guild" is what separates them.
		fireMessage("You have completed 1 out of 8 medium clue scrolls.");

		assertNull(tracker.killCountFor(List.of("Hunter Guild")));
	}

	@Test
	public void laterCountMessageReplacesEarlierOne()
	{
		fireMessage("Your Zulrah kill count is: <col=ff0000>41</col>.");
		fireMessage("Deep delves completed: <col=ff0000>42</col>.");

		assertNull(tracker.killCountFor(List.of("Zulrah")));

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Doom of Mokhaiotl"));
		assertEquals(42, kill.getKillCount());
		assertEquals(KillCountKind.DEEP_DELVES, kill.getKind());
	}

	@Test
	public void forgetsKillCountOnLogout()
	{
		// The tracker is a @Singleton, so its state outlives the session unless cleared - without this
		// a count could be shown after logging in as a different character, where it belongs to
		// someone else entirely.
		fireMessage("Your Zulrah kill count is: <col=ff0000>300</col>.");
		fireGameState(GameState.LOGIN_SCREEN);

		assertNull(tracker.killCountFor(List.of("Zulrah")));
	}

	@Test
	public void forgetsKillCountOnConnectionLost()
	{
		fireMessage("Your Zulrah kill count is: <col=ff0000>300</col>.");
		fireGameState(GameState.CONNECTION_LOST);

		assertNull(tracker.killCountFor(List.of("Zulrah")));
	}

	@Test
	public void keepsKillCountAcrossAWorldHop()
	{
		// A hop keeps the same character, and corpse loot legitimately survives one - see
		// #staysValidAcrossAnArbitraryDelay.
		fireMessage("Your Maggot King kill count is: <col=ff0000>350</col>.");
		fireGameState(GameState.HOPPING);
		fireGameState(GameState.LOGGED_IN);

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Maggot King"));
		assertEquals(350, kill.getKillCount());
	}

	@Test
	public void forgetsKillCountOnReset()
	{
		// What the plugin's shutDown calls - Guice hands back the same instance on re-enable.
		fireMessage("Your Zulrah kill count is: <col=ff0000>300</col>.");
		tracker.reset();

		assertNull(tracker.killCountFor(List.of("Zulrah")));
	}

	@Test
	public void ignoresCountFromUnrelatedChannels()
	{
		// Public chat is player-authored - anyone could type a count message.
		fireMessage("Your Zulrah kill count is: <col=ff0000>41</col>.", ChatMessageType.PUBLICCHAT);

		assertNull(tracker.killCountFor(List.of("Zulrah")));
	}

	/**
	 * Pins the chat gate against being widened again on the strength of RuneLite's own more
	 * permissive gate. The rumour count's real cause was the colour macro, not the channel - see
	 * {@link #parsesRumourCountWithNamedColourMacro()} and AGENTS.md.
	 */
	@Test
	public void ignoresCountFromTradeAndFriendsChatChannels()
	{
		fireMessage("Your Zulrah kill count is: <col=ff0000>41</col>.", ChatMessageType.TRADE);
		assertNull(tracker.killCountFor(List.of("Zulrah")));

		fireMessage("You have completed <col=ff0000>311</col> rumours for the Hunter Guild.",
			ChatMessageType.FRIENDSCHATNOTIFICATION);
		assertNull(tracker.killCountFor(List.of("Hunter Guild")));
	}

	@Test
	public void parsesRumourCountWithUppercaseColourTag()
	{
		fireMessage("You have completed <col=FF0000>311</col> rumours for the Hunter Guild.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hunter Guild"));
		assertEquals(311, kill.getKillCount());
		assertEquals(KillCountKind.RUMOURS, kill.getKind());
	}

	@Test
	public void parsesRumourCountWithContractedOpening()
	{
		fireMessage("You've completed <col=ff0000>311</col> rumours for the Hunter Guild.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hunter Guild"));
		assertEquals(311, kill.getKillCount());
	}

	@Test
	public void parsesRumourCountWithNowInserted()
	{
		fireMessage("You have now completed <col=ff0000>311</col> rumours for the Hunter Guild.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hunter Guild"));
		assertEquals(311, kill.getKillCount());
	}

	@Test
	public void parsesCountWithUppercaseColourTag()
	{
		fireMessage("Your Zulrah kill count is: <col=FF0000>41</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Zulrah"));
		assertEquals(41, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void stillLabelsTicketsWithCaseInsensitivePattern()
	{
		// #kindOf switches on the verb's exact text, so the case-insensitive pattern must not change
		// what the post group captures.
		fireMessage("Your Barbarian Assault Total Ticket count is: <col=ff0000>50</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Barbarian Assault"));
		assertEquals(KillCountKind.TICKETS, kill.getKind());
	}

	@Test
	public void parsesRumourCountWithNamedColourMacro()
	{
		// Verbatim from a real client log: the count is coloured with a named macro rather than a
		// <col> tag, and the message still carries the closing </col> of the pair.
		fireMessage("You have completed @mes_hl_red@312</col> rumours for the Hunter Guild.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Hunter Guild"));
		assertEquals("Hunter Guild", kill.getSource());
		assertEquals(312, kill.getKillCount());
		assertEquals(KillCountKind.RUMOURS, kill.getKind());
	}

	@Test
	public void parsesKillCountWithNamedColourMacro()
	{
		fireMessage("Your Zulrah kill count is: @mes_hl_red@41</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Zulrah"));
		assertEquals("Zulrah", kill.getSource());
		assertEquals(41, kill.getKillCount());
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void stripsColourMacroFromBossName()
	{
		// The macro sits where a <col> tag would, immediately before the name - it must not end up
		// as part of the stored source.
		fireMessage("Your @mes_hl_red@Zulrah</col> kill count is: 41.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Zulrah"));
		assertEquals("Zulrah", kill.getSource());
	}
}
