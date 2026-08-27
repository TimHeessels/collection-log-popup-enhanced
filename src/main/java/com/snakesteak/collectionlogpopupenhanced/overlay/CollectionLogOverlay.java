package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.droprate.DropRateResolver;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountKind;
import com.snakesteak.collectionlogpopupenhanced.rarity.PreviewTier;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import com.snakesteak.collectionlogpopupenhanced.sound.SoundManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
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
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

@Singleton
public class CollectionLogOverlay extends Overlay
{
	// Matches the fixed size of the bundled BackgroundPanel1-4.png background art.
	private static final int BASE_PANEL_WIDTH = 379;
	private static final int BASE_PANEL_HEIGHT = 128;

	// Breathing room above the panel so it doesn't sit flush against the top of the screen. Kept
	// small so the panel's top edge still clears/covers the native OSRS collection log popup, which
	// always renders flush against the very top of the screen.
	private static final int TOP_MARGIN = 2;

	// The icon slot is horizontally centered and straddles the panel's bottom edge, matching the
	// bundled IconPanel1-4.png art (89x89), and animates independently of the panel's fold-open
	// transform.
	private static final int BASE_ICON_CANVAS_SIZE = 89;
	// Item sprites are a fixed 36x32 - far smaller than the icon slot's usable interior, so scale
	// the longer side up to this before centering it.
	private static final int BASE_ICON_TARGET_SIZE = 66;
	// The sprite art isn't visually centered within its own bounding box, so nudge it right of the
	// icon frame's true center to compensate.
	private static final int BASE_ICON_SPRITE_X_OFFSET = 3;

	// Bottom-left / bottom-right stacked label+value blocks, clear of the icon's horizontal footprint
	// and below the progress bar.
	private static final int BASE_CORNER_PADDING_X = 16;
	private static final int BASE_CORNER_LABEL_BASELINE_Y = 93;
	private static final int BASE_CORNER_VALUE_BASELINE_Y = 116;
	// Ambiguous Drop rate values (see PanelStat#DROP_RATE) can show up to 2 stacked value lines in a
	// smaller font so both still fit below the item name, without the second line's descenders
	// running past the panel's bottom edge.
	private static final int BASE_CORNER_MULTI_VALUE_FIRST_BASELINE_Y = 105;
	private static final int BASE_CORNER_MULTI_VALUE_LINE_HEIGHT = 16;
	private static final float BASE_CORNER_MULTI_VALUE_FONT_SIZE = 14f;

	// Item name - centered, below the caption and above the progress bar. Always a single line; names
	// too wide to fit are shrunk, then ellipsised (see #fitName).
	private static final int BASE_NAME_BASELINE_Y = 48;
	private static final int BASE_NAME_SIDE_MARGIN = 16;

	// Total collection log progress bar, drawn in the band the art used to carry a plain divider line
	// (which sat at y=69-70, and has since been removed from the source PNGs).
	//
	// Centered in the gap between the item name and the corner stats: the name is always one line (see
	// #fitName) with descender ink ending at y=51, and the stat labels' ink starts at y=81. 2px - the
	// old divider line's height - is too thin to read as a fill against a track, hence 3.
	private static final int BASE_PROGRESS_BAR_TOP_Y = 64;
	private static final int BASE_PROGRESS_BAR_HEIGHT = 3;
	// Matches the inset the divider line had (~6% of the panel's width per side). Derived from
	// panelWidth rather than a fixed x so it stays centered across both source art widths - panels 4
	// and Pet are 380px wide against the others' 379, which also left the old line 1px off-center.
	private static final float PROGRESS_BAR_SIDE_INSET_FRACTION = 0.0607f;
	// How dark the unfilled track is relative to the panel body. Reuses the same derivation the body
	// uses (see PanelRecolorer#deriveBackground) at a fraction of the configured darkness, so the
	// track stays in the tier's colour family and still separates from the body at either end of the
	// darkness range.
	private static final float PROGRESS_BAR_TRACK_DARKNESS_SCALE = 0.5f;

	private static final String CAPTION_TEXT = "Collection log slot";
	private static final int BASE_CAPTION_BASELINE_Y = 22;

	// The colour picker offers an alpha slider, but the panel art rebuilds every pixel from the
	// source art's own alpha, so a picked alpha can only ever apply to the text - which would leave
	// one config value meaning two different things. Alpha is stripped on read instead, everywhere.
	private static final int RGB_MASK = 0xFFFFFF;

	private static final float BASE_CORNER_LABEL_FONT_SIZE = 16f;
	private static final float BASE_CORNER_VALUE_FONT_SIZE = 19f;
	private static final float BASE_NAME_FONT_SIZE = 27f;
	private static final float BASE_NAME_FONT_MIN_SIZE = 13f;
	private static final float BASE_CAPTION_FONT_SIZE = 16f;

