package com.snakesteak.collectionlogpopupenhanced;

import com.google.inject.Provides;
import com.snakesteak.collectionlogpopupenhanced.cox.CoxChatCensor;
import com.snakesteak.collectionlogpopupenhanced.cox.CoxChatCensorMode;
import com.snakesteak.collectionlogpopupenhanced.cox.CoxLootParser;
import com.snakesteak.collectionlogpopupenhanced.cox.CoxScreenshot;
import com.snakesteak.collectionlogpopupenhanced.droprate.DropRateResolver;
import com.snakesteak.collectionlogpopupenhanced.droprate.LocalDropRateDatasetLoader;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountKind;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountTracker;
import com.snakesteak.collectionlogpopupenhanced.overlay.CollectionLogOverlay;
import com.snakesteak.collectionlogpopupenhanced.overlay.PanelStyle;
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
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
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
	// Dev-only, for testing the CoX delay without a raid: "::coxsim <item> [player]" posts the chat
	// lines a raid end produces, "::coxopen" does what opening the chest does. See TESTING-COX.md.
	private static final String COX_SIMULATE_COMMAND = "coxsim";
	private static final String COX_OPEN_COMMAND = "coxopen";
	private static final String CONFIG_GROUP = "collection-log-popup-enhanced";

	// Matched against VarClientID.NOTIFICATION_TITLE to tell our notification apart from the combat
	// task and league task ones that share the same widget. Same string ScreenshotPlugin matches on.
	private static final String COLLECTION_LOG_NOTIFICATION_TITLE = "Collection log";

	// Swapped in for the notification title while a held CoX popup is on screen, so the Screenshot
	// plugin - which matches the title to "Collection log" - skips it. See onScriptPreFired.
	private static final String SPOOFED_NOTIFICATION_TITLE = " ";

	// VarbitID.OPTION_COLLECTION_NEW_ITEM value for "chat message only" - the Screenshot plugin then
	// screenshots from the chat line instead of the popup, which happens at raid end regardless.
	private static final int COLLECTION_NEW_ITEM_CHAT_ONLY = 1;

	private static final String COX_CENSOR_PLUGIN_CLASS = "com.coxspecialloothider.CoxSpecialLootHiderPlugin";
	private static final String COX_CENSOR_CONFLICT_WARNING = "<col=ff0000>Collection Log Popup Enhanced: please turn off"
		+ " the CoX Censor plugin - this plugin's CoX settings replace it, and using both shows the popup and takes"
		+ " the screenshot twice.</col>";

	// Opening any of these reveals the raid's loot, so a held popup is no longer a spoiler. Private
	// storage and the bank cover leaving the raid without looting the chest.
	private static final int[] COX_LOOT_REVEAL_INTERFACES = {
		InterfaceID.RAIDS_REWARDS,
		InterfaceID.RAIDS_STORAGE_PRIVATE,
		InterfaceID.BANKMAIN,
	};

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
	private ClientThread clientThread;

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

	@Inject
	private CoxChatCensor coxChatCensor;

	@Inject
	private CoxScreenshot coxScreenshot;

	@Inject
	private PluginManager pluginManager;

	private PreviewTier previousPreviewTier = PreviewTier.NONE;

	// The real notification title while it is swapped for SPOOFED_NOTIFICATION_TITLE, else null.
	private String spoofedNotificationTitle;

	@Override
	protected void startUp() throws Exception
	{
		eventBus.register(itemIdResolver);
		eventBus.register(killCountTracker);
		eventBus.register(coxChatCensor);
		collectionLogOverlay.setReleasedFullyOpenListener(this::onReleasedPopupFullyOpen);
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
		eventBus.unregister(coxChatCensor);
		coxChatCensor.reveal();
		restoreNotificationTitle();
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
		// A held CoX popup is hidden in every style, Audio only included - showing it would give the
		// loot away before the chest.
		if (isHeldCoxNotification())
		{
			setNativePopupPaintHidden(true);
			return;
		}

		if (config.panelStyle() == PanelStyle.AUDIO_ONLY)
		{
			return;
		}

		if (!COLLECTION_LOG_NOTIFICATION_TITLE.equalsIgnoreCase(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE)))
		{
			return;
		}

		setNativePopupPaintHidden(true);
	}

	/**
	 * Switching to Audio only mid-popup would otherwise leave the game's popup hidden until it closes.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!CONFIG_GROUP.equals(configChanged.getGroup()))
		{
			return;
		}

		if ("panelStyle".equals(configChanged.getKey()) && config.panelStyle() == PanelStyle.AUDIO_ONLY)
		{
			clientThread.invoke(() -> setNativePopupPaintHidden(false));
		}
		else if ("coxChatCensor".equals(configChanged.getKey()))
		{
			// Rebuilt so lines already in the chatbox follow the new setting straight away.
			clientThread.invoke(client::refreshChat);
		}

		if (isCoxSettingTurnedOn(configChanged) && isCoxCensorEnabled())
		{
			clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				COX_CENSOR_CONFLICT_WARNING, null));
		}
	}

	/**
	 * Only switching one on from off counts - "Only mine" to "Everyone's" was already on.
	 */
	private static boolean isCoxSettingTurnedOn(ConfigChanged configChanged)
	{
		switch (configChanged.getKey())
		{
			case "delayCoxPopupUntilChest":
				return Boolean.parseBoolean(configChanged.getNewValue());
			case "coxChatCensor":
				boolean wasOff = configChanged.getOldValue() == null || CoxChatCensorMode.OFF.name().equals(configChanged.getOldValue());
				return wasOff && !CoxChatCensorMode.OFF.name().equals(configChanged.getNewValue());
			default:
				return false;
		}
	}

	/**
	 * CoX Censor (cox-special-loot-hider) does what our CoX settings do, and with both on the popup
	 * shows and the screenshot is taken twice. False when it isn't installed.
	 */
	private boolean isCoxCensorEnabled()
	{
		return pluginManager.getPlugins().stream()
			.filter(plugin -> COX_CENSOR_PLUGIN_CLASS.equals(plugin.getClass().getName()))
			.anyMatch(pluginManager::isPluginEnabled);
	}

	/**
	 * Swaps the notification title while a held CoX popup is in its delay stage, which is when the
	 * Screenshot plugin reads it. Priority 1 runs this ahead of that plugin, so it sees the swapped
	 * title and skips the screenshot - ours is taken at the chest instead (see
	 * #onReleasedPopupFullyOpen). onScriptPostFired puts the title back straight after.
	 */
	@Subscribe(priority = 1)
	public void onScriptPreFired(ScriptPreFired scriptPreFired)
	{
		if (scriptPreFired.getScriptId() != ScriptID.NOTIFICATION_DELAY || spoofedNotificationTitle != null
			|| !isHeldCoxNotification())
		{
			return;
		}

		spoofedNotificationTitle = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
		client.setVarcStrValue(VarClientID.NOTIFICATION_TITLE, SPOOFED_NOTIFICATION_TITLE);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired)
	{
		if (scriptPostFired.getScriptId() == ScriptID.NOTIFICATION_DELAY)
		{
			restoreNotificationTitle();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState state = gameStateChanged.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			restoreNotificationTitle();
			coxChatCensor.reveal();
		}
	}

	/**
	 * @return true if the game's popup on screen is a CoX purple that ours is holding back until the
	 *         chest
	 */
	private boolean isHeldCoxNotification()
	{
		if (!config.delayCoxPopupUntilChest())
		{
			return false;
		}

		String title = spoofedNotificationTitle != null
			? spoofedNotificationTitle
			: client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
		return COLLECTION_LOG_NOTIFICATION_TITLE.equalsIgnoreCase(title)
			&& CoxLootParser.notificationUnique(client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN)) != null;
	}

	private void restoreNotificationTitle()
	{
		if (spoofedNotificationTitle == null)
		{
			return;
		}
		client.setVarcStrValue(VarClientID.NOTIFICATION_TITLE, spoofedNotificationTitle);
		spoofedNotificationTitle = null;
	}

	/**
	 * Stands in for the Screenshot plugin's collection log screenshot that was skipped at raid end.
	 * With the game set to "chat message only" that plugin took its own from the chat line instead,
	 * so another one here would only be a duplicate.
	 */
	private void onReleasedPopupFullyOpen(String itemName)
	{
		if (client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) == COLLECTION_NEW_ITEM_CHAT_ONLY)
		{
			return;
		}
		coxScreenshot.capture(itemName);
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

	/**
	 * Releases popups held by the "delay CoX popups until chest" option. A held item is deliberately
	 * session-only: nothing re-shows it after a client restart, which is accepted rather than worth
	 * a logout-release path, since the chest is opened within a minute or two in practice.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		for (int interfaceId : COX_LOOT_REVEAL_INTERFACES)
		{
			if (widgetLoaded.getGroupId() == interfaceId)
			{
				revealCoxLoot();
				return;
			}
		}
	}

	private void revealCoxLoot()
	{
		collectionLogOverlay.releaseHeld();
		coxChatCensor.reveal();
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
		if (COX_SIMULATE_COMMAND.equalsIgnoreCase(commandExecuted.getCommand()))
		{
			simulateCoxLoot(String.join(" ", commandExecuted.getArguments()));
			return;
		}
		if (COX_OPEN_COMMAND.equalsIgnoreCase(commandExecuted.getCommand()))
		{
			revealCoxLoot();
			return;
		}
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

	/**
	 * Posts the chat lines a CoX completion produces, through the same handlers as the real ones.
	 * "::coxsim Twisted bow" is your own drop; "::coxsim Twisted bow Some Player" is a teammate's,
	 * which only gets the party and clan broadcasts - the game messages are only ever your own.
	 */
	private void simulateCoxLoot(String arguments)
	{
		String item = CoxLootParser.findUnique(arguments);
		if (item == null)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"Usage: ::coxsim <CoX unique> [player], e.g. ::coxsim Twisted bow", null);
			return;
		}

		String localName = client.getLocalPlayer() != null ? Text.removeTags(client.getLocalPlayer().getName()) : "You";
		String otherName = arguments.substring(arguments.indexOf(item) + item.length()).trim();
		String player = otherName.isEmpty() ? localName : otherName;

		client.addChatMessage(ChatMessageType.FRIENDSCHATNOTIFICATION, "",
			"<col=ef20ff>" + player + " - </col><col=ff0000>" + item + "</col>", null);
		client.addChatMessage(ChatMessageType.CLAN_MESSAGE, "",
			player + " received special loot from a raid: " + item + " (1,234,567 coins)", null);
		if (player.equalsIgnoreCase(localName))
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=ef1020>Valuable drop: " + item + " (1,234,567 coins)</col>", null);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"New item added to your collection log: " + item, null);
		}
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
		// The source-scoped lookup carries the " (max)" twin's rate too where there is one, so a
		// scaling drop shows both ends rather than just the base (see DropRateResolver.SourceRate).
		DropRateResolver.SourceRate sourceRate = source != null ? dropRateResolver.dropRateFor(source, itemName) : null;
		if (sourceRate == null)
		{
			sourceRate = dropRateResolver.soleSourceRate(itemName);
		}
		Double dropProbability = sourceRate != null && sourceRate.getMaxProbability() == null
			? sourceRate.getProbability()
			: null;
		// A drop from more than one tracked source has no single rate to show - collect every
		// candidate instead so the overlay can display them all (see CollectionLogOverlay).
		// A single source with a rate range lands here too, not just a drop from several sources -
		// both render as a range, so the overlay needs no notion of which case it is.
		List<DropRateResolver.SourceRate> ambiguousDropRates = dropProbability != null
			? List.of()
			: sourceRate != null ? List.of(sourceRate) : dropRateResolver.dropRatesByItemName(itemName);

		log.debug("New collection log item '{}' (id {}, resolved via {}) resolved to {} (kill count {} {}, drop probability {}, ambiguous rates {})",
			itemName, itemId, resolvedVia, result, killCount, killCountKind, dropProbability, ambiguousDropRates);

		boolean held = config.delayCoxPopupUntilChest() && CoxLootParser.isUnique(itemName);

		collectionLogOverlay.enqueue(itemName, result.getItemId(), result.getTier(), result.getPrice(), result.isHighAlch(),
			result.getCompPercent(), killCount, killCountKind, source,
			kill != null ? kill.getSecondaryCount() : null, dropProbability, ambiguousDropRates, held);
	}

	@Provides
	CollectionLogPopupEnhancedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectionLogPopupEnhancedConfig.class);
	}
}
