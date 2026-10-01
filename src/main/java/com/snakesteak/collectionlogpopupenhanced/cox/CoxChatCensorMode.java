package com.snakesteak.collectionlogpopupenhanced.cox;

/**
 * Whose CoX purples are hidden in chat until the reward chest is opened.
 */
public enum CoxChatCensorMode
{
	OFF("Off"),
	OWN("Only mine"),
	EVERYONE("Everyone's");

	private final String label;

	CoxChatCensorMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
