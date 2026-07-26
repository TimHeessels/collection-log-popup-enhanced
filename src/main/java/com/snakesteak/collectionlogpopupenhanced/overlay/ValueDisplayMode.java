package com.snakesteak.collectionlogpopupenhanced.overlay;

/**
 * User-facing choice for which price the Value stat (see {@link PanelStat#VALUE}) shows. GE value
 * still falls back to High alch when an item has no GE price (untradeable, or tradeable but
 * unlisted) - this only chooses which one is preferred.
 */
public enum ValueDisplayMode
{
	GE_VALUE("G.E. value"),
	HIGH_ALCH("High alch value");

	private final String label;

	ValueDisplayMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
