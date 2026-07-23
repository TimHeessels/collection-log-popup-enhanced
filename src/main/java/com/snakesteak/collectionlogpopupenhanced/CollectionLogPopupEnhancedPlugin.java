package com.snakesteak.collectionlogpopupenhanced;

import com.google.inject.Provides;
import com.snakesteak.collectionlogpopupenhanced.droprate.DropRateResolver;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountTracker;
import com.snakesteak.collectionlogpopupenhanced.killcount.TrackedKillCountManager;
import com.snakesteak.collectionlogpopupenhanced.overlay.CollectionLogOverlay;
import com.snakesteak.collectionlogpopupenhanced.rarity.ItemIdResolver;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityResolver;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityResult;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Collection Log Popup Enhanced"
)
public class CollectionLogPopupEnhancedPlugin extends Plugin
{
	private static final Pattern NEW_COLLECTION_LOG_ITEM = Pattern.compile("New item added to your collection log: (.*)");

	// Values of VarbitID.OPTION_COLLECTION_NEW_ITEM that suppress the chat message this plugin relies on
	// (off, and popup-only) - detection silently does nothing for these settings without this warning.
	private static final Set<Integer> COLLECTION_LOG_SETTING_VALUES_WITHOUT_CHAT_MESSAGE = Set.of(0, 2);

	private static final int SETTING_WARNING_THROTTLE_TICKS = 16;

	// Dev-only manual trigger: "::clogtest [count]" in the chatbox, picks `count` distinct random item
	// ids from the rarity dataset (default 1). "::clogtest <item name>" instead runs that exact name
	// through the real detection pipeline (ItemIdResolver + RarityResolver) as if it were a genuine
	// collection log chat message - useful for testing against ordinary loot (e.g. goblin drops) that
	// has no rarity data; RarityResolver falls back to COMMON for anything not in the dataset.
	// CommandExecuted only fires when the client is launched with --developer-mode (as the gradle "run"
	// task already does), so this can't be invoked by regular players on a hub-installed build.
	private static final String TEST_COMMAND = "clogtest";

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private RarityResolver rarityResolver;

	@Inject
	private ItemIdResolver itemIdResolver;

	@Inject
	private KillCountTracker killCountTracker;

	@Inject
	private TrackedKillCountManager trackedKillCountManager;

	@Inject
	private DropRateResolver dropRateResolver;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CollectionLogOverlay collectionLogOverlay;

	private int lastSettingWarningTick = -1;

	@Override
	protected void startUp() throws Exception
	{
		eventBus.register(itemIdResolver);
		eventBus.register(killCountTracker);
		trackedKillCountManager.startUp();
		eventBus.register(trackedKillCountManager);
		overlayManager.add(collectionLogOverlay);
		log.debug("Collection Log Popup Enhanced started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(itemIdResolver);
		eventBus.unregister(killCountTracker);
		eventBus.unregister(trackedKillCountManager);
		trackedKillCountManager.shutDown();
		overlayManager.remove(collectionLogOverlay);
		collectionLogOverlay.clear();
		log.debug("Collection Log Popup Enhanced stopped!");
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		Matcher matcher = NEW_COLLECTION_LOG_ITEM.matcher(chatMessage.getMessage());
		if (matcher.matches())
		{
			String itemName = Text.removeTags(matcher.group(1));
			handleNewCollectionLogItem(null, itemName);
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted)
	{
		if (!TEST_COMMAND.equalsIgnoreCase(commandExecuted.getCommand()))
		{
			return;
		}

		String[] args = commandExecuted.getArguments();
		if (args.length == 0)
		{
			testRandomDatasetItems(1);
			return;
		}

		if (args.length == 1)
		{
			try
			{
				int count = Integer.parseInt(args[0]);
				if (count <= 0)
				{
					log.debug("Count must be positive.");
					return;
				}
				testRandomDatasetItems(count);
				return;
			}
			catch (NumberFormatException e)
			{
				// Not a count - fall through and treat it as an item name instead.
			}
		}

		String itemName = String.join(" ", args);
		handleNewCollectionLogItem(null, itemName);
	}

	private void testRandomDatasetItems(int count)
	{
		List<Integer> itemIds = rarityResolver.randomItemIds(count);
		if (itemIds.isEmpty())
		{
			log.debug("No items available to test with - rarity dataset failed to load.");
			return;
		}
		if (itemIds.size() < count)
		{
			log.debug("Only {} distinct items available - showing all of them.", itemIds.size());
		}

		for (int itemId : itemIds)
		{
			int canonicalId = itemManager.canonicalize(itemId);
			String itemName = itemManager.getItemComposition(canonicalId).getName();
			handleNewCollectionLogItem(canonicalId, itemName);
		}
	}

	private void handleNewCollectionLogItem(Integer knownItemId, String itemName)
	{
		if (knownItemId != null)
		{
			handleResolvedItem(knownItemId, itemName, "known");
			return;
		}

		// Resolution is asynchronous - the id may not be known until a later inventory update or
		// even the end of the current tick, see ItemIdResolver.resolveIdByName javadoc.
		itemIdResolver.resolveIdByName(itemName, (itemId, source) -> handleResolvedItem(itemId, itemName, source.toString()));
	}

	private void handleResolvedItem(int itemId, String itemName, String resolvedVia)
	{
		RarityResult result = rarityResolver.resolve(itemId, itemName);
		// Read now, right before the item is displayed, rather than back when the collection log
		// chat message first arrived - resolution can be deferred by a tick or more (see
		// ItemIdResolver), and reading it here also covers the (typical) case where the kill count
		// message hasn't been processed yet at the moment the collection log message is.
		// The official kill count (from an in-game "kill count" chat message) always takes
		// priority over the plugin's own tracked-since count for non-KC-message monsters - never
		// mix the two, and only fall back to the tracked one when there's no official kill.
		KillCountTracker.RecentKill kill = killCountTracker.recentKill();
		boolean killCountIsTracked = false;
		if (kill == null)
		{
			kill = trackedKillCountManager.recentKill();
			killCountIsTracked = kill != null;
		}

		Integer killCount = kill != null ? kill.getKillCount() : null;
		Double dropProbability = kill != null ? dropRateResolver.dropProbability(kill.getSource(), itemName) : null;

		log.debug("New collection log item '{}' (id {}, resolved via {}) resolved to {} (kill count {}, tracked {}, drop probability {})",
			itemName, itemId, resolvedVia, result, killCount, killCountIsTracked, dropProbability);

		collectionLogOverlay.enqueue(itemName, result.getItemId(), result.getTier(), result.getPrice(), result.isHighAlch(),
			result.getCompPercent(), killCount, killCountIsTracked, dropProbability);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (varbitChanged.getVarbitId() != VarbitID.OPTION_COLLECTION_NEW_ITEM)
		{
			return;
		}

		if (!COLLECTION_LOG_SETTING_VALUES_WITHOUT_CHAT_MESSAGE.contains(varbitChanged.getValue()))
		{
			return;
		}

		if (lastSettingWarningTick != -1 && client.getTickCount() - lastSettingWarningTick <= SETTING_WARNING_THROTTLE_TICKS)
		{
			return;
		}
		lastSettingWarningTick = client.getTickCount();

		log.debug("Please enable the chat message option for \"Collection log - New addition notification\" in your game settings for Collection Log Popup Enhanced to detect new unlocks!");
	}

	@Provides
	CollectionLogPopupEnhancedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectionLogPopupEnhancedConfig.class);
	}
}
