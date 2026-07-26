package com.snakesteak.collectionlogpopupenhanced.killcount;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Tracks the most recently seen boss kill count (or chest opening count) from the "Your X ...
 * count is: N." chat message, so
 * {@link com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedPlugin} can attach it
 * to a collection log item that unlocked from the same kill/opening. The count message and the
 * collection log message are separate, with the count arriving first, so correlation is done by
 * game tick rather than by matching names.
 */
@Singleton
public class KillCountTracker
{
	private static final Pattern KILL_COUNT_PATTERN =
		Pattern.compile("Your (?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? (?:kill )?count is: ?(?:<col=[0-9a-f]{6}>)?(?<kc>[0-9,]+)");

	// Both messages land on the same tick, but item id resolution can lag a tick or so (see
	// ItemIdResolver) - a little slack here still rules out a stale kill count from an earlier kill.
	private static final int MAX_TICK_AGE = 2;

	private final Client client;

	private String lastBoss;
	private int lastKillCount;
	private int lastTick = -1;

	@Inject
	public KillCountTracker(Client client)
	{
		this.client = client;
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		Matcher matcher = KILL_COUNT_PATTERN.matcher(chatMessage.getMessage());
		if (!matcher.find())
		{
			return;
		}

		lastBoss = Text.removeTags(matcher.group("boss"));
		lastKillCount = Integer.parseInt(matcher.group("kc").replace(",", ""));
		lastTick = client.getTickCount();
	}

	/**
	 * @return the boss name and kill count from the most recent kill count chat message, if one
	 *         arrived within the last {@link #MAX_TICK_AGE} ticks - null if none has been seen
	 *         recently enough to plausibly belong to the collection log item currently being
	 *         resolved.
	 */
	public RecentKill recentKill()
	{
		if (lastTick == -1 || client.getTickCount() - lastTick > MAX_TICK_AGE)
		{
			return null;
		}
		return new RecentKill(lastBoss, lastKillCount);
	}

	@Value
	public static class RecentKill
	{
		String source;
		int killCount;
	}
}
