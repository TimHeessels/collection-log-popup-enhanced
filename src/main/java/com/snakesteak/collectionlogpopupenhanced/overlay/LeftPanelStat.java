package com.snakesteak.collectionlogpopupenhanced.overlay;

/**
 * User-facing choices for the bottom-left stat. Kill count is the only one of the two that can be
 * unavailable for an item (no correlated kill), in which case it falls back to Completion - see
 * {@link CollectionLogOverlay}.
 */
public enum LeftPanelStat
{
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
