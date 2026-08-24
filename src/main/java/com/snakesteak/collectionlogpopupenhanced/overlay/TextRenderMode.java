package com.snakesteak.collectionlogpopupenhanced.overlay;

/**
 * User-facing choice for how popup text is rasterized. Which option looks sharpest depends on the
 * user's display/OS scaling and isn't reliably predictable from the popup's own scale percentage, so
 * this is a manual choice rather than something the plugin infers automatically.
 */
public enum TextRenderMode
{
	CRISP("Pixel"),
	SMOOTH("Smooth"),
	SMOOTH_OUTLINED("Smooth outlined");

	private final String label;

	TextRenderMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
