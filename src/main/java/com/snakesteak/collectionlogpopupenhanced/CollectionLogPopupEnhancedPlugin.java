package com.snakesteak.collectionlogpopupenhanced;

import com.google.inject.Provides;
import com.snakesteak.collectionlogpopupenhanced.droprate.DropRateResolver;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountTracker;
import com.snakesteak.collectionlogpopupenhanced.overlay.CollectionLogOverlay;
import com.snakesteak.collectionlogpopupenhanced.rarity.ItemIdResolver;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityResolver;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityResult;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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

	private static final String SETTING_WARNING_MESSAGE = "Collection Log Popup Enhanced: enable the \"Chat message\" "
		+ "option for Collection log - New addition notification (Settings > All Settings) so new unlocks can be detected.";

	// Dev-only manual trigger: "::clogtest [count]" in the chatbox, picks `count` distinct random item
	// ids from the rarity dataset (default 1). "::clogtest <item name>" instead runs that exact name
	// through the real detection pipeline (ItemIdResolver + RarityResolver) as if it were a genuine
	// collection log chat message - useful for testing against ordinary loot (e.g. goblin drops) that
	// has no rarity data; RarityResolver falls back to COMMON for anything not in the dataset. Reads
	// the real (likely absent) correlated kill for kill count/drop rate, same as a genuine unlock -
	// "::clogtest <item name> <kc>" instead forces that exact kill count, bypassing the real
	// correlated kill entirely (so there's no known source either - drop rate always comes from
	// DropRateResolver.dropProbabilityByItemName, same as the real "no correlated kill" case), for
	// testing how kill count/drop rate render at a specific kc/rate combination without needing a
	// real kill.
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
	private DropRateResolver dropRateResolver;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CollectionLogOverlay collectionLogOverlay;

	private int lastSettingWarningTick = -1;
	private boolean pendingLoginSettingCheck = false;

	@Override
	protected void startUp() throws Exception
	{
		eventBus.register(itemIdResolver);
		eventBus.register(killCountTracker);
		overlayManager.add(collectionLogOverlay);
		log.debug("Collection Log Popup Enhanced started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(itemIdResolver);
		eventBus.unregister(killCountTracker);
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
			handleNewCollectionLogItem(null, itemName, null);
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

		// If the last token is a non-negative integer, it's a forced kill count (see TEST_COMMAND
		// javadoc) and everything before it is the item name; otherwise the whole thing is the item
		// name (existing behavior, no forced kill count).
		Integer forcedKillCount = null;
		int nameArgCount = args.length;
		if (args.length >= 2)
		{
			try
			{
				int kc = Integer.parseInt(args[args.length - 1]);
				if (kc >= 0)
				{
					forcedKillCount = kc;
					nameArgCount = args.length - 1;
				}
			}
			catch (NumberFormatException e)
			{
				// Last token isn't a kill count - the whole thing is the item name.
			}
		}

		String itemName = String.join(" ", Arrays.copyOfRange(args, 0, nameArgCount));
		handleNewCollectionLogItem(null, itemName, forcedKillCount);
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
			handleNewCollectionLogItem(canonicalId, itemName, null);
		}
	}

	private void handleNewCollectionLogItem(Integer knownItemId, String itemName, Integer forcedKillCount)
	{
		if (knownItemId != null)
		{
			handleResolvedItem(knownItemId, itemName, "known", forcedKillCount);
			return;
		}

		// Resolution is asynchronous - the id may not be known until a later inventory update or
		// even the end of the current tick, see ItemIdResolver.resolveIdByName javadoc.
		itemIdResolver.resolveIdByName(itemName, (itemId, source) -> handleResolvedItem(itemId, itemName, source.toString(), forcedKillCount));
	}

	private void handleResolvedItem(int itemId, String itemName, String resolvedVia, Integer forcedKillCount)
	{
		RarityResult result = rarityResolver.resolve(itemId, itemName);

		Integer killCount;
		String source;
		if (forcedKillCount != null)
		{
			// Dev-only test override (see TEST_COMMAND) - bypasses the real correlated kill
			// entirely, so there's no known source either; use the same source-agnostic lookup the
			// real "no correlated kill" path below uses.
			killCount = forcedKillCount;
			source = null;
		}
		else
		{
			// Read now, right before the item is displayed, rather than back when the collection log
			// chat message first arrived - resolution can be deferred by a tick or more (see
			// ItemIdResolver), and reading it here also covers the (typical) case where the kill count
			// message hasn't been processed yet at the moment the collection log message is.
			KillCountTracker.RecentKill kill = killCountTracker.recentKill();

			killCount = kill != null ? kill.getKillCount() : null;
			source = kill != null ? kill.getSource() : null;
		}

		Double dropProbability = source != null
			? dropRateResolver.dropProbability(source, itemName)
			: dropRateResolver.dropProbabilityByItemName(itemName);
		// Without a known source, an item that's a notable drop from more than one tracked source
		// has no single rate to show - collect every candidate instead so the overlay can display
		// them all (see CollectionLogOverlay). Not attempted when a source is known, since a miss
		// there just means "no known rate from this source", not ambiguity.
		List<DropRateResolver.SourceRate> ambiguousDropRates = source == null && dropProbability == null
			? dropRateResolver.dropRatesByItemName(itemName)
			: List.of();

		log.debug("New collection log item '{}' (id {}, resolved via {}) resolved to {} (kill count {}, drop probability {}, ambiguous rates {})",
			itemName, itemId, resolvedVia, result, killCount, dropProbability, ambiguousDropRates);

		collectionLogOverlay.enqueue(itemName, result.getItemId(), result.getTier(), result.getPrice(), result.isHighAlch(),
			result.getAlchPrice(), result.getCompPercent(), killCount, dropProbability, ambiguousDropRates);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (varbitChanged.getVarbitId() != VarbitID.OPTION_COLLECTION_NEW_ITEM)
		{
			return;
		}

		warnIfSettingBreaksDetection(varbitChanged.getValue());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		// Account settings (like this varbit) aren't necessarily synced from the server yet at the
		// moment this event fires, so defer the read to the next tick rather than risk reading a
		// stale default value here.
		pendingLoginSettingCheck = true;
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (!pendingLoginSettingCheck)
		{
			return;
		}
		pendingLoginSettingCheck = false;

		warnIfSettingBreaksDetection(client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM));
	}

	private void warnIfSettingBreaksDetection(int settingValue)
	{
		if (!COLLECTION_LOG_SETTING_VALUES_WITHOUT_CHAT_MESSAGE.contains(settingValue))
		{
			return;
		}

		if (lastSettingWarningTick != -1 && client.getTickCount() - lastSettingWarningTick <= SETTING_WARNING_THROTTLE_TICKS)
		{
			return;
		}
		lastSettingWarningTick = client.getTickCount();

		client.addChatMessage(ChatMessageType.CONSOLE, "", SETTING_WARNING_MESSAGE, null);
	}

	@Provides
	CollectionLogPopupEnhancedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectionLogPopupEnhancedConfig.class);
	}
}
