package com.snakesteak.collectionlogpopupenhanced.overlay;

/**
 * Which bundled panel art the popup draws. Colourful art is recoloured to the per-tier colours;
 * neutral art is drawn exactly as bundled. Audio only draws nothing and leaves the game's popup up.
 */
public enum PanelStyle
{
	COLORFUL("Colourful", "Colorful"),
	NEUTRAL("Neutral", "Neutral"),
	// Never drawn - the directory only keeps the art loading path uniform.
	AUDIO_ONLY("Audio only", "Colorful");

	private final String label;
	private final String resourceDirectory;

	PanelStyle(String label, String resourceDirectory)
	{
		this.label = label;
		this.resourceDirectory = resourceDirectory;
	}

	String resource(String name)
	{
		return resourceDirectory + "/" + name;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
