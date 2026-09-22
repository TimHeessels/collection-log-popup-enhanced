package com.snakesteak.collectionlogpopupenhanced;

import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountKind;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountTracker;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CollectionLogPopupEnhancedPluginPreviewKillCountTest
{
	@Test
	public void fakeKillCountUsesTheFirstCandidateSource()
	{
		// So the drop rate lookup right after this still resolves normally - it keys off the same
		// source string, and a made-up one would silently break it for preview/test items.
		KillCountTracker.RecentKill kill = CollectionLogPopupEnhancedPlugin.fakeKillCountFor(List.of("Zulrah", "Vorkath"));

		assertEquals("Zulrah", kill.getSource());
	}

	@Test
	public void fakeKillCountIsAPositiveNumberWithinRange()
	{
		KillCountTracker.RecentKill kill = CollectionLogPopupEnhancedPlugin.fakeKillCountFor(List.of("Zulrah"));

		assertTrue("expected a count between 1 and 500, was " + kill.getKillCount(),
			kill.getKillCount() >= 1 && kill.getKillCount() <= 500);
		assertEquals(KillCountKind.KILLS, kill.getKind());
	}

	@Test
	public void returnsNullForAnItemWithNoKnownSource()
	{
		// Nothing sensible to fall back to - the caller keeps showing Wiki Comp% for these, same as
		// before this change.
		assertNull(CollectionLogPopupEnhancedPlugin.fakeKillCountFor(List.of()));
	}
}
