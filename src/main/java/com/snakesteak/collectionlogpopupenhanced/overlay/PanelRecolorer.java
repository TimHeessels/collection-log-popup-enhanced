package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Recolors the bundled panel/icon-frame art to user-configured border and background colors.
 *
 * <p>The bundled PNGs are single-hue: every chromatic pixel of a given tier sits inside a ~2 degree
 * hue band, and splits cleanly into two families by brightness - a bright <i>accent</i> (the border
 * stripe and the divider line) and a dark <i>fill</i> (the panel body). Everything else is either
 * the pure black outline or an antialiasing step between those two families.
 *
 * <p>Recoloring therefore only needs to remap hue/saturation/brightness per pixel, relative to its
 * family's reference shade. That deliberately leaves geometry and alpha untouched, which matters:
 * the five icon frames are five genuinely different designs (plain, corner brackets, scalloped,
 * dashed, scrollwork) carried entirely by their outline and antialiased edges, so any resampling or
 * alpha thresholding here would visibly degrade them.
 */
final class PanelRecolorer
{
	/** Saturation below which a pixel carries no usable hue of its own. */
	private static final float ACHROMATIC_SATURATION = 0.06f;

	/**
	 * Achromatic pixels darker than this are the black outline: structural, and passed through
	 * untouched so each design's silhouette stays pixel-exact. Achromatic pixels *brighter* than this
	 * are COMMON's white accent, which is a tier colour and must still recolour - so brightness, not
	 * saturation alone, is what separates "structure" from "unsaturated artwork".
	 */
	private static final float OUTLINE_MAX_BRIGHTNESS = 0.5f;

	/** Guards the ratio divisions below against a zero-saturation/brightness reference. */
	private static final float MIN_DIVISOR = 0.001f;

	/**
	 * How much of the tier colour's saturation the derived background keeps. The bundled art averages
	 * ~0.88 of the accent's saturation in its fill, so the body reads as the same colour family rather
	 * than a washed-out or oversaturated version of it.
	 */
	private static final float BACKGROUND_SATURATION_SCALE = 0.9f;

	/**
	 * Per-tier reference shades measured from the bundled art, used to work out how far each pixel
	 * deviates from its family's base shade so that deviation can be reproduced against the user's
	 * chosen color.
	 */
	private static final Map<RarityTier, TierReference> REFERENCES = new EnumMap<>(RarityTier.class);

	static
	{
		// accentS/accentB/fillS/fillB are the HSB saturation+brightness of the dominant accent and
		// fill shade of each tier's panel and icon frame (which share a palette). COMMON's accent is
		// pure white, hence saturation 0 - see #scaleSaturation.
		REFERENCES.put(RarityTier.COMMON, new TierReference(0.0000f, 1.0000f, 0.3488f, 0.1686f));
		REFERENCES.put(RarityTier.UNCOMMON, new TierReference(0.8806f, 0.7882f, 0.8276f, 0.3412f));
		REFERENCES.put(RarityTier.RARE, new TierReference(0.7048f, 0.6510f, 0.7037f, 0.2118f));
		REFERENCES.put(RarityTier.VERY_RARE, new TierReference(0.6667f, 0.6941f, 0.5536f, 0.2196f));
		REFERENCES.put(RarityTier.PET, new TierReference(0.8409f, 0.8627f, 0.6222f, 0.1765f));
	}

	private PanelRecolorer()
	{
	}

	/**
	 * Derives the panel background shade from a tier colour: the same hue, dimmed to
	 * {@code darknessPercent} of its brightness and slightly desaturated, matching how the bundled art
	 * pairs a bright border with a dark body.
	 *
	 * <p>A fully achromatic tier colour (e.g. white) has no hue to carry, so the derived background is
	 * a neutral grey rather than the warm brown the stock COMMON art happens to use.
	 */
	static Color deriveBackground(Color tierColour, int darknessPercent)
	{
		float[] hsb = toHsb(tierColour);
		float brightness = Math.min(1f, hsb[2] * (darknessPercent / 100f));
		float saturation = Math.min(1f, hsb[1] * BACKGROUND_SATURATION_SCALE);
		return new Color(Color.HSBtoRGB(hsb[0], saturation, brightness));
	}

	/**
	 * Returns a recolored copy of {@code src}, mapping its accent family (border, divider) onto
	 * {@code border} and its fill family (panel body) onto {@code background}. Dimensions and
	 * per-pixel alpha are preserved exactly.
	 */
	static BufferedImage recolor(BufferedImage src, RarityTier tier, Color border, Color background)
	{
		TierReference ref = REFERENCES.get(tier);
		float[] borderHsb = toHsb(border);
		float[] backgroundHsb = toHsb(background);

		// Pixels at or above this brightness belong to the accent family, the rest to the fill. The
		// two families are far apart on every bundled tier (the closest, VERY_RARE, is 0.69 vs 0.22),
		// so a simple midpoint split is unambiguous.
		float familySplit = (ref.accentBrightness + ref.fillBrightness) / 2f;

		int width = src.getWidth();
		int height = src.getHeight();
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int argb = src.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha == 0)
				{
					out.setRGB(x, y, 0);
					continue;
				}

				float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
				if (hsb[1] < ACHROMATIC_SATURATION && hsb[2] < OUTLINE_MAX_BRIGHTNESS)
				{
					// Black outline: structural, so leave it alone.
					out.setRGB(x, y, argb);
					continue;
				}

				boolean isAccent = hsb[2] >= familySplit;
				float[] target = isAccent ? borderHsb : backgroundHsb;
				float refSaturation = isAccent ? ref.accentSaturation : ref.fillSaturation;
				float refBrightness = isAccent ? ref.accentBrightness : ref.fillBrightness;

				float saturation = scaleSaturation(hsb[1], refSaturation, target[1]);
				float brightness = Math.min(1f, target[2] * (hsb[2] / Math.max(MIN_DIVISOR, refBrightness)));

				int rgb = Color.HSBtoRGB(target[0], saturation, brightness);
				out.setRGB(x, y, (alpha << 24) | (rgb & 0xFFFFFF));
			}
		}

		return out;
	}

	/**
	 * Scales a pixel's saturation into the target color's range. When the source family is achromatic
	 * (COMMON's white accent) there is no ratio to preserve, so the target's saturation is taken
	 * directly rather than multiplying up from ~zero.
	 */
	private static float scaleSaturation(float saturation, float reference, float targetSaturation)
	{
		if (reference < ACHROMATIC_SATURATION)
		{
			return targetSaturation;
		}
		return Math.min(1f, targetSaturation * (saturation / Math.max(MIN_DIVISOR, reference)));
	}

	private static float[] toHsb(Color color)
	{
		return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
	}

	private static final class TierReference
	{
		private final float accentSaturation;
		private final float accentBrightness;
		private final float fillSaturation;
		private final float fillBrightness;

		private TierReference(float accentSaturation, float accentBrightness, float fillSaturation, float fillBrightness)
		{
			this.accentSaturation = accentSaturation;
			this.accentBrightness = accentBrightness;
			this.fillSaturation = fillSaturation;
			this.fillBrightness = fillBrightness;
		}
	}
}
