package com.snakesteak.collectionlogpopupenhanced.killcount;

import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
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
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.GAMEMESSAGE);
		chatMessage.setMessage(message);
		tracker.onChatMessage(chatMessage);
	}

	@Test
	public void parsesBossKillCount()
	{
		fireMessage("Your Zulrah kill count is: <col=ff0000>41</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Zulrah"));
		assertEquals("Zulrah", kill.getSource());
		assertEquals(41, kill.getKillCount());
	}

	@Test
	public void parsesBarrowsChestCount()
	{
		fireMessage("Your Barrows chest count is: <col=ff0000>128</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Barrows chest"));
		assertEquals("Barrows chest", kill.getSource());
		assertEquals(128, kill.getKillCount());
	}

	@Test
	public void parsesLunarChestCount()
	{
		fireMessage("Your Lunar Chest count is: <col=ff0000>320</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Lunar Chest"));
		assertEquals("Lunar Chest", kill.getSource());
		assertEquals(320, kill.getKillCount());
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
	}

	@Test
	public void matchesSourceWithLeadingArticle()
	{
		// The collection log tab is "The Mad Angel", but the kill count message omits the article.
		fireMessage("Your Mad Angel kill count is: <col=ff0000>35</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Mad Angel"));
		assertEquals("Mad Angel", kill.getSource());
		assertEquals(35, kill.getKillCount());
	}

	@Test
	public void matchesOtherArticlePrefixedSources()
	{
		fireMessage("Your Nightmare kill count is: <col=ff0000>12</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Nightmare"));
		assertEquals("Nightmare", kill.getSource());
		assertEquals(12, kill.getKillCount());
	}

	@Test
	public void matchesDagannothKingsVariantAgainstGroupedTab()
	{
		fireMessage("Your Dagannoth Rex kill count is: <col=ff0000>5</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Dagannoth Kings"));
		assertEquals("Dagannoth Rex", kill.getSource());
		assertEquals(5, kill.getKillCount());
	}

	@Test
	public void matchesWildernessDuoBossAgainstGroupedTab()
	{
		fireMessage("Your Callisto kill count is: <col=ff0000>2</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Callisto and Artio"));
		assertEquals("Callisto", kill.getSource());
		assertEquals(2, kill.getKillCount());
	}

	@Test
	public void matchesBarrowsChestAgainstJsonTabName()
	{
		// The dataset's tab is "Barrows Chests" (plural) - the chat message says "Barrows chest".
		fireMessage("Your Barrows chest count is: <col=ff0000>128</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Barrows Chests"));
		assertEquals("Barrows chest", kill.getSource());
		assertEquals(128, kill.getKillCount());
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
		assertEquals("Yama success", kill.getSource());
		assertEquals(241, kill.getKillCount());
	}

	@Test
	public void parsesSubduedWintertodtCount()
	{
		fireMessage("Your subdued Wintertodt count is: <col=ff0000>12</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Wintertodt"));
		assertEquals("subdued Wintertodt", kill.getSource());
		assertEquals(12, kill.getKillCount());
	}

	@Test
	public void parsesCompletedChambersOfXericCount()
	{
		fireMessage("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Chambers of Xeric"));
		assertEquals("completed Chambers of Xeric", kill.getSource());
		assertEquals(5, kill.getKillCount());
	}

	@Test
	public void matchesChambersOfXericChallengeModeAgainstBaseTab()
	{
		// Challenge Mode isn't tracked as a separate collection log source - the difficulty suffix
		// must not prevent the match.
		fireMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>3</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Chambers of Xeric"));
		assertEquals("completed Chambers of Xeric Challenge Mode", kill.getSource());
		assertEquals(3, kill.getKillCount());
	}

	@Test
	public void matchesTheatreOfBloodHardModeAgainstBaseTab()
	{
		fireMessage("Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>2</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Theatre of Blood"));
		assertEquals("completed Theatre of Blood: Hard Mode", kill.getSource());
		assertEquals(2, kill.getKillCount());
	}

	@Test
	public void matchesTombsOfAmascutEntryModeAgainstBaseTab()
	{
		fireMessage("Your completed Tombs of Amascut: Entry Mode count is: <col=ff0000>1</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Tombs of Amascut"));
		assertEquals("completed Tombs of Amascut: Entry Mode", kill.getSource());
		assertEquals(1, kill.getKillCount());
	}

	@Test
	public void matchesTombsOfAmascutExpertModeAgainstBaseTab()
	{
		fireMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>107</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("Tombs of Amascut"));
		assertEquals("completed Tombs of Amascut: Expert Mode", kill.getSource());
		assertEquals(107, kill.getKillCount());
	}

	@Test
	public void matchesFightCavesAgainstTzTokJadAlias()
	{
		fireMessage("Your TzTok-Jad kill count is: <col=ff0000>99</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Fight Caves"));
		assertEquals("TzTok-Jad", kill.getSource());
		assertEquals(99, kill.getKillCount());
	}

	@Test
	public void matchesInfernoAgainstTzKalZukAlias()
	{
		fireMessage("Your TzKal-Zuk kill count is: <col=ff0000>3</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Inferno"));
		assertEquals("TzKal-Zuk", kill.getSource());
		assertEquals(3, kill.getKillCount());
	}

	@Test
	public void matchesGauntletCompletionCount()
	{
		fireMessage("Your Gauntlet completion count is: <col=ff0000>10</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Gauntlet"));
		assertEquals("Gauntlet completion", kill.getSource());
		assertEquals(10, kill.getKillCount());
	}

	@Test
	public void matchesCorruptedGauntletCompletionCountAgainstSameTab()
	{
		// The dataset has no separate "Corrupted Gauntlet" tab - both variants track under
		// "The Gauntlet".
		fireMessage("Your Corrupted Gauntlet completion count is: <col=ff0000>4</col>.");

		KillCountTracker.RecentKill kill = tracker.killCountFor(List.of("The Gauntlet"));
		assertEquals("Corrupted Gauntlet completion", kill.getSource());
		assertEquals(4, kill.getKillCount());
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
}
