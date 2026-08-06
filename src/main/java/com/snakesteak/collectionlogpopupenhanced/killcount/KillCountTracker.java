package com.snakesteak.collectionlogpopupenhanced.killcount;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Tracks the most recently seen boss kill count (or chest opening count) from the "Your X ...
 * count is: N." chat message, so
 * {@link com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedPlugin} can attach it
 * to a collection log item that unlocked from the same kill/opening. The count message and the
 * collection log message are separate and, for some bosses (e.g. ones looted by searching the
 * corpse), can be arbitrarily far apart in time - so correlation is done by matching the stored
 * boss name against the item's known source(s), not by proximity.
 */
@Singleton
public class KillCountTracker
{
	private static final Pattern KILL_COUNT_PATTERN =
		Pattern.compile("Your (?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? (?:kill )?count is: ?(?:<col=[0-9a-f]{6}>)?(?<kc>[0-9,]+)");

	private String lastBoss;
	private int lastKillCount;

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
	}

	/**
	 * @param candidateSources the collection log source(s) (boss/activity names) that the item being
	 *                          resolved is known to come from
	 * @return the most recently seen kill count, if its boss name matches one of {@code
	 *         candidateSources} (case-insensitive) - null if nothing has been seen yet, or the most
	 *         recent kill count belongs to an unrelated source.
	 */
	public RecentKill killCountFor(Collection<String> candidateSources)
	{
		if (lastBoss == null)
		{
			return null;
		}
		boolean matches = candidateSources.stream().anyMatch(lastBoss::equalsIgnoreCase);
		if (!matches)
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
