package com.snakesteak.collectionlogpopupenhanced;

import com.google.inject.Provides;
import com.snakesteak.collectionlogpopupenhanced.droprate.DropRateResolver;
import com.snakesteak.collectionlogpopupenhanced.droprate.RemoteDropRateUpdater;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountTracker;
import com.snakesteak.collectionlogpopupenhanced.overlay.CollectionLogOverlay;
import com.snakesteak.collectionlogpopupenhanced.progress.CollectionLogProgressTracker;
import com.snakesteak.collectionlogpopupenhanced.rarity.ItemIdResolver;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityResolver;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityResult;
import com.snakesteak.collectionlogpopupenhanced.rarity.RemoteRarityOverridesUpdater;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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

	private static final String CONFIG_GROUP = "collection-log-popup-enhanced";

	// Dev-only: "::clogtest [count]" shows random items from the rarity dataset; "::clogtest <item
	// name> [kc]" runs a specific name through the real detection pipeline, optionally forcing a kill
	// count. Only usable in --developer-mode (the gradle "run" task), not on a hub-installed build.
	private static final String TEST_COMMAND = "clogtest";

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
	private RemoteDropRateUpdater remoteDropRateUpdater;

	@Inject
	private RemoteRarityOverridesUpdater remoteRarityOverridesUpdater;

	@Inject
	private CollectionLogProgressTracker progressTracker;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CollectionLogOverlay collectionLogOverlay;

	@Inject
	private CollectionLogPopupEnhancedConfig config;

	@Inject
	private ConfigManager configManager;

	@Override
	protected void startUp() throws Exception
	{
		eventBus.register(itemIdResolver);
		eventBus.register(killCountTracker);
		eventBus.register(progressTracker);
		overlayManager.add(collectionLogOverlay);
		remoteDropRateUpdater.startUp();
		remoteRarityOverridesUpdater.startUp();
		log.debug("Collection Log Popup Enhanced started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(itemIdResolver);
		eventBus.unregister(killCountTracker);
		eventBus.unregister(progressTracker);
		overlayManager.remove(collectionLogOverlay);
		collectionLogOverlay.clear();
		remoteDropRateUpdater.shutDown();
		remoteRarityOverridesUpdater.shutDown();
		log.debug("Collection Log Popup Enhanced stopped!");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!CONFIG_GROUP.equals(configChanged.getGroup()) || !"resyncPageProgress".equals(configChanged.getKey())
			|| !config.resyncPageProgress())
		{
			return;
		}

		progressTracker.triggerFullResync();
		configManager.setConfiguration(CONFIG_GROUP, "resyncPageProgress", false);
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

		// A non-negative last token is a forced kill count; everything before it is the item name.
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

		// Resolution is asynchronous - see ItemIdResolver.resolveIdByName javadoc.
		itemIdResolver.resolveIdByName(itemName, (itemId, source) -> handleResolvedItem(itemId, itemName, source.toString(), forcedKillCount));
	}

	private void handleResolvedItem(int itemId, String itemName, String resolvedVia, Integer forcedKillCount)
	{
		RarityResult result = rarityResolver.resolve(itemId, itemName);

		Integer killCount;
		String source;
		if (forcedKillCount != null)
		{
			// Dev-only test override (see TEST_COMMAND) - bypasses the real correlated kill, so
			// there's no known source either.
			killCount = forcedKillCount;
			source = null;
		}
		else
		{
			// Read now, right before display, rather than when the chat message first arrived -
			// resolution can be deferred by a tick or more (see ItemIdResolver).
			KillCountTracker.RecentKill kill = killCountTracker.recentKill();

			killCount = kill != null ? kill.getKillCount() : null;
			source = kill != null ? kill.getSource() : null;
		}

		Double dropProbability = source != null
			? dropRateResolver.dropProbability(source, itemName)
			: dropRateResolver.dropProbabilityByItemName(itemName);
		// Without a known source, a drop from more than one tracked source has no single rate to show -
		// collect every candidate instead so the overlay can display them all (see CollectionLogOverlay).
		List<DropRateResolver.SourceRate> ambiguousDropRates = source == null && dropProbability == null
			? dropRateResolver.dropRatesByItemName(itemName)
			: List.of();

		CollectionLogProgressTracker.PageProgress pageProgress = null;
		if (source != null)
		{
			progressTracker.recordObtained(result.getItemId());
			pageProgress = progressTracker.progressFor(source);
		}

		log.debug("New collection log item '{}' (id {}, resolved via {}) resolved to {} (kill count {}, drop probability {}, ambiguous rates {}, page progress {})",
			itemName, itemId, resolvedVia, result, killCount, dropProbability, ambiguousDropRates, pageProgress);

		collectionLogOverlay.enqueue(itemName, result.getItemId(), result.getTier(), result.getPrice(), result.isHighAlch(),
			result.getAlchPrice(), result.getCompPercent(), killCount, dropProbability, ambiguousDropRates, source, pageProgress);
	}

	@Provides
	CollectionLogPopupEnhancedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectionLogPopupEnhancedConfig.class);
	}
}
