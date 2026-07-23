package com.snakesteak.collectionlogpopupenhanced.droprate;

/**
 * How lucky a kill count was relative to an item's expected kill count (1 / drop probability).
 * Deliberately not the raw cumulative-probability percentile (100 * (1 - (1-p)^kc)) - that number
 * reads backwards to players, since a LOW percentile there actually means the item came fast
 * (good luck), not bad luck. Ratio-to-expected instead maps low = lucky, high = unlucky, matching
 * how players already think about drop luck ("I got it in a fraction of the average kc").
 */
public enum LuckTier
{
	INSANELY_LUCKY(0.05, "Insanely lucky"),
	VERY_LUCKY(0.25, "Very lucky"),
	LUCKY(0.75, "Lucky"),
	AVERAGE(1.5, "Average"),
	UNLUCKY(2.0, "Unlucky"),
	VERY_UNLUCKY(Double.POSITIVE_INFINITY, "Very unlucky");

	private final double maxRatio;
	private final String label;

	LuckTier(double maxRatio, String label)
	{
		this.maxRatio = maxRatio;
		this.label = label;
	}

	/**
	 * @param killCount kills at the time the item was obtained
	 * @param dropProbability the item's per-kill drop probability (0-1)
	 * @return the tier for killCount / (1 / dropProbability) - how many multiples of the expected
	 *         kill count it actually took
	 */
	public static LuckTier fromKillCount(int killCount, double dropProbability)
	{
		double ratio = killCount * dropProbability;
		for (LuckTier tier : values())
		{
			if (ratio <= tier.maxRatio)
			{
				return tier;
			}
		}
		return VERY_UNLUCKY;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
