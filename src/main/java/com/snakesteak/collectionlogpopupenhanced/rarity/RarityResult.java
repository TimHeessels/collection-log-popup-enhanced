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
}