	// Fold-open (panel scaleY) plays first; the icon pop (its own scale+fade) starts exactly when
	// the fold ends. Both pivot at their own center. Fade-out is the only closing animation.
	private static final long FOLD_MILLIS = 550;
	private static final long ICON_POP_MILLIS = 400;
	private static final long FADE_MILLIS = 400;

	private static final float ICON_MIN_SCALE = 0.4f;
	private static final float FOLD_OVERSHOOT = 1.0f;
	private static final float ICON_POP_OVERSHOOT = 1.9f;

	private final Client client;
	private final ItemManager itemManager;
	private final CollectionLogPopupEnhancedConfig config;
	private final SoundManager soundManager;
	// Pristine art as loaded from the jar, kept so #applyColours can always recolour from the
	// original pixels rather than compounding successive recolours.
	private final Map<RarityTier, BufferedImage> sourceBackgrounds = new EnumMap<>(RarityTier.class);
	private final Map<RarityTier, BufferedImage> sourceIconFrames = new EnumMap<>(RarityTier.class);
	// What actually gets drawn - the source art recoloured to the configured per-tier colours.
	private final Map<RarityTier, BufferedImage> backgrounds = new EnumMap<>(RarityTier.class);
	private final Map<RarityTier, BufferedImage> iconFrames = new EnumMap<>(RarityTier.class);
	// A step darker than the panel body: at the same shade the bar reads as a floating stripe rather
	// than a fill inside a recess. Cached alongside the art - same inputs, and otherwise per-frame.
	private final Map<RarityTier, Color> progressBarTrackColors = new EnumMap<>(RarityTier.class);

	// Only ever written/read from the client thread, so no synchronization is needed.
	private final Deque<PendingItem> queue = new ArrayDeque<>();

	private PendingItem current;
	private long notificationStartMillis;

	// Scaled layout/font state, recomputed by #applyScale only when config.overlayScalePercent()
	// changes (rather than every frame) since Font#deriveFont isn't free to call 50x/sec for nothing.
	private int lastScalePercent = -1;
	// Cache key for the recoloured art: the five tier colours plus the background darkness (see
	// #readColours), so #render can detect a change cheaply; null until #applyColours has run.
	private int[] lastColours;
	private boolean textOutlineEnabled;
	private int panelWidth;
	private int panelHeight;
	private int iconCanvasSize;
	private int iconX;
	private int iconY;
	private int iconTargetSize;
	private int iconSpriteXOffset;
	private int cornerPaddingX;
	private int cornerLabelBaselineY;
	private int cornerValueBaselineY;
	private int cornerMultiValueFirstBaselineY;
	private int cornerMultiValueLineHeight;
	private int cornerTextMaxWidth;
	private int nameBaselineY;
	private int nameSideMargin;
	private int captionBaselineY;
	private int progressBarTopY;
	private int progressBarHeight;
	private int progressBarX;
	private int progressBarWidth;
	private float nameFontMinSize;
	private Font cornerLabelFont;
	private Font cornerValueFont;
	private Font cornerMultiValueFont;
	private Font nameFont;
	private Font captionFont;

	@Inject
	public CollectionLogOverlay(Client client, ItemManager itemManager, CollectionLogPopupEnhancedConfig config, SoundManager soundManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
		this.soundManager = soundManager;
		setPosition(OverlayPosition.TOP_CENTER);
		// Draw above the native game widgets (including the native collection log popup, which
		// otherwise renders on top of and hides this overlay) and ahead of any other TOP_CENTER
		// overlay (e.g. xp orb/drop plugins), so this panel always stays pinned at the very top.
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
		// The panel recenters itself on the real canvas width every frame (see #render) to line up
		// with the native collection log popup, so letting the user drag/snap it elsewhere would just
		// get silently undone next frame - disable that instead of leaving it non-functional.
		setMovable(false);

		sourceBackgrounds.put(RarityTier.COMMON, loadImage("Backgrounds/BackgroundPanel1.png"));
		sourceBackgrounds.put(RarityTier.UNCOMMON, loadImage("Backgrounds/BackgroundPanel2.png"));
		sourceBackgrounds.put(RarityTier.RARE, loadImage("Backgrounds/BackgroundPanel3.png"));
		sourceBackgrounds.put(RarityTier.VERY_RARE, loadImage("Backgrounds/BackgroundPanel4.png"));
		sourceBackgrounds.put(RarityTier.PET, loadImage("Backgrounds/BackgroundPanelPet.png"));

		sourceIconFrames.put(RarityTier.COMMON, loadImage("Icons/IconPanel1.png"));
		sourceIconFrames.put(RarityTier.UNCOMMON, loadImage("Icons/IconPanel2.png"));
		sourceIconFrames.put(RarityTier.RARE, loadImage("Icons/IconPanel3.png"));
		sourceIconFrames.put(RarityTier.VERY_RARE, loadImage("Icons/IconPanel4.png"));
		sourceIconFrames.put(RarityTier.PET, loadImage("Icons/IconPanelPet.png"));

		applyScale(config.overlayScalePercent());
		applyColours(readColours());
	}

