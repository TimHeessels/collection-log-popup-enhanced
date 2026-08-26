package com.snakesteak.collectionlogpopupenhanced.rarity;

import com.google.gson.Gson;
import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.droprate.DropRateResolver;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.TileItem;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The pet-resolution test below needs the real collection-log.json fixture (see
 * src/test/resources/local-only/README.md, gitignored) - all tests in this class are skipped
 * rather than failed if it isn't present locally.
 */
public class ItemIdResolverTest
{
	private static final String FIXTURE = "/local-only/collection-log.json";

	private Client client;
	private ItemManager itemManager;
	private ItemIdResolver resolver;

	private static Map<String, RarityResolver.CompletionEntry> loadFixture()
	{
		InputStream stream = ItemIdResolverTest.class.getResourceAsStream(FIXTURE);
		Assume.assumeTrue("Local fixture " + FIXTURE + " not present - see src/test/resources/local-only/README.md", stream != null);
		Gson gson = new Gson();
		try (Reader reader = new InputStreamReader(stream))
		{
			return gson.fromJson(reader, RarityResolver.DATASET_TYPE);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private static ItemPrice itemPrice(int id, String name)
	{
		ItemPrice itemPrice = new ItemPrice();
		itemPrice.setId(id);
		itemPrice.setName(name);
		return itemPrice;
	}

	private static void nameItem(ItemManager itemManager, int id, String name)
	{
		ItemComposition composition = mock(ItemComposition.class);
		when(composition.getName()).thenReturn(name);
		when(itemManager.getItemComposition(id)).thenReturn(composition);
	}

	private static ItemContainer inventoryOf(Item... items)
	{
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(items);
		return inventory;
	}

	/**
	 * Captures the id/source delivered to a ResolveCallback so tests can assert on it after the fact.
	 * Resolution is asynchronous now (see ItemIdResolver.resolveIdByName), so tests can't just take a
	 * return value.
	 */
	private static class CapturingCallback implements ItemIdResolver.ResolveCallback
	{
		private boolean called;
		private int value;
		private ItemIdResolver.Source source;

		@Override
		public void accept(int value, ItemIdResolver.Source source)
		{
			this.called = true;
			this.value = value;
			this.source = source;
		}
	}

	@Before
	public void before()
	{
		client = mock(Client.class);
		itemManager = mock(ItemManager.class);
		CollectionLogPopupEnhancedConfig config = new CollectionLogPopupEnhancedConfig()
		{
		};
		DropRateResolver dropRateResolver = mock(DropRateResolver.class);
		RarityResolver rarityResolver = new RarityResolver(itemManager, config, dropRateResolver);
		rarityResolver.reload(loadFixture());
		resolver = new ItemIdResolver(client, itemManager, rarityResolver);

		ItemContainer emptyInventory = inventoryOf();
		when(client.getItemContainer(InventoryID.INV)).thenReturn(emptyInventory);
		when(itemManager.search(org.mockito.ArgumentMatchers.anyString())).thenReturn(Collections.emptyList());
	}

	@Test
	public void resolvesPetByNameWithoutTouchingInventoryOrGround()
	{
		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Baby mole", callback);

		assertEquals(net.runelite.api.gameval.ItemID.MOLEPET, callback.value);
		assertEquals(ItemIdResolver.Source.PET, callback.source);
	}

	@Test
	public void findsMatchInInventorySynchronously()
	{
		ItemContainer inventory = inventoryOf(new Item(100, 1), new Item(-1, 0));
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		nameItem(itemManager, 100, "Foo");

		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Foo", callback);

		assertEquals(100, callback.value);
		assertEquals(ItemIdResolver.Source.INVENTORY, callback.source);
	}

	@Test
	public void findsMatchAmongTrackedGroundItemsSynchronously()
	{
		TileItem tileItem = mock(TileItem.class);
		when(tileItem.getId()).thenReturn(200);
		nameItem(itemManager, 200, "Bar");

		resolver.onItemSpawned(new ItemSpawned(null, tileItem));

		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Bar", callback);

		assertEquals(200, callback.value);
		assertEquals(ItemIdResolver.Source.GROUND, callback.source);
	}

	@Test
	public void despawnedGroundItemNoLongerMatches()
	{
		TileItem tileItem = mock(TileItem.class);
		when(tileItem.getId()).thenReturn(201);
		nameItem(itemManager, 201, "Baz");

		resolver.onItemSpawned(new ItemSpawned(null, tileItem));
		resolver.onItemDespawned(new ItemDespawned(null, tileItem));

		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Baz", callback);
		assertFalse("should defer rather than resolve immediately", callback.called);

		resolver.onGameTick(new GameTick());
		assertEquals(-1, callback.value);
		assertEquals(ItemIdResolver.Source.UNRESOLVED, callback.source);
	}

	@Test
	public void fallsBackToGeSearchAtEndOfTickWhenNotInInventoryOrOnGround()
	{
		when(itemManager.search("Twisted bow")).thenReturn(List.of(itemPrice(20997, "Twisted bow")));

		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Twisted bow", callback);
		assertFalse("should defer rather than resolve immediately", callback.called);

		resolver.onGameTick(new GameTick());
		assertEquals(20997, callback.value);
		assertEquals(ItemIdResolver.Source.GE_SEARCH, callback.source);
	}

	@Test
	public void fallsBackToDatasetForUntradeableNotInInventoryOrOnGround()
	{
		// Guild hunter top is untradeable, so itemManager.search finds nothing (the @Before stubs an
		// empty GE result for every name) - the dataset index is the only check left that can resolve
		// it, and without it the popup renders a blank icon for id -1.
		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Guild hunter top", callback);
		assertFalse("should defer rather than resolve immediately", callback.called);

		resolver.onGameTick(new GameTick());
		assertEquals(29265, callback.value);
		assertEquals(ItemIdResolver.Source.DATASET, callback.source);
	}

	@Test
	public void prefersGeSearchOverDatasetWhenItemIsTradeable()
	{
		// The dataset only knows a canonical id, so a live-game check must always win where one is
		// available - here the GE search, which resolves the exact tradeable item.
		when(itemManager.search("Twisted bow")).thenReturn(List.of(itemPrice(20997, "Twisted bow")));

		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Twisted bow", callback);
		resolver.onGameTick(new GameTick());

		assertEquals(20997, callback.value);
		assertEquals(ItemIdResolver.Source.GE_SEARCH, callback.source);
	}

	@Test
	public void datasetFallbackPicksLowestIdForNamesSharedByVariants()
	{
		// Chompy bird hat covers 18 ids, all cosmetic variants of one item; the name alone can't say
		// which, so the lowest is used as a representative sprite.
		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Chompy bird hat", callback);
		resolver.onGameTick(new GameTick());

		assertEquals(2978, callback.value);
		assertEquals(ItemIdResolver.Source.DATASET, callback.source);
	}

	@Test
	public void returnsMinusOneWhenNothingMatchesByEndOfTick()
	{
		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Nothing matches this", callback);

		resolver.onGameTick(new GameTick());
		assertEquals(-1, callback.value);
		assertEquals(ItemIdResolver.Source.UNRESOLVED, callback.source);
	}

	/**
	 * Reproduces the real-world bug: the unlock chat message arrives BEFORE the
	 * ItemContainerChanged that adds the item, so a synchronous check always falls back to -1. The
	 * resolver must wait for the inventory update and diff against the pre-message snapshot.
	 */
	@Test
	public void resolvesUntradeableItemFromDeferredInventoryDiff()
	{
		nameItem(itemManager, 300, "Partial note");

		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Partial note", callback);
		assertFalse("id shouldn't be known yet - inventory hasn't updated", callback.called);

		ItemContainer updatedInventory = inventoryOf(new Item(300, 1));
		resolver.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, updatedInventory));

		assertEquals(300, callback.value);
		assertEquals(ItemIdResolver.Source.INVENTORY_DIFF, callback.source);
	}

	@Test
	public void ignoresItemContainerChangedForOtherContainers()
	{
		// Deliberately a name absent from the dataset: the point of the test is the assertFalse below,
		// and a real collection log name would now resolve via the dataset fallback on the tick,
		// masking whether the bank update itself was correctly ignored.
		nameItem(itemManager, 300, "Not a real item");

		CapturingCallback callback = new CapturingCallback();
		resolver.resolveIdByName("Not a real item", callback);

		ItemContainer bank = inventoryOf(new Item(300, 1));
		resolver.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, bank));
		assertFalse("bank updates shouldn't resolve an inventory-bound lookup", callback.called);

		resolver.onGameTick(new GameTick());
		assertEquals(-1, callback.value);
		assertEquals(ItemIdResolver.Source.UNRESOLVED, callback.source);
	}
}
