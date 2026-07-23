package com.snakesteak.collectionlogpopupenhanced.overlay;

/**
 * User-facing choices for the bottom-right stat. Luck is the only one of the two that can be
 * unavailable for an item (no correlated kill), in which case it falls back to Value - see
 * {@link CollectionLogOverlay}.
 */
public enum RightPanelStat
{
	LUCK("Luck", PanelStat.LUCK),
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
