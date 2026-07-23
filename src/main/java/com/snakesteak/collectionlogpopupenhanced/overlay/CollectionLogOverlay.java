package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.droprate.LuckTier;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import com.snakesteak.collectionlogpopupenhanced.sound.SoundManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

@Singleton
public class CollectionLogOverlay extends Overlay
{
	// Matches the fixed size of the bundled Rarity1-4.png background art.
	private static final int PANEL_WIDTH = 328;
	private static final int PANEL_HEIGHT = 109;

	private static final int ICON_X = 18;
	private static final int ICON_Y = 14;
	private static final int ICON_SIZE = 78;
	// Item sprites are a fixed 36x32 (net.runelite.api.Constants.ITEM_SPRITE_WIDTH/HEIGHT) - far
	// smaller than the icon slot, so scale the longer side up to this before centering it.
	private static final int ICON_TARGET_SIZE = 66;

	private static final int TEXT_X = 108;
	private static final int TEXT_RIGHT_MARGIN = 10;
	private static final int TITLE_BASELINE_Y = 33;
	private static final int NAME_BASELINE_Y = 53;
	private static final int NAME_LINE_HEIGHT = 17;
	// Pinned near the bottom of the panel's actual interior (rather than a fixed offset below the
	// name) so a long item name can wrap onto a second line without pushing the stat row down with
	// it. The bundled Rarity1-4.png art is 109px tall, but its dark interior background - as opposed
	// to the decorative border, or fully transparent padding below that - only extends to about y=90
	// (verified by sampling pixel alpha/color down the image), so this has to stay well short of
	// PANEL_HEIGHT to avoid drawing into the border.
	private static final int PRICE_BASELINE_Y = 84;

	private static final Color TITLE_COLOR = new Color(255, 152, 31);
	private static final Color COMMON_COLOR = new Color(220, 220, 214);
	private static final Color UNCOMMON_COLOR = new Color(43, 127, 191);
	private static final Color RARE_COLOR = new Color(175, 43, 191);
	private static final Color VERY_RARE_COLOR = new Color(191, 182, 43);
	private static final Color PET_COLOR = new Color(235, 120, 190);
	private static final Color PRICE_LABEL_COLOR = COMMON_COLOR;
	private static final Color PRICE_VALUE_COLOR = new Color(255, 205, 45);

	private static final float TITLE_FONT_SIZE = 13f;
	private static final float NAME_FONT_SIZE = 15f;
	private static final float NAME_FONT_MIN_SIZE = 10f;
	private static final float PRICE_FONT_SIZE = 11f;

	// Fold open/closed drives how much of the background is revealed; fade in/out drives the
	// icon/text alpha. The two run back-to-back (fold, then fade) on the way in, and in reverse
	// on the way out.
	private static final long FOLD_MILLIS = 220;
	private static final long FADE_MILLIS = 180;

	private enum Phase
	{
		OPENING, FADING_IN, VISIBLE, FADING_OUT, CLOSING
	}

	private final ItemManager itemManager;
	private final CollectionLogPopupEnhancedConfig config;
	private final SoundManager soundManager;
	private final Map<RarityTier, BufferedImage> backgrounds = new EnumMap<>(RarityTier.class);
	private final Font titleFont;
	private final Font nameFont;
	private final Font priceFont;

	// Only ever written/read from the client thread (enqueue() from chat/command event handlers,
	// render() from the client's render pass), so no synchronization is needed.
	private final Deque<PendingItem> queue = new ArrayDeque<>();

	private PendingItem current;
	private Phase phase;
	private long phaseStartMillis;

	@Inject
	public CollectionLogOverlay(ItemManager itemManager, CollectionLogPopupEnhancedConfig config, SoundManager soundManager)
	{
		this.itemManager = itemManager;
		this.config = config;
		this.soundManager = soundManager;
		setPosition(OverlayPosition.TOP_CENTER);

		BufferedImage common = loadBackground("Rarity1.png");
		BufferedImage uncommon = loadBackground("Rarity2.png");
		BufferedImage rare = loadBackground("Rarity3.png");
		BufferedImage veryRare = loadBackground("Rarity4.png");
		backgrounds.put(RarityTier.COMMON, common);
		backgrounds.put(RarityTier.UNCOMMON, uncommon);
		backgrounds.put(RarityTier.RARE, rare);
		backgrounds.put(RarityTier.VERY_RARE, veryRare);
		// No dedicated pet artwork is bundled - pets are the rarest category, so they reuse the
		// very rare border; the pink item-name text still distinguishes them from a regular drop.
		backgrounds.put(RarityTier.PET, veryRare);

		titleFont = FontManager.getRunescapeBoldFont().deriveFont(TITLE_FONT_SIZE);
		nameFont = FontManager.getRunescapeBoldFont().deriveFont(NAME_FONT_SIZE);
		priceFont = FontManager.getRunescapeBoldFont().deriveFont(PRICE_FONT_SIZE);
	}

