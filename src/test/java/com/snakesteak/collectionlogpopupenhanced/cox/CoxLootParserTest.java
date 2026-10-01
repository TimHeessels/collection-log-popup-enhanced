package com.snakesteak.collectionlogpopupenhanced.cox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Optional;
import org.junit.Test;

public class CoxLootParserTest
{
	@Test
	public void prefersTheLongestNameContained()
	{
		assertEquals("Ancestral robe bottom", CoxLootParser.findUnique("Ancestral robe bottom"));
		assertEquals("Twisted bow", CoxLootParser.findUnique("<col=ff0000>Twisted bow</col>"));
		assertNull(CoxLootParser.findUnique("Abyssal whip"));
	}

	@Test
	public void parsesThePartyBroadcast()
	{
		Optional<CoxLootParser.LootBroadcast> loot =
			CoxLootParser.parsePartyLoot("<col=ef20ff>Some Player - </col><col=ff0000>Elder maul</col>");
		assertEquals(Optional.of(new CoxLootParser.LootBroadcast("Some Player", "Elder maul")), loot);
		assertFalse(CoxLootParser.parsePartyLoot("<col=ef20ff>Special loot:</col>").isPresent());
	}

	@Test
	public void parsesEachClanBroadcast()
	{
		CoxLootParser.LootBroadcast expected = new CoxLootParser.LootBroadcast("Some Player", "Kodai insignia");
		assertEquals(Optional.of(expected), CoxLootParser.parseClanBroadcast(
			"Some Player received special loot from a raid: Kodai insignia (12,345,678 coins)."));
		assertEquals(Optional.of(expected), CoxLootParser.parseClanBroadcast(
			"Some Player received a drop: Kodai insignia (12,345,678 coins)."));
		assertEquals(Optional.of(expected), CoxLootParser.parseClanBroadcast(
			"Some Player received a new collection log item: Kodai insignia (123/1500)"));
		assertFalse(CoxLootParser.parseClanBroadcast("Some Player received a drop: Abyssal whip").isPresent());
	}

	@Test
	public void recognisesOwnLootGameMessages()
	{
		assertTrue(CoxLootParser.isOwnLootGameMessage("New item added to your collection log: Twisted bow"));
		assertTrue(CoxLootParser.isOwnLootGameMessage("<col=ef1020>Valuable drop: Twisted bow (1,234 coins)</col>"));
		assertFalse(CoxLootParser.isOwnLootGameMessage("Your completed Chambers of Xeric count is: 12."));
	}

	@Test
	public void censorsTheItemAndItsValue()
	{
		assertEquals("<col=ef20ff>Some Player - </col><col=ff0000>???</col>",
			CoxLootParser.censorItem("<col=ef20ff>Some Player - </col><col=ff0000>Dragon claws</col>", "Dragon claws"));
		assertEquals("New item added to your collection log: ???",
			CoxLootParser.censorAfterColon("New item added to your collection log: Dragon claws"));
		assertEquals("<col=ef1020>Valuable drop: ???</col>",
			CoxLootParser.censorAfterColon("<col=ef1020>Valuable drop: Dragon claws (5,000,000 coins)</col>"));
	}

	@Test
	public void readsTheNativePopupText()
	{
		assertEquals("Twisted bow", CoxLootParser.notificationUnique("New item:<br><col=ffffff>Twisted bow</col>"));
		assertNull(CoxLootParser.notificationUnique("New item:<br><col=ffffff>Abyssal whip</col>"));
		assertNull(CoxLootParser.notificationUnique(null));
	}
}
