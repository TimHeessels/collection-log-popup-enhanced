package com.snakesteak.collectionlogpopupenhanced.overlay;

/**
 * Which stat {@link CollectionLogOverlay} shows in one of the two configurable bottom-row text
 * fields.
 */
public enum PanelStat
{
	NONE("None"),
	VALUE("Value"),
	RARITY("Completion"),
	KILL_COUNT("Kill count"),
	DROP_RATE("Drop rate");

	private final String label;

	PanelStat(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