	private static BufferedImage loadBackground(String resourceName)
	{
		try (InputStream in = CollectionLogOverlay.class.getResourceAsStream("/" + resourceName))
		{
			return ImageIO.read(in);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException("Failed to load overlay background " + resourceName, e);
		}
	}

	public void enqueue(String itemName, int itemId, RarityTier tier, int price, boolean highAlch, Double compPercent,
		Integer killCount, boolean killCountIsTracked, Double dropProbability)
	{
		// The overlay was fully idle (nothing showing, nothing queued) right before this item arrived,
		// so it's the first of a fresh batch - the only one that plays a sound when bulkUnlockSfx is on.
		// Anything that arrives while the overlay is still working through a previous item (or its own
		// queue) is treated as part of that same batch, however far apart in time the underlying chat
		// messages actually were.
		boolean batchStart = queue.isEmpty() && current == null;
		queue.addLast(new PendingItem(itemName, itemId, tier, price, highAlch, compPercent, killCount, killCountIsTracked,
			dropProbability, batchStart));
	}

	public void clear()
	{
		queue.clear();
		current = null;
		phase = null;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		long now = System.currentTimeMillis();
		advance(now);

		if (current == null)
		{
			// Report the same size used when populated instead of null/0 - RuneLite resets an
			// overlay's cached bounds to (0, 0) whenever render() returns null, and TOP_CENTER
			// positioning centers each frame using the *previous* frame's bounds. Returning 0 here
			// would make the panel start centered around half of nothing, then visibly jump left
			// once the real width is reported on the following frame.
			return new Dimension(PANEL_WIDTH, 0);
		}

		BufferedImage background = backgrounds.get(current.getTier());
		float openProgress = openProgress(now);
		int drawnHeight = Math.round(PANEL_HEIGHT * openProgress);

		if (drawnHeight >= 2)
		{
			Shape originalClip = graphics.getClip();
			graphics.clipRect(0, 0, PANEL_WIDTH, drawnHeight);
			graphics.drawImage(background, 0, 0, null);
			graphics.setClip(originalClip);
		}

		float contentAlpha = openProgress >= 1f ? contentAlpha(now) : 0f;
		if (contentAlpha > 0.01f)
		{
			Composite originalComposite = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, contentAlpha));
			// The RuneScape font is a small bitmap-style pixel font - antialiasing/fractional
			// metrics smooth it into a blurry halo instead of the crisp look the game's own UI has.
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

