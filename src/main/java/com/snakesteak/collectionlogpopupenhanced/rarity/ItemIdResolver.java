package com.snakesteak.collectionlogpopupenhanced.rarity;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Resolves an item's id from its display name - the chat message that drives collection log
 * detection only ever gives us a name, never an id. Checked in order:
 * 1. {@link RarityResolver#petIdForName}, a name-to-id lookup derived from the wiki dataset's "All
 *    Pets" tag (pets attach as a follower NPC rather than entering the inventory or landing on the
 *    ground, so none of the checks below can find one)
 * 2. Current inventory (covers the rare case where the inventory update already landed)
 * 3. Ground items tracked via spawn/despawn events (covers drops and pickpocketing overflow still
 *    sitting on the ground, unpicked)
 * 4. A deferred diff of the inventory against a pre-message snapshot (see {@link #resolveIdByName})
 * 5. GE search by exact name, once the diff has had a full tick to land and found nothing
 * 6. {@link RarityResolver#datasetIdForName}, the wiki dataset's own name-to-id index - the last
 *    check standing for an untradeable item (absent from the GE index) that never reached the
 *    inventory or the ground, e.g. one granted straight to the bank, or a name typed at the
 *    "::clogtest" dev command with no real unlock behind it. It resolves a canonical id rather than
 *    the exact variant received, so it deliberately runs after every live-game check.
 */
@Singleton
public class ItemIdResolver
{
	/**
	 * Which check produced the id. Surfaced so the "::clogtest" dev command can tell a genuine
	 * ground-item hit from a GE-search fallback that happened to find the same tradeable item.
	 */
	public enum Source
	{
		PET, INVENTORY, GROUND, INVENTORY_DIFF, GE_SEARCH, DATASET, UNRESOLVED
	}

	/**
	 * @see #resolveIdByName
	 */
	public interface ResolveCallback
	{
		void accept(int itemId, Source source);
	}

	private final Client client;
	private final ItemManager itemManager;
	private final RarityResolver rarityResolver;
	private final Multiset<Integer> groundItemIds = HashMultiset.create();

	// Set while waiting on the inventory update for a name that couldn't be resolved synchronously.
	private String pendingItemName;
	private Multiset<Integer> pendingInventorySnapshot;
	private ResolveCallback pendingCallback;

	@Inject
	public ItemIdResolver(Client client, ItemManager itemManager, RarityResolver rarityResolver)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.rarityResolver = rarityResolver;
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		groundItemIds.add(itemSpawned.getItem().getId());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		groundItemIds.remove(itemDespawned.getItem().getId());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged itemContainerChanged)
	{
		if (pendingItemName == null || itemContainerChanged.getContainerId() != InventoryID.INV)
		{
			return;
		}

		Multiset<Integer> current = snapshotInventory(itemContainerChanged.getItemContainer());
		Integer id = findMatchingId(Multisets.difference(current, pendingInventorySnapshot).elementSet(), pendingItemName);
		if (id == null)
		{
			// Nothing new matched (e.g. an existing stack's quantity changed) - keep waiting, using
			// this as the new baseline so a later genuine addition still diffs correctly.
			pendingInventorySnapshot = current;
			return;
		}

		deliverPending(id, Source.INVENTORY_DIFF);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		// The inventory update didn't land this tick (or the item never enters the inventory at all,
		// e.g. it goes straight to the bank) - give up waiting and fall back to the name lookups.
		if (pendingItemName != null)
		{
			deliverByNameFallback(pendingItemName);
		}
	}

	/**
	 * @param itemName display name from the "New item added to your collection log" chat message
	 * @param callback invoked exactly once, on the client thread, with the resolved item id (-1 if
	 *                  unresolved) and which check found it
	 *
	 * The chat message arrives BEFORE the item appears in the inventory, so a live lookup at call
	 * time almost always misses. Resolution is deferred: snapshot the inventory now, diff it once it
	 * changes, and fall back to the name lookups if nothing lands by end of tick.
	 */
	public void resolveIdByName(String itemName, ResolveCallback callback)
	{
		Integer petItemId = rarityResolver.petIdForName(itemName);
		if (petItemId != null)
		{
			callback.accept(petItemId, Source.PET);
			return;
		}

		if (pendingItemName != null)
		{
			// A second lookup started before the first tick-fallback fired - flush the first one now
			// rather than silently dropping its callback.
			deliverByNameFallback(pendingItemName);
		}

		Integer id = findMatchingId(snapshotInventory(client.getItemContainer(InventoryID.INV)).elementSet(), itemName);
		if (id != null)
		{
			callback.accept(id, Source.INVENTORY);
			return;
		}

		id = findMatchingId(groundItemIds.elementSet(), itemName);
		if (id != null)
		{
			callback.accept(id, Source.GROUND);
			return;
		}

		pendingItemName = itemName;
		pendingInventorySnapshot = snapshotInventory(client.getItemContainer(InventoryID.INV));
		pendingCallback = callback;
	}

	private void deliverPending(int id, Source source)
	{
		ResolveCallback callback = pendingCallback;
		pendingItemName = null;
		pendingInventorySnapshot = null;
		pendingCallback = null;
		callback.accept(id, source);
	}

	private static Multiset<Integer> snapshotInventory(ItemContainer inventory)
	{
		Multiset<Integer> snapshot = HashMultiset.create();
		if (inventory == null)
		{
			return snapshot;
		}

		for (Item item : inventory.getItems())
		{
			if (item.getId() > 0)
			{
				snapshot.add(item.getId(), item.getQuantity());
			}
		}
		return snapshot;
	}

	private Integer findMatchingId(Collection<Integer> candidateIds, String itemName)
	{
		for (int id : candidateIds)
		{
			if (itemManager.getItemComposition(id).getName().equalsIgnoreCase(itemName))
			{
				return id;
			}
		}
		return null;
	}

	// GE search first, since it resolves the exact tradeable item, then the dataset index for the
	// untradeables a GE search can never find.
	private void deliverByNameFallback(String itemName)
	{
		int geId = resolveTradeableIdByExactName(itemName);
		if (geId >= 0)
		{
			deliverPending(geId, Source.GE_SEARCH);
			return;
		}

		Integer datasetId = rarityResolver.datasetIdForName(itemName);
		if (datasetId != null)
		{
			deliverPending(datasetId, Source.DATASET);
			return;
		}

		deliverPending(-1, Source.UNRESOLVED);
	}

	private int resolveTradeableIdByExactName(String itemName)
	{
		List<ItemPrice> matches = itemManager.search(itemName);
		for (ItemPrice match : matches)
		{
			if (match.getName().equalsIgnoreCase(itemName))
			{
				return match.getId();
			}
		}
		return -1;
	}
}