	/**
	 * Recomputes every scaled layout constant and re-derives each font at its scaled point size, so
	 * text is rasterized natively at the target size rather than blurred by stretching a fixed-size
	 * raster. Cheap, but only worth doing when the configured scale actually changes (see #render).
	 */
	private void applyScale(int scalePercent)
	{
		float scale = scalePercent / 100f;

		panelWidth = Math.round(BASE_PANEL_WIDTH * scale);
		panelHeight = Math.round(BASE_PANEL_HEIGHT * scale);
		iconCanvasSize = Math.round(BASE_ICON_CANVAS_SIZE * scale);
		iconX = (panelWidth - iconCanvasSize) / 2;
		iconY = panelHeight - iconCanvasSize / 2;
		iconTargetSize = Math.round(BASE_ICON_TARGET_SIZE * scale);
		iconSpriteXOffset = Math.round(BASE_ICON_SPRITE_X_OFFSET * scale);

		cornerPaddingX = Math.round(BASE_CORNER_PADDING_X * scale);
		cornerLabelBaselineY = Math.round(BASE_CORNER_LABEL_BASELINE_Y * scale);
		cornerValueBaselineY = Math.round(BASE_CORNER_VALUE_BASELINE_Y * scale);
		cornerMultiValueFirstBaselineY = Math.round(BASE_CORNER_MULTI_VALUE_FIRST_BASELINE_Y * scale);
		cornerMultiValueLineHeight = Math.round(BASE_CORNER_MULTI_VALUE_LINE_HEIGHT * scale);
		cornerTextMaxWidth = iconX - cornerPaddingX;

		nameBaselineY = Math.round(BASE_NAME_BASELINE_Y * scale);
		nameSideMargin = Math.round(BASE_NAME_SIDE_MARGIN * scale);
		nameFontMinSize = BASE_NAME_FONT_MIN_SIZE * scale;

		captionBaselineY = Math.round(BASE_CAPTION_BASELINE_Y * scale);

		progressBarTopY = Math.round(BASE_PROGRESS_BAR_TOP_Y * scale);
		// At least 1px so the bar never vanishes entirely at the smallest configured scale.
		progressBarHeight = Math.max(1, Math.round(BASE_PROGRESS_BAR_HEIGHT * scale));
		progressBarX = Math.round(panelWidth * PROGRESS_BAR_SIDE_INSET_FRACTION);
		progressBarWidth = panelWidth - 2 * progressBarX;

		cornerLabelFont = FontManager.getRunescapeBoldFont().deriveFont(BASE_CORNER_LABEL_FONT_SIZE * scale);
		cornerValueFont = FontManager.getRunescapeBoldFont().deriveFont(BASE_CORNER_VALUE_FONT_SIZE * scale);
		cornerMultiValueFont = FontManager.getRunescapeBoldFont().deriveFont(BASE_CORNER_MULTI_VALUE_FONT_SIZE * scale);
		nameFont = FontManager.getRunescapeBoldFont().deriveFont(BASE_NAME_FONT_SIZE * scale);
		captionFont = FontManager.getRunescapeBoldFont().deriveFont(BASE_CAPTION_FONT_SIZE * scale);

		lastScalePercent = scalePercent;
	}

	// Purely a change detector - the rebuild re-reads each colour by tier (see #applyColours), so
	// this array's order carries no meaning beyond being stable frame to frame.
	private int[] readColours()
	{
		RarityTier[] tiers = RarityTier.values();
		int[] key = new int[tiers.length + 1];
		for (int i = 0; i < tiers.length; i++)
		{
			key[i] = tierColor(tiers[i]).getRGB();
		}
		// Part of the cache key, not a colour: changing it re-derives every background.
		key[tiers.length] = config.backgroundDarkness();
		return key;
	}