			// A negative id means ItemIdResolver couldn't identify the item (untradeable and not seen
			// in inventory/on the ground - see ItemIdResolver javadoc). ItemManager.getImage() doesn't
			// handle that gracefully - it silently returns an unrelated cache sprite rather than null
			// or throwing - so the icon slot is left blank instead of showing that garbage image.
			if (current.getItemId() >= 0)
			{
				BufferedImage sprite = itemManager.getImage(current.getItemId());
				float spriteScale = ICON_TARGET_SIZE / (float) Math.max(sprite.getWidth(), sprite.getHeight());
				int scaledWidth = Math.round(sprite.getWidth() * spriteScale);
				int scaledHeight = Math.round(sprite.getHeight() * spriteScale);
				Object originalInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
				graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				graphics.drawImage(sprite,
					ICON_X + (ICON_SIZE - scaledWidth) / 2,
					ICON_Y + (ICON_SIZE - scaledHeight) / 2,
					scaledWidth, scaledHeight,
					null);
				if (originalInterpolation != null)
				{
					graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, originalInterpolation);
				}
			}

			graphics.setFont(titleFont);
			graphics.setColor(TITLE_COLOR);
			graphics.drawString("Collection log", TEXT_X, TITLE_BASELINE_Y);

			int maxNameWidth = PANEL_WIDTH - TEXT_X - TEXT_RIGHT_MARGIN;
			FittedName fittedName = fitName(graphics, current.getItemName(), nameFont, maxNameWidth);
			graphics.setFont(fittedName.getFont());
			graphics.setColor(tierColor(current.getTier()));
			int nameY = NAME_BASELINE_Y;
			for (String line : fittedName.getLines())
			{
				graphics.drawString(line, TEXT_X, nameY);
				nameY += NAME_LINE_HEIGHT;
			}

			graphics.setFont(priceFont);
			FontMetrics priceMetrics = graphics.getFontMetrics(priceFont);

			Stat leftStat = resolveStatWithFallback(config.leftPanelStat().toPanelStat(), current);
			if (leftStat != null)
			{
				graphics.setColor(PRICE_LABEL_COLOR);
				graphics.drawString(leftStat.getLabel(), TEXT_X, PRICE_BASELINE_Y);
				graphics.setColor(leftStat.getValueColor());
				graphics.drawString(leftStat.getValue(), TEXT_X + priceMetrics.stringWidth(leftStat.getLabel()), PRICE_BASELINE_Y);
			}

			Stat rightStat = resolveStatWithFallback(config.rightPanelStat().toPanelStat(), current);
			if (rightStat != null)
			{
				int rightX = TEXT_X + maxNameWidth - priceMetrics.stringWidth(rightStat.getLabel() + rightStat.getValue());

				graphics.setColor(PRICE_LABEL_COLOR);
				graphics.drawString(rightStat.getLabel(), rightX, PRICE_BASELINE_Y);
				graphics.setColor(rightStat.getValueColor());
				graphics.drawString(rightStat.getValue(), rightX + priceMetrics.stringWidth(rightStat.getLabel()), PRICE_BASELINE_Y);
			}

			graphics.setComposite(originalComposite);
		}

		return new Dimension(PANEL_WIDTH, PANEL_HEIGHT);
	}

	private void advance(long now)
	{
		if (current == null)
		{
			current = queue.pollFirst();
			if (current != null)
			{
				phase = Phase.OPENING;
				phaseStartMillis = now;
				// Fired here rather than at enqueue() time so playback is spaced out to match the visual
				// reveal of each item, instead of every item in a burst firing its sound at once (which
				// used to overlap/cut each other off on top of the client's audio mixer).
				if (!config.bulkUnlockSfx() || current.isBatchStart())
				{
					soundManager.play(current.getTier());
				}
			}
			return;
		}

		long elapsed = now - phaseStartMillis;
		switch (phase)
		{
			case OPENING:
				if (elapsed >= FOLD_MILLIS)
				{
					phase = Phase.FADING_IN;
					phaseStartMillis = now;
				}
				break;
			case FADING_IN:
				if (elapsed >= FADE_MILLIS)
				{
					phase = Phase.VISIBLE;
					phaseStartMillis = now;
				}
				break;
			case VISIBLE:
				if (elapsed >= config.overlayDisplaySeconds() * 1000L)
				{
					phase = Phase.FADING_OUT;
					phaseStartMillis = now;
				}
				break;
			case FADING_OUT:
				if (elapsed >= FADE_MILLIS)
				{
					phase = Phase.CLOSING;
					phaseStartMillis = now;
				}
				break;
			case CLOSING:
				if (elapsed >= FOLD_MILLIS)
				{
					current = null;
					phase = null;
				}
				break;
		}
	}

	private float openProgress(long now)
	{
		long elapsed = now - phaseStartMillis;
		switch (phase)
		{
			case OPENING:
				return clamp01(elapsed / (float) FOLD_MILLIS);
			case CLOSING:
				return 1f - clamp01(elapsed / (float) FOLD_MILLIS);
			default:
				return 1f;
		}
	}

	private float contentAlpha(long now)
	{
		long elapsed = now - phaseStartMillis;
		switch (phase)
		{
			case FADING_IN:
				return clamp01(elapsed / (float) FADE_MILLIS);
			case VISIBLE:
				return 1f;
			case FADING_OUT:
				return 1f - clamp01(elapsed / (float) FADE_MILLIS);
			default:
				return 0f;
		}
	}

	private static float clamp01(float value)
	{
		return Math.max(0f, Math.min(1f, value));
	}

	/**
	 * Wraps the item name onto up to 2 lines, shrinking the font (down to {@link #NAME_FONT_MIN_SIZE})
	 * only if the name still doesn't fit both lines at that size - e.g. a single word longer than
	 * the panel is wide. Any overflow left after that is truncated with an ellipsis on the 2nd line.
	 */
	private static FittedName fitName(Graphics2D graphics, String text, Font baseFont, int maxWidth)
	{
		Font font = baseFont;
		List<String> lines;
		while (true)
		{
			FontMetrics metrics = graphics.getFontMetrics(font);
			if (metrics.stringWidth(text) <= maxWidth)
			{
				lines = List.of(text);
				break;
			}

			lines = wrapToTwoLines(metrics, text, maxWidth);
			boolean secondLineFits = lines.size() < 2 || metrics.stringWidth(lines.get(1)) <= maxWidth;
			if (secondLineFits || font.getSize2D() <= NAME_FONT_MIN_SIZE)
			{
				break;
			}
			font = font.deriveFont(font.getSize2D() - 1f);
		}

		if (lines.size() == 2 && graphics.getFontMetrics(font).stringWidth(lines.get(1)) > maxWidth)
		{
			lines = List.of(lines.get(0), truncate(graphics, lines.get(1), font, maxWidth));
		}
		return new FittedName(font, lines);
	}

	/**
	 * Greedily fills the first line with whole words up to maxWidth; everything left over (however
	 * much doesn't fit) goes on the second line.
	 */
	private static List<String> wrapToTwoLines(FontMetrics metrics, String text, int maxWidth)
	{
		String[] words = text.split(" ");
		StringBuilder firstLine = new StringBuilder();
		int splitIndex = words.length;
		for (int i = 0; i < words.length; i++)
		{
			String candidate = firstLine.length() == 0 ? words[i] : firstLine + " " + words[i];
			if (firstLine.length() > 0 && metrics.stringWidth(candidate) > maxWidth)
			{
				splitIndex = i;
				break;
			}
			firstLine = new StringBuilder(candidate);
		}

		if (splitIndex == words.length)
		{
			return List.of(firstLine.toString());
		}
		return List.of(firstLine.toString(), String.join(" ", Arrays.copyOfRange(words, splitIndex, words.length)));
	}

	private static String truncate(Graphics2D graphics, String text, Font font, int maxWidth)
	{
		FontMetrics metrics = graphics.getFontMetrics(font);
		if (metrics.stringWidth(text) <= maxWidth)
		{
			return text;
		}

		String ellipsis = "...";
		StringBuilder truncated = new StringBuilder(text);
		while (truncated.length() > 0 && metrics.stringWidth(truncated + ellipsis) > maxWidth)
		{
			truncated.setLength(truncated.length() - 1);
		}
		return truncated + ellipsis;
	}

	/**
	 * @return the primary stat, or its fallback if the primary isn't applicable to {@code item}
	 *         (e.g. Luck picked but there's no correlated kill) - or null if neither is, in which
	 *         case that side of the panel is simply left blank.
	 */
	private Stat resolveStatWithFallback(PanelStat primary, PendingItem item)
	{
		Stat stat = resolveStat(primary, item);
		return stat != null ? stat : resolveStat(fallbackFor(primary), item);
	}

	/**
	 * Kill count and Luck are the only stats that can be unavailable for an item (no correlated
	 * kill); each falls back to a stat that's always available. Everything else has no fallback.
	 */
	private static PanelStat fallbackFor(PanelStat stat)
	{
		switch (stat)
		{
			case KILL_COUNT:
				return PanelStat.RARITY;
			case LUCK:
				return PanelStat.VALUE;
			default:
				return PanelStat.NONE;
		}
	}

	/**
	 * @return the label/value/color to draw for {@code stat}, or null if that stat isn't
	 *         applicable to {@code item} (e.g. no rarity data, or no kill count was detected).
	 */
	private Stat resolveStat(PanelStat stat, PendingItem item)
	{
		switch (stat)
		{
			case VALUE:
				String valueText = QuantityFormatter.formatNumber(item.getPrice()) + " gp" + (item.isHighAlch() ? " (HA)" : "");
				return new Stat("Value: ", valueText, PRICE_VALUE_COLOR);
			case RARITY:
				if (item.getCompPercent() == null)
				{
					return null;
				}
				return new Stat("Wiki Comp%: ", String.format("%.1f%%", item.getCompPercent()), tierColor(item.getTier()));
			case KILL_COUNT:
				if (item.getKillCount() == null)
				{
					return null;
				}
				String killCountText = QuantityFormatter.formatNumber(item.getKillCount()) + (item.isKillCountIsTracked() ? "*" : "");
				return new Stat("KC: ", killCountText, PRICE_VALUE_COLOR);
			case LUCK:
				if (item.getKillCount() == null || item.getDropProbability() == null)
				{
					return null;
				}
				LuckTier tier = LuckTier.fromKillCount(item.getKillCount(), item.getDropProbability());
				String fraction = "1/" + Math.round(1 / item.getDropProbability());
				String luckText = tier + " (" + item.getKillCount() + "kc, " + fraction + ")";
				return new Stat("Luck: ", luckText, PRICE_VALUE_COLOR);
			case NONE:
			default:
				return null;
		}
	}

	private static Color tierColor(RarityTier tier)
	{
		switch (tier)
		{
			case COMMON:
				return COMMON_COLOR;
			case UNCOMMON:
				return UNCOMMON_COLOR;
			case RARE:
				return RARE_COLOR;
			case VERY_RARE:
				return VERY_RARE_COLOR;
			case PET:
				return PET_COLOR;
			default:
				return COMMON_COLOR;
		}
	}

	@Value
	private static class PendingItem
	{
		String itemName;
		int itemId;
		RarityTier tier;
		int price;
		boolean highAlch;
		Double compPercent;
		Integer killCount;
		boolean killCountIsTracked;
		Double dropProbability;
		boolean batchStart;
	}

	@Value
	private static class FittedName
	{
		Font font;
		List<String> lines;
	}

	@Value
	private static class Stat
	{
		String label;
		String value;
		Color valueColor;
	}
}
