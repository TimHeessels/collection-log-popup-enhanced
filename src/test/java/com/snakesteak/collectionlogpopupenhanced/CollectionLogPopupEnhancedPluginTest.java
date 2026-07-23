package com.snakesteak.collectionlogpopupenhanced;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CollectionLogPopupEnhancedPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CollectionLogPopupEnhancedPlugin.class);
		RuneLite.main(args);
	}
}