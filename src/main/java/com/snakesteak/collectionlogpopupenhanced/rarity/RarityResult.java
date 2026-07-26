package com.snakesteak.collectionlogpopupenhanced.rarity;

import lombok.Value;

/**
 * Everything that went into a resolve() call's tier decision, for display/debugging - the whole
 * point being that "trust me, it's rare" isn't verifiable without the raw numbers.
 */
@Value
public class RarityResult
{
	RarityTier tier;
	int itemId;
	int price;
	boolean highAlch;
	Double compPercent;
	Double completionScore;
	double valueScore;
	double percentile;
	int datasetSize;
	double logPriceMin;
	double logPriceMax;
	// Always the high alch value regardless of GE availability - unlike price/highAlch above (which
	// are GE-first, HA-fallback, and feed the rarity score), this lets display code honor a user
	// preference to always show High alch instead of GE (see ValueDisplayMode).
	int alchPrice;
}
