package com.snakesteak.collectionlogpopupenhanced.cox;

import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Hides CoX purples in chat until the reward chest is opened, then shows them again.
 * <p>Censoring is done in "chatFilterCheck", which runs every time the chatbox is built, by swapping
 * the string the script is about to draw. The stored messages are never touched, so revealing is
 * just forgetting what was tracked and rebuilding the chatbox - nothing can be left censored by a
 * missed restore, and other plugins reading ChatMessage still see the real text.
 * <p>Each line is classified once, on arrival, and remembered by message id; tracking only starts from the player's own raid.
 */
@Slf4j
@Singleton
public class CoxChatCensor
{
	private static final String CHAT_FILTER_CHECK = "chatFilterCheck";

	// Lines players can type themselves never start or receive a censor.
	private static final Set<ChatMessageType> PLAYER_TYPED = EnumSet.of(
		ChatMessageType.PUBLICCHAT,
		ChatMessageType.MODCHAT,
		ChatMessageType.PRIVATECHAT,
		ChatMessageType.PRIVATECHATOUT,
		ChatMessageType.MODPRIVATECHAT,
		ChatMessageType.FRIENDSCHAT,
		ChatMessageType.CLAN_CHAT,
		ChatMessageType.CLAN_GUEST_CHAT,
		ChatMessageType.CLAN_GIM_CHAT,
		ChatMessageType.AUTOTYPER,
		ChatMessageType.MODAUTOTYPER);

	private enum CensorStyle
	{
		ITEM,
		AFTER_COLON
	}

	@Value
	private static class Candidate
	{
		// Standardised "name|item", or null for the local player's own game messages, which carry no name.
		String key;
		String item;
		boolean localPlayer;
		CensorStyle style;
	}

	private final Client client;
	private final CollectionLogPopupEnhancedConfig config;

	// Party and clan broadcasts, keyed by standardised player name + item.
	private final Set<String> trackedBroadcasts = new HashSet<>();
	// The local player's own game messages carry no name, so they are tracked by item alone.
	private final Set<String> trackedOwnItems = new HashSet<>();
	// Every line naming a purple, by MessageNode id - other plugins may rewrite the text, but not the id.
	private final Map<Integer, Candidate> candidates = new HashMap<>();

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
		if (mode == CoxChatCensorMode.OFF || PLAYER_TYPED.contains(chatMessage.getType()))
		{
			return;
		}

		String message = chatMessage.getMessage();
		String unique = CoxLootParser.findUnique(message);
		MessageNode node = chatMessage.getMessageNode();
		if (unique == null || node == null)
		{
			return;
		}
		log.debug("CoX unique in chat, type {}: {}", chatMessage.getType(), message);

		ChatMessageType type = chatMessage.getType();
		if (type == ChatMessageType.GAMEMESSAGE && CoxLootParser.isOwnLootGameMessage(message))
		{
			candidates.put(node.getId(), new Candidate(null, unique, true, CensorStyle.AFTER_COLON));
			if (isInRaid() && trackedOwnItems.add(unique))
			{
				client.refreshChat();
			}
			return;
		}

		// Every line below must prove it is the player's own raid: clan broadcasts also arrive for clanmates' raids.
		Optional<CoxLootParser.LootBroadcast> clan = CoxLootParser.parseClanBroadcast(message);
		if (clan.isPresent())
		{
			CoxLootParser.LootBroadcast loot = clan.get();
			candidates.put(node.getId(), candidate(loot, CensorStyle.AFTER_COLON));
			if (isInOwnRaid(loot.getPlayer()))
			{
				trackBroadcast(loot, mode);
			}
			return;
		}

		Optional<CoxLootParser.LootBroadcast> party = CoxLootParser.parsePartyLoot(message);
		if (party.isPresent())
		{
			CoxLootParser.LootBroadcast loot = party.get();
			candidates.put(node.getId(), candidate(loot, CensorStyle.ITEM));
			if (isInOwnRaid(loot.getPlayer()))
			{
				trackBroadcast(loot, mode);
			}
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!CHAT_FILTER_CHECK.equals(event.getEventName()) || !isCensoring() || candidates.isEmpty())
		{
			return;
		}

		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		Object[] objectStack = client.getObjectStack();
		int objectStackSize = client.getObjectStackSize();

		// Looked up by message id, as ChatFilterPlugin does: the raw type can't express CLAN_GIM_MESSAGE (id -1).
		Candidate candidate = candidates.get(intStack[intStackSize - 1]);
		String message = (String) objectStack[objectStackSize - 1];
		if (candidate == null || message == null || !isCensored(candidate))
		{
			return;
		}

		objectStack[objectStackSize - 1] = candidate.getStyle() == CensorStyle.ITEM
			? CoxLootParser.censorItem(message, candidate.getItem())
			: CoxLootParser.censorAfterColon(message);
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
		candidates.clear();
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

	private boolean isCensored(Candidate candidate)
	{
		if (candidate.getKey() != null && trackedBroadcasts.contains(candidate.getKey()))
		{
			return true;
		}
		// The player's own party or clan line is hidden by their own valuable drop / clog message too.
		return candidate.isLocalPlayer() && trackedOwnItems.contains(candidate.getItem());
	}

	private Candidate candidate(CoxLootParser.LootBroadcast loot, CensorStyle style)
	{
		return new Candidate(key(loot), loot.getItem(), isLocalPlayer(loot.getPlayer()), style);
	}

	private boolean isInRaid()
	{
		return client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
	}

	private boolean isInOwnRaid(String name)
	{
		if (!isInRaid())
		{
			return false;
		}
		String standardized = Text.standardize(name);
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player != null && player.getName() != null && Text.standardize(player.getName()).equals(standardized))
			{
				return true;
			}
		}
		return isLocalPlayer(name);
	}

	private void trackBroadcast(CoxLootParser.LootBroadcast loot, CoxChatCensorMode mode)
	{
		if (mode == CoxChatCensorMode.OWN && !isLocalPlayer(loot.getPlayer()))
		{
			return;
		}
		// Refresh even for an already drawn line: the party line is drawn before the clan line that may track it.
		if (trackedBroadcasts.add(key(loot)))
		{
			client.refreshChat();
		}
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
