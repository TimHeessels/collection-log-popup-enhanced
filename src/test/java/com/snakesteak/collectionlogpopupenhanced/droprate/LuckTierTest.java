package com.snakesteak.collectionlogpopupenhanced.droprate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Examples all use a 1/50 item (expected kill count 50) to make the ratio-to-expected math easy to
 * follow: killCount / (1 / dropProbability) == killCount * dropProbability.
 */
public class LuckTierTest
{
	private static final double ONE_IN_FIFTY = 1.0 / 50;

	@Test
	public void twoKillsOutOfFiftyIsInsanelyLucky()
	{
		assertEquals(LuckTier.INSANELY_LUCKY, LuckTier.fromKillCount(2, ONE_IN_FIFTY));
	}

	@Test
	public void tenKillsOutOfFiftyIsVeryLucky()
	{
		assertEquals(LuckTier.VERY_LUCKY, LuckTier.fromKillCount(10, ONE_IN_FIFTY));
	}

	@Test
	public void thirtyKillsOutOfFiftyIsLucky()
	{
		assertEquals(LuckTier.LUCKY, LuckTier.fromKillCount(30, ONE_IN_FIFTY));
	}

	@Test
	public void fiftyKillsOutOfFiftyIsAverage()
	{
		assertEquals(LuckTier.AVERAGE, LuckTier.fromKillCount(50, ONE_IN_FIFTY));
	}

	@Test
	public void eightyKillsOutOfFiftyIsUnlucky()
	{
		assertEquals(LuckTier.UNLUCKY, LuckTier.fromKillCount(80, ONE_IN_FIFTY));
	}

	@Test
	public void oneHundredTwentyKillsOutOfFiftyIsVeryUnlucky()
	{
		assertEquals(LuckTier.VERY_UNLUCKY, LuckTier.fromKillCount(120, ONE_IN_FIFTY));
	}

	@Test
	public void zeroKillsIsInsanelyLucky()
	{
		assertEquals(LuckTier.INSANELY_LUCKY, LuckTier.fromKillCount(0, ONE_IN_FIFTY));
	}
}
