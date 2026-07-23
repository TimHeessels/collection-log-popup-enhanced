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
 * 1. {@link PetItems}, a static name-to-id lookup (pets attach as a follower NPC rather than
 *    entering the inventory or landing on the ground, so none of the checks below can ever find
 *    one - see {@link PetItems} javadoc)
 * 2. Current inventory (covers the rare case where the inventory update already landed)
 * 3. Ground items tracked via spawn/despawn events (covers monster drops and pickpocketing/stealing
 *    overflow that are still sitting on the ground, unpicked)
 * 4. A deferred diff of the inventory against a pre-message snapshot (see {@link #resolveIdByName})
 * 5. GE search by exact name, once the diff has had a full tick to land and found nothing
 * Ground items are tracked via events rather than scanning the scene, per RuneLite plugin
 * performance conventions; a plugin started mid-session won't see items already on the ground
 * before it registered.
 */
@Singleton
public class ItemIdResolver
{
	/**
	 * Which of the checks in the class javadoc actually produced the id - surfaced so callers (in
	 * particular the "::clogtest" dev command) can tell whether e.g. ground-item detection genuinely
	 * fired, rather than the result looking identical because a GE-search fallback happened to also
	 * find the same tradeable item.
	 */
	public enum Source
	{
		PET, INVENTORY, GROUND, INVENTORY_DIFF, GE_SEARCH, UNRESOLVED
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
	private final Multiset<Integer> groundItemIds = HashMultiset.create();

	// Set while waiting on the inventory update for a name that couldn't be resolved synchronously -
	// see the javadoc on resolveIdByName for why this has to be deferred at all.
	private String pendingItemName;
	private Multiset<Integer> pendingInventorySnapshot;
	private ResolveCallback pendingCallback;

	@Inject
	public ItemIdResolver(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
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
		// e.g. it goes straight to the bank) - give up waiting and fall back to a GE lookup.
		if (pendingItemName != null)
		{
			int id = resolveTradeableIdByExactName(pendingItemName);
			deliverPending(id, id >= 0 ? Source.GE_SEARCH : Source.UNRESOLVED);
		}
	}

	/**
	 * @param itemName display name from the "New item added to your collection log" chat message
	 * @param callback invoked exactly once, on the client thread, with the resolved item id (-1 if it
	 *                  couldn't be resolved) and which check found it
	 *
	 * The chat message announcing a new collection log item is delivered BEFORE the item actually
	 * appears in the player's inventory - the inventory update arrives as a separate, later
	 * ItemContainerChanged event (the same ordering the "Collection Log" RuneLite plugin has to work
	 * around). So a same-name lookup against the live inventory at call time will almost always miss
	 * for a genuinely new item. Resolution is therefore deferred: take a snapshot of the inventory now,
	 * then wait for it to change and diff against that snapshot to find the newly-appeared id. If
	 * nothing shows up in the inventory by the end of the current tick, fall back to a GE search
	 * (tradeable items only).
	 */
	public void resolveIdByName(String itemName, ResolveCallback callback)
	{
		Integer petItemId = PetItems.idForName(itemName);
		if (petItemId != null)
		{
			callback.accept(petItemId, Source.PET);
			return;
		}

		if (pendingItemName != null)
		{
			// A second lookup started before the first tick-fallback fired - flush the first one now
			// rather than silently dropping its callback.
			int id = resolveTradeableIdByExactName(pendingItemName);
			deliverPending(id, id >= 0 ? Source.GE_SEARCH : Source.UNRESOLVED);
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
