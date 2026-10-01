package com.snakesteak.collectionlogpopupenhanced.cox;

import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Hides CoX purples in chat until the reward chest is opened, then shows them again.
 * <p>Censoring is done in "chatFilterCheck", which runs every time the chatbox is built, by swapping
 * the string the script is about to draw. The stored messages are never touched, so revealing is
 * just forgetting what was tracked and rebuilding the chatbox - nothing can be left censored by a
 * missed restore, and other plugins reading ChatMessage still see the real text.
 * <p>Only the raid party's own broadcast starts tracking, which is what scopes censoring to the player's raid.
 */
@Slf4j
@Singleton
public class CoxChatCensor
{
	private static final String CHAT_FILTER_CHECK = "chatFilterCheck";

	private final Client client;
	private final CollectionLogPopupEnhancedConfig config;

	// Party and clan broadcasts, keyed by standardised player name + item.
	private final Set<String> trackedBroadcasts = new HashSet<>();
	// The local player's own game messages carry no name, so they are tracked by item alone.
	private final Set<String> trackedOwnItems = new HashSet<>();

	@Inject
	CoxChatCensor(Client client, CollectionLogPopupEnhancedConfig config)
	{
		this.client = client;
		this.config = config;
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		CoxChatCensorMode mode = config.coxChatCensor();
		if (mode == CoxChatCensorMode.OFF)
		{
			return;
		}

		String message = chatMessage.getMessage();
		// Clan broadcasts never start tracking - they also arrive for raids the player wasn't in.
		switch (chatMessage.getType())
		{
			case FRIENDSCHATNOTIFICATION:
				CoxLootParser.parsePartyLoot(message).ifPresent(loot -> trackBroadcast(loot, mode));
				break;
			case GAMEMESSAGE:
				if (CoxLootParser.isOwnLootGameMessage(message))
				{
					String item = CoxLootParser.findUnique(message);
					if (item != null)
					{
						trackedOwnItems.add(item);
					}
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!CHAT_FILTER_CHECK.equals(event.getEventName()) || !isCensoring())
		{
			return;
		}

		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		Object[] objectStack = client.getObjectStack();
		int objectStackSize = client.getObjectStackSize();

		ChatMessageType type = ChatMessageType.of(intStack[intStackSize - 2]);
		String message = (String) objectStack[objectStackSize - 1];
		if (message == null)
		{
			return;
		}

		String censored = censor(type, message);
		if (censored != null)
		{
			objectStack[objectStackSize - 1] = censored;
		}
	}

	/**
	 * Shows every censored line again. Called when the chest (or anything else that reveals the
	 * loot) is opened, and on logout, hop and shutdown so nothing stays hidden into a new session.
	 */
	public void reveal()
	{
		boolean wasCensoring = isCensoring();
		trackedBroadcasts.clear();
		trackedOwnItems.clear();
		if (wasCensoring)
		{
			client.refreshChat();
		}
	}

	private boolean isCensoring()
	{
		return config.coxChatCensor() != CoxChatCensorMode.OFF
			&& (!trackedBroadcasts.isEmpty() || !trackedOwnItems.isEmpty());
	}

	/**
	 * @return the line to draw instead, or null to leave it as it is
	 */
	private String censor(ChatMessageType type, String message)
	{
		switch (type)
		{
			case FRIENDSCHATNOTIFICATION:
				return CoxLootParser.parsePartyLoot(message)
					.filter(this::isTracked)
					.map(loot -> CoxLootParser.censorItem(message, loot.getItem()))
					.orElse(null);
			case CLAN_MESSAGE:
			case CLAN_GUEST_MESSAGE:
			case CLAN_GIM_MESSAGE:
				Optional<CoxLootParser.LootBroadcast> broadcast = CoxLootParser.parseClanBroadcast(message);
				return broadcast.filter(this::isTracked).isPresent() ? CoxLootParser.censorAfterColon(message) : null;
			case GAMEMESSAGE:
				if (CoxLootParser.isOwnLootGameMessage(message) && trackedOwnItems.contains(CoxLootParser.findUnique(message)))
				{
					return CoxLootParser.censorAfterColon(message);
				}
				return null;
			default:
				return null;
		}
	}

	private void trackBroadcast(CoxLootParser.LootBroadcast loot, CoxChatCensorMode mode)
	{
		if (mode == CoxChatCensorMode.OWN && !isLocalPlayer(loot.getPlayer()))
		{
			return;
		}
		trackedBroadcasts.add(key(loot));
	}

	private boolean isTracked(CoxLootParser.LootBroadcast loot)
	{
		return trackedBroadcasts.contains(key(loot));
	}

	private boolean isLocalPlayer(String name)
	{
		Player local = client.getLocalPlayer();
		return local != null && local.getName() != null && Text.standardize(local.getName()).equals(Text.standardize(name));
	}

	private static String key(CoxLootParser.LootBroadcast loot)
	{
		return Text.standardize(loot.getPlayer()) + "|" + loot.getItem();
	}
}