	/**
	 * Rebuilds the drawn panel/icon art for every tier from the pristine sources. ~280k pixels across
	 * ten images, so this runs on startup and colour changes only (see #render) - never per frame.
	 */
	private void applyColours(int[] colours)
	{
		int darkness = config.backgroundDarkness();
		for (RarityTier tier : RarityTier.values())
		{
			Color border = tierColor(tier);
			Color background = PanelRecolorer.deriveBackground(border, darkness);
			backgrounds.put(tier, PanelRecolorer.recolor(sourceBackgrounds.get(tier), tier, border, background));
			iconFrames.put(tier, PanelRecolorer.recolor(sourceIconFrames.get(tier), tier, border, background));
			progressBarTrackColors.put(tier,
				PanelRecolorer.deriveBackground(border, Math.max(1, Math.round(darkness * PROGRESS_BAR_TRACK_DARKNESS_SCALE))));
		}
		lastColours = colours;
	}

	private static BufferedImage loadImage(String resourceName)
	{
		try (InputStream in = CollectionLogOverlay.class.getResourceAsStream("/" + resourceName))
		{
			return ImageIO.read(in);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException("Failed to load overlay image " + resourceName, e);
		}
	}

	public void enqueue(String itemName, int itemId, RarityTier tier, int price, boolean highAlch, int alchPrice,
		Double compPercent, Integer killCount, KillCountKind killCountKind, String killCountSource,
		Double dropProbability, List<DropRateResolver.SourceRate> ambiguousDropRates)
	{
		// The overlay was fully idle right before this item arrived, so it's the first of a fresh
		// batch - the only one that plays a sound when bulkUnlockSfx is on.
		boolean batchStart = queue.isEmpty() && current == null;
		queue.addLast(new PendingItem(itemName, itemId, tier, price, highAlch, alchPrice, compPercent, killCount,
			killCountKind, killCountSource, dropProbability, ambiguousDropRates, batchStart));
	}

	public void clear()
	{
		queue.clear();
		current = null;
	}

	/**
	 * @return true if nothing is currently showing or queued - used to drive the preview mode loop
	 *         (see CollectionLogPopupEnhancedPlugin#onGameTick), which re-triggers a new random item
	 *         each time the previous one finishes.
	 */
	public boolean isIdle()
	{
		return current == null && queue.isEmpty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		long now = System.currentTimeMillis();
		advance(now);

		int scalePercent = config.overlayScalePercent();
		if (scalePercent != lastScalePercent)
		{
			applyScale(scalePercent);
		}

		int[] colours = readColours();
		if (!Arrays.equals(colours, lastColours))
		{
			applyColours(colours);
		}

		// TOP_CENTER centers on the HUD container widget, not the client canvas, so it doesn't line
		// up with the native (canvas-centered) popup this overlaps. An absolute location bypasses
		// that. Must center on the viewport rect, not the client width: in fixed/classic layout the
		// viewport is offset left of the sidebar, and client width would skew the panel toward it.
		int viewportCenterX;
		if (client.isResized())
		{
			viewportCenterX = client.getRealDimensions().width / 2;
		}
		else
		{
			viewportCenterX = client.getViewportXOffset() + (client.getViewportWidth() / 2);
		}
		setPreferredLocation(new Point(viewportCenterX - (panelWidth / 2), TOP_MARGIN));

		if (current == null)
		{
			// Report the panel's real width instead of 0 - RuneLite's positioning centers each frame
			// using the *previous* frame's bounds, so returning 0 here would make the panel jump left
			// once its real width is reported on the next frame it's shown.
			return new Dimension(panelWidth, 0);
		}

		long elapsed = now - notificationStartMillis;
		long iconPopStart = FOLD_MILLIS;

		float foldRaw = clamp01(elapsed / (float) FOLD_MILLIS);
		float foldT = easeOutBack(foldRaw, FOLD_OVERSHOOT);
		// Preview mode holds the item on screen indefinitely (see #advance) instead of fading it out
		// on the usual timer, so users can freely tweak scale/text settings without it disappearing.
		float fadeAlpha;
		if (config.previewTier() != PreviewTier.NONE)
		{
			fadeAlpha = 1f;
		}
		else
		{
			long holdStart = iconPopStart + ICON_POP_MILLIS;
			long fadeStart = holdStart + config.overlayDisplaySeconds() * 1000L;
			fadeAlpha = elapsed < fadeStart ? 1f : 1f - easeInCubic(clamp01((elapsed - fadeStart) / (float) FADE_MILLIS));
		}

		// Which rendering hints look sharpest depends on the user's own display/OS scaling, which isn't
		// predictable from the popup's own scale percentage - so this is a manual config choice (see
		// TextRenderMode) rather than something inferred automatically.
		TextRenderMode textRenderMode = config.textRenderMode();
		textOutlineEnabled = textRenderMode == TextRenderMode.SMOOTH_OUTLINED;
		if (textRenderMode == TextRenderMode.CRISP)
		{
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
			graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
		}
		else
		{
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			// runescape_bold.ttf is hand-hinted to snap to the pixel grid at its native integer point
			// sizes (our 16f sizes hit this at 100% scale), which defeats antialiasing whatever the AA
			// hint says - that only controls whether the already-snapped outline is antialiased.
			// STROKE_PURE renders the unhinted outline, so smoothing is consistent at every size.
			graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		}

		// graphics is already translated to the panel's absolute position (see preferredLocation
		// above) - capture that as the base transform both animated pieces reset back to between draws.
		AffineTransform base = graphics.getTransform();
		Composite originalComposite = graphics.getComposite();

		// --- panel: scaleY-only fold, pivoted at its own center, plus whatever's left of the
		// fade-out alpha. Corner stats/name/caption are drawn here too so they reveal progressively
		// as the fold grows outward from the middle.
		graphics.translate(panelWidth / 2.0, panelHeight / 2.0);
		graphics.scale(1.0, foldT);
		graphics.translate(-panelWidth / 2.0, -panelHeight / 2.0);

		Shape originalClip = graphics.getClip();
		if (foldRaw < 1f)
		{
			// Still folding - clip to the panel's own (already scaled) bounds so nothing draws
			// past where the fold has actually reached yet.
			graphics.clip(new Rectangle2D.Float(0, 0, panelWidth, panelHeight));
		}
		if (fadeAlpha < 1f)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
		}

