package com.snakesteak.collectionlogpopupenhanced.overlay;

/**
 * User-facing choices for the bottom-left stat. Page progress and Kill count can both be
 * unavailable for an item (no locally tracked page progress, or no correlated kill respectively),
 * falling back down the chain Page progress -> Kill count -> Completion - see
 * {@link CollectionLogOverlay}.
 */
public enum LeftPanelStat
{
	PAGE_PROGRESS("Page progress", PanelStat.PAGE_PROGRESS),
	KILL_COUNT("Kill count", PanelStat.KILL_COUNT),
	COMPLETION("Completion", PanelStat.RARITY);

	private final String label;
	private final PanelStat panelStat;

	LeftPanelStat(String label, PanelStat panelStat)
	{
		this.label = label;
		this.panelStat = panelStat;
	}

	public PanelStat toPanelStat()
	{
		return panelStat;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
