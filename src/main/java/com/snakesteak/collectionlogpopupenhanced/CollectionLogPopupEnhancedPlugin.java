package com.snakesteak.collectionlogpopupenhanced;

import com.google.inject.Provides;
import com.snakesteak.collectionlogpopupenhanced.droprate.DropRateResolver;
import com.snakesteak.collectionlogpopupenhanced.droprate.LocalDropRateDatasetLoader;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountKind;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountTracker;
import com.snakesteak.collectionlogpopupenhanced.overlay.CollectionLogOverlay;
import com.snakesteak.collectionlogpopupenhanced.rarity.ItemIdResolver;
import com.snakesteak.collectionlogpopupenhanced.rarity.LocalRarityDatasetLoader;
import com.snakesteak.collectionlogpopupenhanced.rarity.PreviewTier;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityResolver;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityResult;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
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

	// Dev-only: "::clogtest [count]" shows random items from the rarity dataset; "::clogtest <item
	// name>" runs a specific name through the real detection pipeline, kill count correlation
	// included. Only usable in --developer-mode (the gradle "run" task), not on a hub-installed build.
	private static final String TEST_COMMAND = "clogtest";

	// Matched against VarClientID.NOTIFICATION_TITLE to tell our notification apart from the combat
	// task and league task ones that share the same widget. Same string ScreenshotPlugin matches on.
	private static final String COLLECTION_LOG_NOTIFICATION_TITLE = "Collection log";

	// The notification's painted widgets. UNIVERSE, CONTAINER and CONTENT are excluded on purpose -
	// the open animation resizes those, and hiding one stalls it before the screenshot fires.
	private static final int[] NATIVE_POPUP_PAINT_COMPONENTS = {
		InterfaceID.NotificationDisplay.BACKGROUND,
		InterfaceID.NotificationDisplay.FRAME,
		InterfaceID.NotificationDisplay.TITLE,
		InterfaceID.NotificationDisplay.TITLE_TEXT,
		InterfaceID.NotificationDisplay.MAIN,
		InterfaceID.NotificationDisplay.MAIN_TEXT,
	};

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
	private LocalDropRateDatasetLoader localDropRateDatasetLoader;

	@Inject
	private LocalRarityDatasetLoader localRarityDatasetLoader;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CollectionLogOverlay collectionLogOverlay;

	@Inject
	private CollectionLogPopupEnhancedConfig config;

	private PreviewTier previousPreviewTier = PreviewTier.NONE;

	@Override
	protected void startUp() throws Exception
	{
		eventBus.register(itemIdResolver);
		eventBus.register(killCountTracker);
		overlayManager.add(collectionLogOverlay);
		localDropRateDatasetLoader.load();
		localRarityDatasetLoader.load();
		log.debug("Collection Log Popup Enhanced started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(itemIdResolver);
		eventBus.unregister(killCountTracker);
		// Unregistering stops new messages, but the tracker is a @Singleton - Guice returns this same
		// instance when the plugin is re-enabled, so the stored count has to be dropped explicitly.
		killCountTracker.reset();
		overlayManager.remove(collectionLogOverlay);
		collectionLogOverlay.clear();
		// Only ever undoes this plugin's own hide - never something the game hid.
		setNativePopupPaintHidden(false);
		log.debug("Collection Log Popup Enhanced stopped!");
	}

	/**
	 * Hides the game's own collection log popup. Reasserted every frame - the notification is rebuilt
	 * and resized throughout its open animation, which undoes a one-shot hide.
	 * <p>See "This Plugin: Native Popup & Screenshots" in AGENTS.md before changing what is hidden or
	 * touching the game's popup setting - both silently break users' screenshots.
	 */
	@Subscribe
	public void onBeforeRender(BeforeRender beforeRender)
	{
		if (!COLLECTION_LOG_NOTIFICATION_TITLE.equalsIgnoreCase(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE)))
		{
			return;
		}

		setNativePopupPaintHidden(true);
	}

	/**
	 * Dynamic children are toggled too - the frame is drawn as eight of them (its corners and edges),
	 * which keep painting on their own when only the parent is hidden.
	 */
	private void setNativePopupPaintHidden(boolean hidden)
	{
		for (int componentId : NATIVE_POPUP_PAINT_COMPONENTS)
		{
			Widget widget = client.getWidget(componentId);
			if (widget == null)
			{
				continue;
			}

			widget.setHidden(hidden);

			Widget[] children = widget.getDynamicChildren();
			if (children != null)
			{
				for (Widget child : children)
				{
					if (child != null)
					{
						child.setHidden(hidden);
					}
				}
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		PreviewTier previewTier = config.previewTier();
		if (previewTier != previousPreviewTier)
		{
			// Dismiss whatever's showing/queued so the next state starts clean. A held preview item
			// also carries a stale notificationStartMillis, so its hold/fade timer (see
			// CollectionLogOverlay#advance) would otherwise resume from an unpredictable point.
			collectionLogOverlay.clear();
		}
		previousPreviewTier = previewTier;

		// Fires once when preview mode is enabled; the overlay then holds the item indefinitely (see
		// CollectionLogOverlay#advance), so this doesn't re-fire until preview is toggled or
		// switched. Same pipeline as "::clogtest", driven by config so any user can preview.
		if (previewTier != PreviewTier.NONE && collectionLogOverlay.isIdle())
		{
			if (previewTier == PreviewTier.RANDOM)
			{
				testRandomDatasetItems(1);
			}
			else
			{
				testTierPreviewItem(previewTier);
			}
		}
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

		// Every token is part of the item name, trailing digits included - the log is full of names
		// like "Saradomin page 1", and a trailing number was once read as a kill count override,
		// which silently truncated them to an item that doesn't exist.
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

	private void testTierPreviewItem(PreviewTier tier)
	{
		Integer itemId = rarityResolver.randomItemIdForTier(tier);
		if (itemId == null)
		{
			log.debug("No {} tier items available to preview - rarity dataset failed to load, or no item of that tier exists.", tier);
			return;
		}

		int canonicalId = itemManager.canonicalize(itemId);
		String itemName = itemManager.getItemComposition(canonicalId).getName();
		handleNewCollectionLogItem(canonicalId, itemName);
	}

	private void handleNewCollectionLogItem(Integer knownItemId, String itemName)
	{
		if (knownItemId != null)
		{
			handleResolvedItem(knownItemId, itemName, "known");
			return;
		}

		// Resolution is asynchronous - see ItemIdResolver.resolveIdByName javadoc.
		itemIdResolver.resolveIdByName(itemName, (itemId, source) -> handleResolvedItem(itemId, itemName, source.toString()));
	}

	private void handleResolvedItem(int itemId, String itemName, String resolvedVia)
	{
		RarityResult result = rarityResolver.resolve(itemId, itemName);

		// Read now, right before display, rather than when the chat message first arrived -
		// resolution can be deferred by a tick or more (see ItemIdResolver).
		List<String> candidateSources = rarityResolver.tabsForItemName(itemName);
		KillCountTracker.RecentKill kill = killCountTracker.killCountFor(candidateSources);

		Integer killCount = kill != null ? kill.getKillCount() : null;
		KillCountKind killCountKind = kill != null ? kill.getKind() : null;
		String source = kill != null ? kill.getSource() : null;

		// The drop rate dataset's source names don't always agree with the kill count's source name
		// (e.g. Barrows' kill count source is "Barrows chest", but its drop rate source is "Chest
		// (Barrows)") - so a source-scoped miss falls back to the item-name-wide lookup rather than
		// giving up, same as when there's no known source at all.
		Double dropProbability = source != null ? dropRateResolver.dropProbability(source, itemName) : null;
		if (dropProbability == null)
		{
			dropProbability = dropRateResolver.dropProbabilityByItemName(itemName);
		}
		// A drop from more than one tracked source has no single rate to show - collect every
		// candidate instead so the overlay can display them all (see CollectionLogOverlay).
		List<DropRateResolver.SourceRate> ambiguousDropRates = dropProbability == null
			? dropRateResolver.dropRatesByItemName(itemName)
			: List.of();

		log.debug("New collection log item '{}' (id {}, resolved via {}) resolved to {} (kill count {} {}, drop probability {}, ambiguous rates {})",
			itemName, itemId, resolvedVia, result, killCount, killCountKind, dropProbability, ambiguousDropRates);

		collectionLogOverlay.enqueue(itemName, result.getItemId(), result.getTier(), result.getPrice(), result.isHighAlch(),
			result.getAlchPrice(), result.getCompPercent(), killCount, killCountKind, source, dropProbability, ambiguousDropRates);
	}

	@Provides
	CollectionLogPopupEnhancedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectionLogPopupEnhancedConfig.class);
	}
}
