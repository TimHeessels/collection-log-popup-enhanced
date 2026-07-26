package com.snakesteak.collectionlogpopupenhanced.overlay;

/**
 * User-facing choices for the bottom-right stat. Drop rate can be unavailable for an item (no
 * known drop rate, or ambiguous across more than 2 sources), in which case it falls back to Value -
 * see {@link CollectionLogOverlay}.
 */
public enum RightPanelStat
{
	DROP_RATE("Drop rate", PanelStat.DROP_RATE),
	VALUE("Value", PanelStat.VALUE);

	private final String label;
	private final PanelStat panelStat;

	RightPanelStat(String label, PanelStat panelStat)
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
