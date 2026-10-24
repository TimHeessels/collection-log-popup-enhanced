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
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.DoubleSummaryStatistics;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
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

	private static final String CAPTION_TEXT = "Collection Log";
	private static final int BASE_CAPTION_BASELINE_Y = 22;

	// The colour picker offers an alpha slider, but the panel art rebuilds every pixel from the
	// source art's own alpha, so a picked alpha can only ever apply to the text - which would leave
	// one config value meaning two different things. Alpha is stripped on read instead, everywhere.
	private static final int RGB_MASK = 0xFFFFFF;

	// Neutral body insets {left, top, right, bottom}, measured off the art: its outline has gaps a flood fill leaks through.
	private static final int[] NEUTRAL_PANEL_BODY_INSETS = {7, 7, 8, 8};
	private static final int[] NEUTRAL_ICON_BODY_INSETS = {10, 9, 9, 9};

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

	// Compact layout: one short panel, the icon unframed in a left column behind a divider, the
	// caption, name and stats stacked to its right, and the progress bar along the bottom.
	private static final int COMPACT_PANEL_HEIGHT = 92;
	private static final int COMPACT_ICON_CENTER_X = 35;
	private static final int COMPACT_ICON_CENTER_Y = 44;
	private static final int COMPACT_ICON_BOX_SIZE = 52;
	private static final int COMPACT_ICON_TARGET_SIZE = 44;
	private static final int COMPACT_DIVIDER_X = 66;
	private static final int COMPACT_DIVIDER_WIDTH = 2;
	private static final int COMPACT_DIVIDER_TOP_Y = 10;
	// Ends clear of the progress bar, which runs underneath it.
	private static final int COMPACT_DIVIDER_BOTTOM_Y = 78;
	private static final int COMPACT_TEXT_X = 76;
	private static final int COMPACT_RIGHT_MARGIN = 14;
	private static final int COMPACT_CAPTION_BASELINE_Y = 21;
	private static final int COMPACT_NAME_BASELINE_Y = 49;
	// Both stats on one line, labels in the game's small font - the narrowest that still reads.
	private static final int COMPACT_STATS_BASELINE_Y = 72;
	private static final int COMPACT_STAT_GAP = 12;
	private static final float COMPACT_STAT_LABEL_FONT_SIZE = 16f;
	private static final int COMPACT_PROGRESS_BAR_HEIGHT = 4;

	// Rarity effects start with the icon pop, fully faded in before the screenshot at the end of the open.
	static final long EFFECTS_START_MILLIS = FOLD_MILLIS;
	private static final long EFFECTS_FADE_IN_MILLIS = 300;

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
	// Drawn as-is, never recoloured - loaded up front so switching style needs no jar IO.
	private final Map<RarityTier, BufferedImage> neutralBackgrounds = new EnumMap<>(RarityTier.class);
	private final Map<RarityTier, BufferedImage> neutralIconFrames = new EnumMap<>(RarityTier.class);
	// Which pixels the background opacity applies to, per source image; the rest take the foreground's.
	private final Map<BufferedImage, boolean[]> bodyMasks = new IdentityHashMap<>();
	// What actually gets drawn - the source art recoloured to the configured per-tier colours.
	private final Map<RarityTier, BufferedImage> backgrounds = new EnumMap<>(RarityTier.class);
	private final Map<RarityTier, BufferedImage> iconFrames = new EnumMap<>(RarityTier.class);
	private final Map<RarityTier, BufferedImage> compactBackgrounds = new EnumMap<>(RarityTier.class);
	// The body inside each compact panel's frame, in art pixels: the progress bar runs along its bottom edge.
	private final Map<RarityTier, Rectangle> compactBodies = new EnumMap<>(RarityTier.class);
	// A step darker than the panel body: at the same shade the bar reads as a floating stripe rather
	// than a fill inside a recess. Cached alongside the art - same inputs, and otherwise per-frame.
	private final Map<RarityTier, Color> progressBarTrackColors = new EnumMap<>(RarityTier.class);
	// The accent colour nudged until it reads against the panel body (see PanelRecolorer#legibleOn):
	// a dark tier colour would otherwise draw the name barely brighter than the body it derives.
	private final Map<RarityTier, Color> nameColors = new EnumMap<>(RarityTier.class);

	// Only ever written/read from the client thread, so no synchronization is needed.
	private final Deque<PendingItem> queue = new ArrayDeque<>();

	private PendingItem current;
	private long notificationStartMillis;
	private final RarityEffects rarityEffects = new RarityEffects();
	// The centered location #render last set, to tell it apart from one the user dragged in.
	private Point lastAutoLocation;

	// Told the item name once a popup released by #releaseHeld has finished opening - the plugin
	// takes the delayed CoX collection log screenshot from it.
	@Setter
	private Consumer<String> releasedFullyOpenListener;

	// Scaled layout/font state, recomputed by #applyScale only when config.overlayScalePercent()
	// changes (rather than every frame) since Font#deriveFont isn't free to call 50x/sec for nothing.
	private int lastScalePercent = -1;
	private PanelLayout lastLayout;
	private boolean compact;
	// Cache key for the recoloured art: the five tier colours plus the background darkness (see
	// #readColours), so #render can detect a change cheaply; null until #applyColours has run.
	private int[] lastColours;
	private boolean textOutlineEnabled;
	// The configured opacities as 0-1 factors, refreshed alongside the art by #applyColours.
	private float backgroundOpacity = 1f;
	private float foregroundOpacity = 1f;
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
	private int dividerX;
	private int dividerY;
	private int dividerWidth;
	private int dividerHeight;
	private int textX;
	private int textMaxWidth;
	private int statsBaselineY;
	private int statGap;
	private float nameFontMinSize;
	private Font cornerLabelFont;
	private Font cornerValueFont;
	private Font cornerMultiValueFont;
	private Font nameFont;
	private Font captionFont;
	private Font compactStatLabelFont;

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
		setMovable(true);

		loadArt(PanelStyle.COLORFUL, sourceBackgrounds, sourceIconFrames);
		loadArt(PanelStyle.NEUTRAL, neutralBackgrounds, neutralIconFrames);
		for (RarityTier tier : RarityTier.values())
		{
			bodyMasks.put(sourceBackgrounds.get(tier), PanelRecolorer.floodFillBodyMask(sourceBackgrounds.get(tier)));
			bodyMasks.put(sourceIconFrames.get(tier), PanelRecolorer.floodFillBodyMask(sourceIconFrames.get(tier)));
			bodyMasks.put(neutralBackgrounds.get(tier), insetBodyMask(neutralBackgrounds.get(tier), NEUTRAL_PANEL_BODY_INSETS));
			bodyMasks.put(neutralIconFrames.get(tier), insetBodyMask(neutralIconFrames.get(tier), NEUTRAL_ICON_BODY_INSETS));
		}

		applyScale(config.overlayScalePercent(), config.panelLayout());
		applyColours(readColours());
	}

	/**
	 * Recomputes every scaled layout constant and re-derives each font at its scaled point size, so
	 * text is rasterized natively at the target size rather than blurred by stretching a fixed-size
	 * raster. Cheap, but only worth doing when the configured scale actually changes (see #render).
	 */
	private void applyScale(int scalePercent, PanelLayout layout)
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

		compact = layout == PanelLayout.COMPACT;
		if (compact)
		{
			applyCompactScale(scale);
		}

		lastScalePercent = scalePercent;
		lastLayout = layout;
	}

	/** Overrides the extended geometry {@link #applyScale} just set with the compact layout's. */
	private void applyCompactScale(float scale)
	{
		panelHeight = Math.round(COMPACT_PANEL_HEIGHT * scale);
		iconCanvasSize = Math.round(COMPACT_ICON_BOX_SIZE * scale);
		iconX = Math.round(COMPACT_ICON_CENTER_X * scale) - iconCanvasSize / 2;
		iconY = Math.round(COMPACT_ICON_CENTER_Y * scale) - iconCanvasSize / 2;
		iconTargetSize = Math.round(COMPACT_ICON_TARGET_SIZE * scale);

		dividerX = Math.round(COMPACT_DIVIDER_X * scale);
		dividerY = Math.round(COMPACT_DIVIDER_TOP_Y * scale);
		dividerWidth = Math.max(1, Math.round(COMPACT_DIVIDER_WIDTH * scale));
		dividerHeight = Math.round(COMPACT_DIVIDER_BOTTOM_Y * scale) - dividerY;

		textX = Math.round(COMPACT_TEXT_X * scale);
		textMaxWidth = panelWidth - textX - Math.round(COMPACT_RIGHT_MARGIN * scale);
		captionBaselineY = Math.round(COMPACT_CAPTION_BASELINE_Y * scale);
		nameBaselineY = Math.round(COMPACT_NAME_BASELINE_Y * scale);
		statsBaselineY = Math.round(COMPACT_STATS_BASELINE_Y * scale);
		statGap = Math.round(COMPACT_STAT_GAP * scale);
		compactStatLabelFont = FontManager.getRunescapeSmallFont().deriveFont(COMPACT_STAT_LABEL_FONT_SIZE * scale);

		// The rest of the bar's geometry depends on the tier's frame - see #placeCompactProgressBar.
		progressBarHeight = Math.max(1, Math.round(COMPACT_PROGRESS_BAR_HEIGHT * scale));
	}

	// Purely a change detector - the rebuild re-reads each colour by tier (see #applyColours), so
	// this array's order carries no meaning beyond being stable frame to frame.
	private int[] readColours()
	{
		RarityTier[] tiers = RarityTier.values();
		int[] key = new int[tiers.length + 4];
		for (int i = 0; i < tiers.length; i++)
		{
			key[i] = tierColor(tiers[i]).getRGB();
		}
		// Part of the cache key, not colours: changing any of these re-derives every background.
		key[tiers.length] = config.backgroundDarkness();
		key[tiers.length + 1] = config.panelStyle().ordinal();
		key[tiers.length + 2] = config.backgroundOpacity();
		key[tiers.length + 3] = config.foregroundOpacity();
		return key;
	}

	/**
	 * Rebuilds the drawn panel/icon art for every tier from the pristine sources. ~280k pixels across
	 * ten images, so this runs on startup and colour changes only (see #render) - never per frame.
	 */
	private void applyColours(int[] colours)
	{
		int darkness = config.backgroundDarkness();
		boolean neutral = config.panelStyle() == PanelStyle.NEUTRAL;
		backgroundOpacity = config.backgroundOpacity() / 100f;
		foregroundOpacity = config.foregroundOpacity() / 100f;
		for (RarityTier tier : RarityTier.values())
		{
			if (neutral)
			{
				backgrounds.put(tier, withOpacity(neutralBackgrounds.get(tier), neutralBackgrounds.get(tier)));
				iconFrames.put(tier, withOpacity(neutralIconFrames.get(tier), neutralIconFrames.get(tier)));
				progressBarTrackColors.put(tier,
					PanelRecolorer.deriveBackground(Color.WHITE, Math.max(1, Math.round(darkness * PROGRESS_BAR_TRACK_DARKNESS_SCALE))));
				nameColors.put(tier, Color.WHITE);
				continue;
			}

			Color border = tierColor(tier);
			Color background = PanelRecolorer.deriveBackground(border, darkness);
			BufferedImage panel = sourceBackgrounds.get(tier);
			BufferedImage frame = sourceIconFrames.get(tier);
			backgrounds.put(tier, withOpacity(panel, PanelRecolorer.recolor(panel, tier, border, background)));
			iconFrames.put(tier, withOpacity(frame, PanelRecolorer.recolor(frame, tier, border, background)));
			progressBarTrackColors.put(tier,
				PanelRecolorer.deriveBackground(border, Math.max(1, Math.round(darkness * PROGRESS_BAR_TRACK_DARKNESS_SCALE))));
			nameColors.put(tier, PanelRecolorer.legibleOn(border, background));
		}
		for (RarityTier tier : RarityTier.values())
		{
			BufferedImage source = (neutral ? neutralBackgrounds : sourceBackgrounds).get(tier);
			compactBackgrounds.put(tier, withoutMiddleRows(backgrounds.get(tier), COMPACT_PANEL_HEIGHT));
			compactBodies.put(tier, compactBody(source, bodyMasks.get(source)));
		}
		lastColours = colours;
	}

	/** The bounds of {@code source}'s body mask, moved to where the compact cut leaves its bottom edge. */
	private static Rectangle compactBody(BufferedImage source, boolean[] mask)
	{
		int width = source.getWidth();
		int left = width;
		int right = -1;
		int top = source.getHeight();
		int bottom = -1;
		for (int i = 0; i < mask.length; i++)
		{
			if (mask[i])
			{
				left = Math.min(left, i % width);
				right = Math.max(right, i % width);
				top = Math.min(top, i / width);
				bottom = Math.max(bottom, i / width);
			}
		}
		int bottomInset = source.getHeight() - 1 - bottom;
		return new Rectangle(left, top, right - left + 1, COMPACT_PANEL_HEIGHT - bottomInset - top);
	}

	// Cut rather than scaled, so the frame and the Neutral corner studs stay pixel-exact.
	private static BufferedImage withoutMiddleRows(BufferedImage art, int height)
	{
		int top = height / 2;
		int bottom = height - top;
		BufferedImage cut = new BufferedImage(art.getWidth(), height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = cut.createGraphics();
		graphics.drawImage(art.getSubimage(0, 0, art.getWidth(), top), 0, 0, null);
		graphics.drawImage(art.getSubimage(0, art.getHeight() - bottom, art.getWidth(), bottom), 0, top, null);
		graphics.dispose();
		return cut;
	}

	/** {@code art} with the configured opacities applied, split by {@code source}'s body mask. */
	private BufferedImage withOpacity(BufferedImage source, BufferedImage art)
	{
		if (backgroundOpacity >= 1f && foregroundOpacity >= 1f)
		{
			return art;
		}
		return PanelRecolorer.applyOpacity(art, bodyMasks.get(source), backgroundOpacity, foregroundOpacity);
	}

	private static boolean[] insetBodyMask(BufferedImage image, int[] insets)
	{
		return PanelRecolorer.insetBodyMask(image.getWidth(), image.getHeight(), insets[0], insets[1], insets[2], insets[3]);
	}

	private static void loadArt(PanelStyle style, Map<RarityTier, BufferedImage> panels, Map<RarityTier, BufferedImage> frames)
	{
		panels.put(RarityTier.COMMON, loadImage(style.resource("Backgrounds/BackgroundPanel1.png")));
		panels.put(RarityTier.UNCOMMON, loadImage(style.resource("Backgrounds/BackgroundPanel2.png")));
		panels.put(RarityTier.RARE, loadImage(style.resource("Backgrounds/BackgroundPanel3.png")));
		panels.put(RarityTier.VERY_RARE, loadImage(style.resource("Backgrounds/BackgroundPanel4.png")));
		panels.put(RarityTier.PET, loadImage(style.resource("Backgrounds/BackgroundPanelPet.png")));

		frames.put(RarityTier.COMMON, loadImage(style.resource("Icons/IconPanel1.png")));
		frames.put(RarityTier.UNCOMMON, loadImage(style.resource("Icons/IconPanel2.png")));
		frames.put(RarityTier.RARE, loadImage(style.resource("Icons/IconPanel3.png")));
		frames.put(RarityTier.VERY_RARE, loadImage(style.resource("Icons/IconPanel4.png")));
		frames.put(RarityTier.PET, loadImage(style.resource("Icons/IconPanelPet.png")));
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

	public void enqueue(String itemName, int itemId, RarityTier tier, long price, boolean highAlch,
		Double compPercent, Integer killCount, KillCountKind killCountKind, String killCountSource,
		Double dropProbability, List<DropRateResolver.SourceRate> ambiguousDropRates)
	{
		enqueue(itemName, itemId, tier, price, highAlch, compPercent, killCount, killCountKind,
			killCountSource, null, dropProbability, ambiguousDropRates);
	}

	public void enqueue(String itemName, int itemId, RarityTier tier, long price, boolean highAlch,
		Double compPercent, Integer killCount, KillCountKind killCountKind, String killCountSource,
		Integer secondaryKillCount, Double dropProbability, List<DropRateResolver.SourceRate> ambiguousDropRates)
	{
		enqueue(itemName, itemId, tier, price, highAlch, compPercent, killCount, killCountKind,
			killCountSource, secondaryKillCount, dropProbability, ambiguousDropRates, false);
	}

	/**
	 * @param held holds the item back until {@link #releaseHeld()} instead of showing it in turn -
	 *             see CollectionLogPopupEnhancedPlugin's CoX chest handling.
	 */
	public void enqueue(String itemName, int itemId, RarityTier tier, long price, boolean highAlch,
		Double compPercent, Integer killCount, KillCountKind killCountKind, String killCountSource,
		Integer secondaryKillCount, Double dropProbability, List<DropRateResolver.SourceRate> ambiguousDropRates,
		boolean held)
	{
		// The overlay was fully idle right before this item arrived, so it's the first of a fresh
		// batch - the only one that plays a sound when bulkUnlockSfx is on. A held item can't claim
		// this: it shows later, so #releaseHeld works it out again at the point it's let through.
		boolean batchStart = !held && queue.isEmpty() && current == null;
		queue.addLast(new PendingItem(itemName, itemId, tier, price, highAlch, compPercent, killCount,
			killCountKind, killCountSource, secondaryKillCount, dropProbability, ambiguousDropRates, batchStart, held, false));
	}

	public void clear()
	{
		queue.clear();
		current = null;
	}

	/**
	 * Lets every held item through, in the order it was unlocked.
	 */
	public void releaseHeld()
	{
		// Whether the first released item opens a batch can't be decided at enqueue time - items may
		// have come and gone while it was held - so it's recomputed here against the state now.
		boolean batchStart = current == null && queue.stream().allMatch(PendingItem::isHeld);
		for (PendingItem item : queue)
		{
			if (!item.isHeld())
			{
				continue;
			}
			item.setHeld(false);
			item.setReleased(true);
			item.setBatchStart(batchStart);
			batchStart = false;
		}
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
		// Still advanced in Audio only: the queue is what plays and spaces out the sounds.
		advance(now);
		if (config.panelStyle() == PanelStyle.AUDIO_ONLY)
		{
			return null;
		}

		int scalePercent = config.overlayScalePercent();
		PanelLayout layout = config.panelLayout();
		if (scalePercent != lastScalePercent || layout != lastLayout)
		{
			applyScale(scalePercent, layout);
		}

		int[] colours = readColours();
		if (!Arrays.equals(colours, lastColours))
		{
			applyColours(colours);
		}

		// Compared by value, not identity: an alt-click that doesn't move still saves our own point.
		Point placed = getPreferredLocation();
		boolean userPlaced = getPreferredPosition() != null || (placed != null && !placed.equals(lastAutoLocation));
		if (!userPlaced)
		{
			// Centers on the viewport rect, not the canvas: fixed/classic offsets it left of the sidebar,
			// and the Fixed Resizable Hybrid plugin narrows it in resizable mode.
			int viewportWidth = client.getViewportWidth();
			int viewportCenterX = viewportWidth > 0
				? client.getViewportXOffset() + viewportWidth / 2
				: client.getRealDimensions().width / 2;
			lastAutoLocation = new Point(viewportCenterX - (panelWidth / 2), TOP_MARGIN);
			setPreferredLocation(lastAutoLocation);
		}

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
		// The art carries its opacities baked in (see #applyColours); text is scaled here instead.
		setAlpha(graphics, originalComposite, fadeAlpha);
		graphics.drawImage((compact ? compactBackgrounds : backgrounds).get(current.getTier()), 0, 0, panelWidth, panelHeight, null);
		setAlpha(graphics, originalComposite, fadeAlpha * foregroundOpacity);
		if (compact)
		{
			drawCompactPanelContent(graphics, originalComposite, fadeAlpha);
		}
		else
		{
			drawPanelContent(graphics, originalComposite, fadeAlpha);
		}

		graphics.setClip(originalClip);
		graphics.setComposite(originalComposite);
		graphics.setTransform(base);

		RarityEffects.Frame effects = effectsFrame(now, elapsed, fadeAlpha);
		if (effects != null)
		{
			rarityEffects.drawUnderIcon(graphics, effects);
		}

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

			if (!compact)
			{
				setAlpha(graphics, originalComposite, iconAlpha);
				graphics.drawImage(iconFrames.get(current.getTier()), iconX, iconY, iconCanvasSize, iconCanvasSize, null);
			}
			setAlpha(graphics, originalComposite, iconAlpha * foregroundOpacity);
			drawItemSprite(graphics, itemSprite);
			graphics.setComposite(originalComposite);
			graphics.setTransform(base);
		}

		if (effects != null)
		{
			rarityEffects.drawOverIcon(graphics, effects);
		}

		return new Dimension(panelWidth, totalHeight());
	}

	// Extended's height includes the icon frame's bottom half, which straddles the panel's bottom edge.
	private int totalHeight()
	{
		return compact ? panelHeight : panelHeight + iconCanvasSize / 2;
	}

	/**
	 * @return this frame's rarity effects, or null when there are none to draw: turned off, not the
	 *         colourful style, a tier without any, or the icon not popping in yet
	 */
	private RarityEffects.Frame effectsFrame(long now, long elapsed, float fadeAlpha)
	{
		RarityTier tier = current.getTier();
		RarityEffectsLevel level = config.rarityEffects();
		if (config.panelStyle() != PanelStyle.COLORFUL || !RarityEffects.hasEffects(tier, level)
			|| elapsed < EFFECTS_START_MILLIS)
		{
			return null;
		}

		float fadeIn = clamp01((elapsed - EFFECTS_START_MILLIS) / (float) EFFECTS_FADE_IN_MILLIS);
		float scale = lastScalePercent / 100f;
		return RarityEffects.Frame.builder()
			.tier(tier)
			.level(level)
			.millis(elapsed - EFFECTS_START_MILLIS)
			.alpha(fadeAlpha * foregroundOpacity * fadeIn * level.getIntensity())
			.scale(scale)
			.panelWidth(panelWidth)
			.panelHeight(panelHeight)
			.totalHeight(totalHeight())
			.iconCenterX(iconX + iconCanvasSize / 2f)
			.iconCenterY(iconY + iconCanvasSize / 2f)
			.iconSize(iconCanvasSize)
			.color(RarityEffects.effectColor(tierColor(tier)))
			.seed(current.getItemId() * 31L + notificationStartMillis)
			.room(roomOnScreen(Math.round(RarityEffects.BASE_MAX_OVERFLOW * scale)))
			.build();
	}

	/**
	 * How far effects may spill past the panel on each side before leaving the screen, capped at
	 * {@code max}. Taken from the panel's bounds as last laid out - it only moves when dragged.
	 */
	private Insets roomOnScreen(int max)
	{
		Rectangle bounds = getBounds();
		int canvasWidth = client.getCanvasWidth();
		int canvasHeight = client.getCanvasHeight();
		if (bounds == null || canvasWidth <= 0 || canvasHeight <= 0)
		{
			return new Insets(0, 0, 0, 0);
		}
		return new Insets(
			clampRoom(bounds.y, max),
			clampRoom(bounds.x, max),
			clampRoom(canvasHeight - (bounds.y + totalHeight()), max),
			clampRoom(canvasWidth - (bounds.x + panelWidth), max));
	}

	private static int clampRoom(int room, int max)
	{
		return Math.max(0, Math.min(max, room));
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

	private static void setAlpha(Graphics2D graphics, Composite original, float alpha)
	{
		graphics.setComposite(alpha < 1f ? AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)) : original);
	}

	/**
	 * @param fadeAlpha the panel's fade, which the text composite already includes - passed on so the
	 *                  progress track can swap to the background opacity for its own fill
	 */
	private void drawPanelContent(Graphics2D graphics, Composite original, float fadeAlpha)
	{
		// Drawn top-to-bottom to match the fold-open reveal (see #render), which clips progressively
		// from the panel's top edge downward: caption, then name, then the bottom corner stats.
		graphics.setFont(captionFont);
		String caption = caption();
		int captionX = (panelWidth - graphics.getFontMetrics().stringWidth(caption)) / 2;
		drawOutlinedString(graphics, caption, captionX, captionBaselineY, opaque(config.colourCaption()));

		int maxNameWidth = panelWidth - 2 * nameSideMargin;
		FittedName fittedName = fitName(graphics, current.getItemName(), nameFont, maxNameWidth, nameFontMinSize);
		graphics.setFont(fittedName.getFont());
		int nameX = (panelWidth - graphics.getFontMetrics().stringWidth(fittedName.getText())) / 2;
		drawOutlinedString(graphics, fittedName.getText(), nameX, nameBaselineY, nameColors.get(current.getTier()));

		drawProgressBar(graphics, original, fadeAlpha);

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

	// Neutral art carries no tier colour, so the caption names the tier instead.
	private String caption()
	{
		return config.panelStyle() == PanelStyle.NEUTRAL
			? current.getTier().getLabel() + " " + CAPTION_TEXT
			: CAPTION_TEXT;
	}

	private void drawCompactPanelContent(Graphics2D graphics, Composite original, float fadeAlpha)
	{
		RarityTier tier = current.getTier();
		int textRight = textX + textMaxWidth;

		graphics.setFont(captionFont);
		drawOutlinedString(graphics, caption(), textX, captionBaselineY, opaque(config.colourCaption()));

		FittedName fittedName = fitName(graphics, current.getItemName(), nameFont, textMaxWidth, nameFontMinSize);
		graphics.setFont(fittedName.getFont());
		drawOutlinedString(graphics, fittedName.getText(), textX, nameBaselineY, nameColors.get(tier));

		drawCompactStats(graphics, textRight);

		// The divider is part of the background, like the progress track drawn after it.
		setAlpha(graphics, original, fadeAlpha * backgroundOpacity);
		graphics.setColor(progressBarTrackColors.get(tier));
		graphics.fillRect(dividerX, dividerY, dividerWidth, dividerHeight);
		setAlpha(graphics, original, fadeAlpha * foregroundOpacity);

		// Unlike extended, there is no divider line for an empty track to stand in for.
		if (config.showProgressBar())
		{
			placeCompactProgressBar(tier);
			drawProgressBar(graphics, original, fadeAlpha);
		}
	}

	/**
	 * Draws the left stat from the text column's left edge and the right one against its right edge,
	 * each as "label value" on the same baseline.
	 *
	 * <p>The widest real pair ("Beginner caskets 12,345" with a two-source drop range) cannot fit even
	 * in the small font, so where the pair overflows the right stat tries its compact value, then drops
	 * its label (a drop rate or gp value reads without it) with exact digits, then compact, then truncates.
	 */
	private void drawCompactStats(Graphics2D graphics, int textRight)
	{
		Stat leftStat = resolveStatWithFallback(config.leftPanelStat().toPanelStat(), current);
		Stat rightStat = resolveStatWithFallback(config.rightPanelStat().toPanelStat(), current);
		FontMetrics labelMetrics = graphics.getFontMetrics(compactStatLabelFont);
		FontMetrics valueMetrics = graphics.getFontMetrics(cornerValueFont);

		String leftLabel = leftStat != null ? inlineLabel(leftStat) : "";
		String leftValue = leftStat != null ? String.join(" ", leftStat.getValueLines()) : "";
		int leftWidth = labelMetrics.stringWidth(leftLabel) + valueMetrics.stringWidth(leftValue);
		if (leftStat != null)
		{
			drawCompactStat(graphics, leftLabel, leftValue, textX, leftStat.getValueColor());
		}
		if (rightStat == null)
		{
			return;
		}

		int available = textMaxWidth - (leftStat != null ? leftWidth + statGap : 0);
		if (available <= 0)
		{
			return;
		}
		String exact = String.join(" ", rightStat.getValueLines());
		String shorter = rightStat.getCompactValueLines() != null ? String.join(" ", rightStat.getCompactValueLines()) : exact;
		String rightLabel = inlineLabel(rightStat);
		String rightValue = exact;
		// The label outranks exact digits, but once it is dropped the exact value gets another try.
		for (String[] candidate : new String[][]{{rightLabel, exact}, {rightLabel, shorter}, {"", exact}, {"", shorter}})
		{
			rightLabel = candidate[0];
			rightValue = candidate[1];
			if (labelMetrics.stringWidth(rightLabel) + valueMetrics.stringWidth(rightValue) <= available)
			{
				break;
			}
		}
		rightValue = truncate(graphics, rightValue, cornerValueFont, available);
		int rightWidth = labelMetrics.stringWidth(rightLabel) + valueMetrics.stringWidth(rightValue);
		drawCompactStat(graphics, rightLabel, rightValue, textRight - rightWidth, rightStat.getValueColor());
	}

	private void drawCompactStat(Graphics2D graphics, String label, String value, int x, Color valueColor)
	{
		graphics.setFont(compactStatLabelFont);
		drawOutlinedString(graphics, label, x, statsBaselineY, opaque(config.colourStatLabel()));
		x += graphics.getFontMetrics().stringWidth(label);
		graphics.setFont(cornerValueFont);
		drawOutlinedString(graphics, value, x, statsBaselineY, valueColor);
	}

	// "Obtained by: " -> "Obtained by ": the font change already sets the label off from its value.
	private static String inlineLabel(Stat stat)
	{
		String label = stat.getLabel().trim();
		return (label.endsWith(":") ? label.substring(0, label.length() - 1) : label) + " ";
	}

	/** Lays the bar along the bottom of the tier's panel body, edge to edge inside the frame. */
	private void placeCompactProgressBar(RarityTier tier)
	{
		Rectangle body = compactBodies.get(tier);
		float scaleX = panelWidth / (float) compactBackgrounds.get(tier).getWidth();
		float scaleY = panelHeight / (float) COMPACT_PANEL_HEIGHT;
		progressBarX = Math.round(body.x * scaleX);
		progressBarWidth = Math.round((body.x + body.width) * scaleX) - progressBarX;
		progressBarTopY = Math.round((body.y + body.height) * scaleY) - progressBarHeight;
	}


	/**
	 * Draws overall collection log completion as a filled track in the band the art's divider line
	 * used to occupy. See "This Plugin: Progress Bar" in AGENTS.md for why this is overall progress
	 * rather than per-page.
	 *
	 * <p>{@code fillRect} rather than a stroked or rounded shape on purpose: shape antialiasing is
	 * never enabled here and {@code STROKE_PURE} is set only in the SMOOTH text modes (see #render),
	 * so anything stroked would rasterize differently between text modes. The track is part of the
	 * background and the fill part of the foreground, so each takes that opacity times the fade.
	 * Leaves the composite at the foreground's for the text drawn after it.
	 */
	private void drawProgressBar(Graphics2D graphics, Composite original, float fadeAlpha)
	{
		// The track is drawn either way: with the bar turned off it stands in for the divider line the
		// panel art used to carry, which looks the same as a bar at 0%.
		setAlpha(graphics, original, fadeAlpha * backgroundOpacity);
		graphics.setColor(progressBarTrackColors.get(current.getTier()));
		graphics.fillRect(progressBarX, progressBarTopY, progressBarWidth, progressBarHeight);
		setAlpha(graphics, original, fadeAlpha * foregroundOpacity);

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

		graphics.setColor(accentColor(current.getTier()));
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
		// Measured against the real font rather than guessed from length: the compact form loses
		// digits, so it is only worth reaching for when the exact one genuinely would not fit.
		List<String> valueLines = fittingValueLines(graphics, stat);
		boolean multiLine = valueLines.size() > 1;
		Font valueFont = multiLine ? cornerMultiValueFont : valueMetrics.getFont();
		FontMetrics activeValueMetrics = multiLine ? graphics.getFontMetrics(valueFont) : valueMetrics;
		int lineHeight = multiLine ? cornerMultiValueLineHeight : 0;

		graphics.setFont(valueFont);
		int valueY = multiLine ? cornerMultiValueFirstBaselineY : cornerValueBaselineY;
		for (String rawLine : valueLines)
		{
			// Only the multi-line case bothers truncating - a fraction's denominator has no
			// natural length cap, unlike the usual single-line stats.
			String line = multiLine ? truncate(graphics, rawLine, valueFont, cornerTextMaxWidth) : rawLine;
			drawOutlinedString(graphics, line, rightAligned ? edgeX - activeValueMetrics.stringWidth(line) : edgeX, valueY, stat.getValueColor());
			valueY += lineHeight;
		}
	}

	/**
	 * @return the stat's compact value lines where its exact ones would overflow the corner and a
	 *         compact form exists, else the exact ones. Only the single-line case is checked - a
	 *         multi-line stat is already truncated per line as it is drawn.
	 */
	private List<String> fittingValueLines(Graphics2D graphics, Stat stat)
	{
		List<String> exact = stat.getValueLines();
		if (stat.getCompactValueLines() == null || exact.size() != 1)
		{
			return exact;
		}
		FontMetrics metrics = graphics.getFontMetrics(cornerValueFont);
		return metrics.stringWidth(exact.get(0)) <= cornerTextMaxWidth ? exact : stat.getCompactValueLines();
	}

	/**
	 * Skips past held items rather than stopping at the head: the queue is FIFO, so a CoX item
	 * waiting on the chest would otherwise block every later unlock behind it.
	 */
	private PendingItem pollFirstUnheld()
	{
		for (Iterator<PendingItem> it = queue.iterator(); it.hasNext(); )
		{
			PendingItem item = it.next();
			if (!item.isHeld())
			{
				it.remove();
				return item;
			}
		}
		return null;
	}

	private void advance(long now)
	{
		if (current == null)
		{
			current = pollFirstUnheld();
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

		// Fully open: the fold has finished and the icon has popped in.
		if (current.isReleased() && now - notificationStartMillis >= FOLD_MILLIS + ICON_POP_MILLIS)
		{
			current.setReleased(false);
			if (releasedFullyOpenListener != null)
			{
				releasedFullyOpenListener.accept(current.getItemName());
			}
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
				String valueText = formatValue(item.getPrice()) + " gp" + (item.isHighAlch() ? " (HA)" : "");
				return new Stat("Value: ", List.of(valueText), opaque(config.colourStatValue()));
			case RARITY:
				if (item.getCompPercent() == null)
				{
					return null;
				}
				return new Stat("Obtained by: ", List.of(String.format("%.1f%%", item.getCompPercent())), opaque(config.colourStatValue()));
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
				if (item.getSecondaryKillCount() != null)
				{
					killCountText += " (" + QuantityFormatter.formatNumber(item.getSecondaryKillCount()) + ")";
				}
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
				// Both ends of every candidate, so a source with a " (max)" twin contributes its
				// range rather than only its base rate.
				DoubleSummaryStatistics stats = ambiguous.stream()
					.flatMap(rate -> rate.probabilities().stream())
					.mapToDouble(Double::doubleValue)
					.summaryStatistics();
				// Ordered by rate, not by which end the dataset calls "max" - for a couple of items
				// (Little Nightmare, Zalcano shard) the max-scale rate is the rarer one, so taking
				// the data's own order would render a range that reads backwards.
				double commonest = stats.getMax();
				double rarest = stats.getMin();
				// Commonest first: the denominators then run upwards ("1/540 - 1/1350"), which is
				// how a rate range is written everywhere else.
				if (commonest == rarest)
				{
					return new Stat("Drop rate: ", List.of(formatFraction(commonest)), opaque(config.colourStatValue()));
				}
				// Exact digits by default; the compact form is only reached if the exact one would
				// run under the icon, which the widest real range does by a few px.
				String rangeText = formatFraction(commonest) + " - " + formatFraction(rarest);
				String compactRangeText = formatCompactFraction(commonest) + " - " + formatCompactFraction(rarest);
				return new Stat("Drop rate: ", List.of(rangeText), opaque(config.colourStatValue()),
					List.of(compactRangeText));
			case NONE:
			default:
				return null;
		}
	}

	private static String formatFraction(double dropProbability)
	{
		return "1/" + formatDenominator(Math.round(1 / dropProbability));
	}

	// Same fraction, fewer characters: a 4-digit denominator becomes one decimal of k ("1/1232" ->
	// "1/1.2k"), which is what buys the width back on a range. Only used when the exact form would
	// overflow, so the precision is lost on the few widest ranges rather than on every one.
	private static String formatCompactFraction(double dropProbability)
	{
		long denominator = Math.round(1 / dropProbability);
		if (denominator < 1000 || denominator >= 10_000)
		{
			return "1/" + formatDenominator(denominator);
		}
		String thousands = BigDecimal.valueOf(denominator, 3)
			.setScale(1, RoundingMode.HALF_UP)
			.stripTrailingZeros()
			.toPlainString();
		return "1/" + thousands + "k";
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
	private static String formatValue(long value)
	{
		long absValue = Math.abs(value);
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
	 * The progress fill colour: the tier colour, or plain white on the neutral style. The item name
	 * starts from the same colour but is contrast-adjusted (see #nameColors).
	 */
	private Color accentColor(RarityTier tier)
	{
		return config.panelStyle() == PanelStyle.NEUTRAL ? Color.WHITE : tierColor(tier);
	}

	/**
	 * The configured colour for {@code tier}: the colourful panel art, and via #accentColor the progress
	 * fill and (contrast-adjusted) name, so they always read as the same tier.
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

	// Not @Value like the others here: #releaseHeld rewrites batchStart, held and released after
	// construction, so only those carry a setter and the rest stay read-only.
	@Getter
	@AllArgsConstructor
	private static class PendingItem
	{
		private final String itemName;
		private final int itemId;
		private final RarityTier tier;
		private final long price;
		private final boolean highAlch;
		private final Double compPercent;
		private final Integer killCount;
		private final KillCountKind killCountKind;
		private final String killCountSource;
		// Shown in brackets beside killCount where the activity has two counts at once - Doom's
		// deep delves against its total. Null for every other source.
		private final Integer secondaryKillCount;
		private final Double dropProbability;
		private final List<DropRateResolver.SourceRate> ambiguousDropRates;
		@Setter
		private boolean batchStart;
		@Setter
		private boolean held;
		// Let through by #releaseHeld and not yet reported to releasedFullyOpenListener.
		@Setter
		private boolean released;
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
		// A shorter rendering of the same value, used only when valueLines would not fit (see
		// drawCornerStat). Null where there is no shorter form worth showing.
		List<String> compactValueLines;

		Stat(String label, List<String> valueLines, Color valueColor)
		{
			this(label, valueLines, valueColor, null);
		}

		Stat(String label, List<String> valueLines, Color valueColor, List<String> compactValueLines)
		{
			this.label = label;
			this.valueLines = valueLines;
			this.valueColor = valueColor;
			this.compactValueLines = compactValueLines;
		}
	}
}