		graphics.drawImage(backgrounds.get(current.getTier()), 0, 0, panelWidth, panelHeight, null);
		drawPanelContent(graphics);

		graphics.setClip(originalClip);
		graphics.setComposite(originalComposite);
		graphics.setTransform(base);

		// --- icon: independent pop, only once the fold has played out. Skipped entirely (not just
		// alpha 0) when unresolved - an empty icon frame is worse than a text-only panel.
		//
		// A non-negative id isn't proof of an icon: the client returns null for an id it has no
		// sprite for, so the image itself is what's checked. Fetched before the frame is drawn so
		// the two are skipped together; unguarded it throws here every frame the popup is up.
		BufferedImage itemSprite = current.getItemId() >= 0 ? itemManager.getImage(current.getItemId()) : null;
		if (elapsed >= iconPopStart && itemSprite != null)
		{
			float iconRaw = clamp01((elapsed - iconPopStart) / (float) ICON_POP_MILLIS);
			float iconEase = easeOutBack(iconRaw, ICON_POP_OVERSHOOT);
			float iconScale = ICON_MIN_SCALE + (1f - ICON_MIN_SCALE) * iconEase;
			float iconAlpha = clamp01(iconEase) * fadeAlpha;

			float iconCenterX = iconX + iconCanvasSize / 2f;
			float iconCenterY = iconY + iconCanvasSize / 2f;
			graphics.translate(iconCenterX, iconCenterY);
			graphics.scale(iconScale, iconScale);
			graphics.translate(-iconCenterX, -iconCenterY);

			if (iconAlpha < 1f)
			{
				graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, iconAlpha)));
			}
			graphics.drawImage(iconFrames.get(current.getTier()), iconX, iconY, iconCanvasSize, iconCanvasSize, null);
			drawItemSprite(graphics, itemSprite);
			graphics.setComposite(originalComposite);
			graphics.setTransform(base);
		}

		// Height includes the icon frame's bottom half, which now straddles the panel's bottom edge.
		return new Dimension(panelWidth, panelHeight + iconCanvasSize / 2);
	}

	private void drawItemSprite(Graphics2D graphics, BufferedImage sprite)
	{
		float spriteScale = iconTargetSize / (float) Math.max(sprite.getWidth(), sprite.getHeight());
		int scaledWidth = Math.round(sprite.getWidth() * spriteScale);
		int scaledHeight = Math.round(sprite.getHeight() * spriteScale);
		Object originalInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(sprite,
			iconX + iconSpriteXOffset + (iconCanvasSize - scaledWidth) / 2,
			iconY + (iconCanvasSize - scaledHeight) / 2,
			scaledWidth, scaledHeight,
			null);
		if (originalInterpolation != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, originalInterpolation);
		}
	}

	/**
	 * Draws {@code text} in {@code color}, preceded by a 1px black drop shadow when
	 * {@link #textOutlineEnabled} (set from {@link TextRenderMode#SMOOTH_OUTLINED} - see #render).
	 */
	private void drawOutlinedString(Graphics2D graphics, String text, int x, int y, Color color)
	{
		if (textOutlineEnabled)
		{
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, x + 1, y + 1);
		}
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	private void drawPanelContent(Graphics2D graphics)
	{
		// Drawn top-to-bottom to match the fold-open reveal (see #render), which clips progressively
		// from the panel's top edge downward: caption, then name, then the bottom corner stats.
		graphics.setFont(captionFont);
		int captionX = (panelWidth - graphics.getFontMetrics().stringWidth(CAPTION_TEXT)) / 2;
		drawOutlinedString(graphics, CAPTION_TEXT, captionX, captionBaselineY, opaque(config.colourCaption()));

		int maxNameWidth = panelWidth - 2 * nameSideMargin;
		FittedName fittedName = fitName(graphics, current.getItemName(), nameFont, maxNameWidth, nameFontMinSize);
		graphics.setFont(fittedName.getFont());
		int nameX = (panelWidth - graphics.getFontMetrics().stringWidth(fittedName.getText())) / 2;
		drawOutlinedString(graphics, fittedName.getText(), nameX, nameBaselineY, tierColor(current.getTier()));

		drawProgressBar(graphics);

		FontMetrics cornerLabelMetrics = graphics.getFontMetrics(cornerLabelFont);
		FontMetrics cornerValueMetrics = graphics.getFontMetrics(cornerValueFont);

		Stat leftStat = resolveStatWithFallback(config.leftPanelStat().toPanelStat(), current);
		if (leftStat != null)
		{
			drawCornerStat(graphics, leftStat, cornerPaddingX, false, cornerLabelMetrics, cornerValueMetrics);
		}

		Stat rightStat = resolveStatWithFallback(config.rightPanelStat().toPanelStat(), current);
		if (rightStat != null)
		{
			// Right corner: label and each value line are independently right-aligned to the panel
			// edge, since the label is usually much shorter than the value.
			drawCornerStat(graphics, rightStat, panelWidth - cornerPaddingX, true, cornerLabelMetrics, cornerValueMetrics);
		}
	}

	/**
	 * Draws overall collection log completion as a filled track in the band the art's divider line
	 * used to occupy. See "This Plugin: Progress Bar" in AGENTS.md for why this is overall progress
	 * rather than per-page.
	 *
	 * <p>{@code fillRect} rather than a stroked or rounded shape on purpose: shape antialiasing is
	 * never enabled here and {@code STROKE_PURE} is set only in the SMOOTH text modes (see #render),
	 * so anything stroked would rasterize differently between text modes. Does not touch the
	 * composite - it inherits the panel's fold and fade, and setting its own would clobber the fade.
	 */
	private void drawProgressBar(Graphics2D graphics)
	{
		// The track is drawn either way: with the bar turned off it stands in for the divider line the
		// panel art used to carry, which looks the same as a bar at 0%.
		graphics.setColor(progressBarTrackColors.get(current.getTier()));
		graphics.fillRect(progressBarX, progressBarTopY, progressBarWidth, progressBarHeight);

		if (!config.showProgressBar())
		{
			return;
		}

		int obtained = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
		int total = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);

		// total is 0 before the varps have been populated (e.g. a preview fired on the login screen),
		// which would otherwise divide by zero - leave the empty track showing, since a bar at 0% still
		// looks deliberate.
		if (total <= 0)
		{
			return;
		}

		float progress = clamp01(obtained / (float) total);
		int filledWidth = Math.round(progressBarWidth * progress);
		if (filledWidth <= 0)
		{
			return;
		}

		graphics.setColor(tierColor(current.getTier()));
		graphics.fillRect(progressBarX, progressBarTopY, filledWidth, progressBarHeight);
	}

	/**
	 * @param edgeX the x coordinate {@code stat}'s label and each value line are anchored to - their
	 *              left edge if {@code rightAligned} is false, their right edge otherwise
	 */
	private void drawCornerStat(Graphics2D graphics, Stat stat, int edgeX, boolean rightAligned, FontMetrics labelMetrics, FontMetrics valueMetrics)
	{
		graphics.setFont(labelMetrics.getFont());
		// A no-op today - every label, including the derived ones, comes from a table written to fit
		// (see KillCountKind). Here so a longer one added later ellipsises rather than running under
		// the icon. Not shrunk the way the item name is:
		// the label sits beside a fixed-size value, and a mismatched pair reads as a bug.
		String label = truncate(graphics, stat.getLabel(), labelMetrics.getFont(), cornerTextMaxWidth);
		drawOutlinedString(graphics, label, rightAligned ? edgeX - labelMetrics.stringWidth(label) : edgeX, cornerLabelBaselineY, opaque(config.colourStatLabel()));

		// A stat with more than 1 value line (an ambiguous Drop rate - see PanelStat#DROP_RATE) uses
		// a smaller font and tighter line spacing so both still fit above the item name.
		boolean multiLine = stat.getValueLines().size() > 1;
		Font valueFont = multiLine ? cornerMultiValueFont : valueMetrics.getFont();
		FontMetrics activeValueMetrics = multiLine ? graphics.getFontMetrics(valueFont) : valueMetrics;
		int lineHeight = multiLine ? cornerMultiValueLineHeight : 0;

		graphics.setFont(valueFont);
		int valueY = multiLine ? cornerMultiValueFirstBaselineY : cornerValueBaselineY;
		for (String rawLine : stat.getValueLines())
		{
			// Only the multi-line case bothers truncating - a fraction's denominator has no
			// natural length cap, unlike the usual single-line stats.
			String line = multiLine ? truncate(graphics, rawLine, valueFont, cornerTextMaxWidth) : rawLine;
			drawOutlinedString(graphics, line, rightAligned ? edgeX - activeValueMetrics.stringWidth(line) : edgeX, valueY, stat.getValueColor());
			valueY += lineHeight;
		}
	}

	private void advance(long now)
	{
		if (current == null)
		{
			current = queue.pollFirst();
			if (current != null)
			{
				notificationStartMillis = now;
				// Fired here rather than at enqueue() time so playback is spaced out to match each
				// item's visual reveal, instead of a whole burst firing its sound at once.
				if (!config.bulkUnlockSfx() || current.isBatchStart())
				{
					soundManager.play(current.getTier());
				}
			}
			return;
		}

		if (config.previewTier() != PreviewTier.NONE)
		{
			// Held indefinitely - see the fadeAlpha branch in #render.
			return;
		}

		long totalMillis = FOLD_MILLIS + ICON_POP_MILLIS + config.overlayDisplaySeconds() * 1000L + FADE_MILLIS;
		if (now - notificationStartMillis >= totalMillis)
		{
			current = null;
		}
	}

	private static float clamp01(float value)
	{
		return Math.max(0f, Math.min(1f, value));
	}

	/**
	 * Standard parameterized back-ease-out: overshoots past 1 before settling exactly at 1 when
	 * t=1. {@code overshoot} controls how far past 1 it swings - a bigger value is a punchier pop.
	 */
	private static float easeOutBack(float t, float overshoot)
	{
		float c3 = overshoot + 1f;
		float p = t - 1f;
		return 1f + c3 * p * p * p + overshoot * p * p;
	}

	private static float easeInCubic(float t)
	{
		return t * t * t;
	}

	/**
	 * Fits the item name on a single line, shrinking the font (down to {@code minFontSize}) until it
	 * does. Anything still too wide at the minimum size is truncated with an ellipsis.
	 *
	 * <p>Never wraps. Only 28 of the ~1700 item names are wide enough to need it, and a second line
	 * would force everything below to reserve room for a line that almost never appears - a visible
	 * gap on every other popup. Shrinking those few keeps the layout below the name fixed.
	 */
	private static FittedName fitName(Graphics2D graphics, String text, Font baseFont, int maxWidth, float minFontSize)
	{
		Font font = baseFont;
		while (graphics.getFontMetrics(font).stringWidth(text) > maxWidth && font.getSize2D() > minFontSize)
		{
			font = font.deriveFont(font.getSize2D() - 1f);
		}

		return new FittedName(font, truncate(graphics, text, font, maxWidth));
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
	 * @return the primary stat, or the first stat down its fallback chain that's applicable to
	 *         {@code item} (e.g. Kill count picked but there's no correlated kill, so Completion is
	 *         tried) - or null if none of them are, in which case that side of the panel is simply
	 *         left blank.
	 */
	private Stat resolveStatWithFallback(PanelStat primary, PendingItem item)
	{
		PanelStat candidate = primary;
		while (candidate != PanelStat.NONE)
		{
			Stat stat = resolveStat(candidate, item);
			if (stat != null)
			{
				return stat;
			}
			candidate = fallbackFor(candidate);
		}
		return null;
	}

	/**
	 * Kill count and Drop rate are the stats that can be unavailable for an item (no correlated
	 * kill, or no known/unambiguous drop rate); each falls back to a stat that's always available.
	 * Everything else has no fallback.
	 */
	private static PanelStat fallbackFor(PanelStat stat)
	{
		switch (stat)
		{
			case KILL_COUNT:
				return PanelStat.RARITY;
			case DROP_RATE:
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
				boolean showAlch = config.valueDisplayMode() == ValueDisplayMode.HIGH_ALCH;
				int displayPrice = showAlch ? item.getAlchPrice() : item.getPrice();
				boolean displayHighAlch = showAlch || item.isHighAlch();
				String valueText = formatValue(displayPrice) + " gp" + (displayHighAlch ? " (HA)" : "");
				return new Stat("Value: ", List.of(valueText), opaque(config.colourStatValue()));
			case RARITY:
				if (item.getCompPercent() == null)
				{
					return null;
				}
				return new Stat("Wiki Comp%: ", List.of(String.format("%.1f%%", item.getCompPercent())), tierColor(item.getTier()));
			case KILL_COUNT:
				if (item.getKillCount() == null)
				{
					return null;
				}
				String killCountText = QuantityFormatter.formatNumber(item.getKillCount());
				// The label names what was actually counted - kills, harvests, deep delves - or the
				// source itself where that would still be ambiguous, as for a clue tier or a raid
				// mode (see KillCountKind). Count and kind always arrive together, so the fallback is
				// only a guard against a future caller supplying one without the other.
				KillCountKind killCountKind = item.getKillCountKind() != null ? item.getKillCountKind() : KillCountKind.KILLS;
				return new Stat(killCountKind.labelFor(item.getKillCountSource()), List.of(killCountText), opaque(config.colourStatValue()));
			case DROP_RATE:
				if (item.getDropProbability() != null)
				{
					return new Stat("Drop rate: ", List.of(formatFraction(item.getDropProbability())), opaque(config.colourStatValue()));
				}
				// No single known rate - show the rarest-to-most-common range across every tracked
				// source instead of hiding the stat, regardless of how many sources there are.
				List<DropRateResolver.SourceRate> ambiguous = item.getAmbiguousDropRates();
				if (ambiguous.isEmpty())
				{
					return null;
				}
				double minProbability = ambiguous.stream().mapToDouble(DropRateResolver.SourceRate::getProbability).min().getAsDouble();
				double maxProbability = ambiguous.stream().mapToDouble(DropRateResolver.SourceRate::getProbability).max().getAsDouble();
				String rangeText = minProbability == maxProbability
					? formatFraction(minProbability)
					: formatFraction(minProbability) + " - " + formatFraction(maxProbability);
				return new Stat("Drop rate: ", List.of(rangeText), opaque(config.colourStatValue()));
			case NONE:
			default:
				return null;
		}
	}

	private static String formatFraction(double dropProbability)
	{
		return "1/" + formatDenominator(Math.round(1 / dropProbability));
	}

	// Panel width doesn't fit long denominators (e.g. "1/313168") without clipping, so anything at or
	// above 10k is truncated to the nearest thousand - approximate rarity is all this stat is for.
	private static String formatDenominator(long denominator)
	{
		if (denominator < 10_000)
		{
			return Long.toString(denominator);
		}
		return (denominator / 1000) + "k";
	}

	// Long gp values (e.g. "2,147,483,647 gp") don't fit the panel either, so truncate to one decimal
	// of K/M/B once the value hits 100k+ - below that the plain number is short enough to fit as-is.
	private static String formatValue(int value)
	{
		long absValue = Math.abs((long) value);
		String sign = value < 0 ? "-" : "";
		if (absValue < 100_000)
		{
			return sign + absValue;
		}
		if (absValue < 1_000_000_000L)
		{
			return absValue < 1_000_000
				? sign + oneDecimal(absValue, 1_000) + "k"
				: sign + oneDecimal(absValue, 1_000_000) + "m";
		}
		return sign + oneDecimal(absValue, 1_000_000_000L) + "b";
	}

	private static String oneDecimal(long value, long unit)
	{
		return String.format("%.1f", value / (double) unit);
	}

	/**
	 * The configured colour for {@code tier}, used for both the panel art and the item name/Wiki Comp%
	 * text so they always read as the same tier.
	 */
	private Color tierColor(RarityTier tier)
	{
		switch (tier)
		{
			case COMMON:
				return opaque(config.colourCommonTier());
			case UNCOMMON:
				return opaque(config.colourUncommonTier());
			case RARE:
				return opaque(config.colourRareTier());
			case VERY_RARE:
				return opaque(config.colourVeryRareTier());
			case PET:
				return opaque(config.colourPetTier());
			default:
				return opaque(config.colourCommonTier());
		}
	}

	/**
	 * Drops any alpha the colour picker allowed the user to dial in - see {@link #RGB_MASK}.
	 */
	private static Color opaque(Color color)
	{
		return color.getAlpha() == 0xFF ? color : new Color(color.getRGB() & RGB_MASK);
	}

	@Value
	private static class PendingItem
	{
		String itemName;
		int itemId;
		RarityTier tier;
		int price;
		boolean highAlch;
		int alchPrice;
		Double compPercent;
		Integer killCount;
		KillCountKind killCountKind;
		String killCountSource;
		Double dropProbability;
		List<DropRateResolver.SourceRate> ambiguousDropRates;
		boolean batchStart;
	}

	@Value
	private static class FittedName
	{
		Font font;
		String text;
	}

	@Value
	private static class Stat
	{
		String label;
		List<String> valueLines;
		Color valueColor;
	}
}
