package com.snakesteak.collectionlogpopupenhanced.overlay;

/**
 * Extended is the full panel with the framed icon below it; compact is a single short panel with the
 * icon inside it on the left. Both draw in whichever {@link PanelStyle} is selected.
 */
public enum PanelLayout
{
	EXTENDED("Extended"),
	COMPACT("Compact");

	private final String label;

	PanelLayout(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
