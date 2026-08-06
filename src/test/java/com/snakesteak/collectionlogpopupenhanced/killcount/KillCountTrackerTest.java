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
}
