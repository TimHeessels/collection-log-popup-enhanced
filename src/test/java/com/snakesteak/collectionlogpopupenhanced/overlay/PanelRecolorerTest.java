package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PanelRecolorerTest
{
	/**
	 * The stock accent colour baked into each bundled asset, which is also that tier's config default.
	 */
	private static final Object[][] TIERS = {
		{RarityTier.COMMON, "Backgrounds/BackgroundPanel1.png", "Icons/IconPanel1.png", 0xFFFFFF},
		{RarityTier.UNCOMMON, "Backgrounds/BackgroundPanel2.png", "Icons/IconPanel2.png", 0x1888C9},
		{RarityTier.RARE, "Backgrounds/BackgroundPanel3.png", "Icons/IconPanel3.png", 0x8431A6},
		{RarityTier.VERY_RARE, "Backgrounds/BackgroundPanel4.png", "Icons/IconPanel4.png", 0xB19F3B},
		{RarityTier.PET, "Backgrounds/BackgroundPanelPet.png", "Icons/IconPanelPet.png", 0xDC2367},
	};

	private static BufferedImage load(String resource) throws IOException
	{
		try (InputStream in = PanelRecolorerTest.class.getResourceAsStream("/" + resource))
		{
			return ImageIO.read(in);
		}
	}

	/**
	 * The accent family (border, divider) is driven directly by the tier colour, so recolouring to a
	 * tier's stock colour must reproduce the original border. The fill is now derived rather than
	 * configured, so it is deliberately not asserted here.
	 */
	@Test
	public void stockTierColourReproducesBorder() throws IOException
	{
		for (Object[] tier : TIERS)
		{
			Color tierColour = new Color((Integer) tier[3]);
			BufferedImage src = load((String) tier[1]);
			BufferedImage out = PanelRecolorer.recolor(src, (RarityTier) tier[0], tierColour,
				PanelRecolorer.deriveBackground(tierColour, 30));

			// x=190 is clear of the rounded corners; y=2 sits on the top border stripe.
			int expected = src.getRGB(190, 2);
			assertChannelsWithinOne((String) tier[1], 190, 2, expected, out.getRGB(190, 2));
		}
	}

	/** The derived background must stay clearly darker than the tier colour it came from. */
	@Test
	public void derivedBackgroundIsDarkerThanTierColour()
	{
		for (Object[] tier : TIERS)
		{
			Color tierColour = new Color((Integer) tier[3]);
			Color background = PanelRecolorer.deriveBackground(tierColour, 30);

			float[] tierHsb = Color.RGBtoHSB(tierColour.getRed(), tierColour.getGreen(), tierColour.getBlue(), null);
			float[] bgHsb = Color.RGBtoHSB(background.getRed(), background.getGreen(), background.getBlue(), null);

			assertTrue("background should be darker for " + tier[0], bgHsb[2] < tierHsb[2]);
			if (tierHsb[1] > 0.06f)
			{
				assertEquals("hue should be preserved for " + tier[0], tierHsb[0], bgHsb[0], 0.02f);
			}
		}
	}

	/** A higher darkness percentage must produce a brighter background, monotonically. */
	@Test
	public void darknessSliderIsMonotonic()
	{
		Color tierColour = new Color(0x8431A6);
		float previous = -1f;
		for (int darkness = 10; darkness <= 60; darkness += 10)
		{
			Color background = PanelRecolorer.deriveBackground(tierColour, darkness);
			float brightness = Color.RGBtoHSB(background.getRed(), background.getGreen(), background.getBlue(), null)[2];
			assertTrue("brightness should increase with darkness%", brightness > previous);
			previous = brightness;
		}
	}

	/** Geometry and alpha must survive untouched - the icon frame designs are carried by both. */
	@Test
	public void recolorPreservesDimensionsAndAlpha() throws IOException
	{
		for (Object[] tier : TIERS)
		{
			for (int asset = 1; asset <= 2; asset++)
			{
				BufferedImage src = load((String) tier[asset]);
				BufferedImage out = PanelRecolorer.recolor(src, (RarityTier) tier[0], Color.GREEN, Color.BLUE);

				assertEquals("width", src.getWidth(), out.getWidth());
				assertEquals("height", src.getHeight(), out.getHeight());

				for (int y = 0; y < src.getHeight(); y++)
				{
					for (int x = 0; x < src.getWidth(); x++)
					{
						assertEquals("alpha at " + x + "," + y,
							(src.getRGB(x, y) >>> 24) & 0xFF,
							(out.getRGB(x, y) >>> 24) & 0xFF);
					}
				}
			}
		}
	}

	/**
	 * COMMON's accent is pure white (saturation 0), which would divide by ~zero if the saturation
	 * ratio were applied blindly. Recolouring it must still produce the requested hue.
	 */
	@Test
	public void whiteAccentTierRecoloursWithoutArtifacts() throws IOException
	{
		BufferedImage src = load("Backgrounds/BackgroundPanel1.png");
		BufferedImage out = PanelRecolorer.recolor(src, RarityTier.COMMON, new Color(0x1FA84A), new Color(0x0E2E1A));

		// x=2,y=2 sits on the border stripe, which is white in the source art.
		float[] hsb = Color.RGBtoHSB((out.getRGB(2, 2) >> 16) & 0xFF, (out.getRGB(2, 2) >> 8) & 0xFF, out.getRGB(2, 2) & 0xFF, null);
		assertTrue("white accent should take the target saturation, was " + hsb[1], hsb[1] > 0.5f);
		assertEquals("hue should match the requested border colour", 0.371f, hsb[0], 0.02f);
	}

	/** The black outline defines each icon frame's silhouette and must never be recoloured. */
	@Test
	public void blackOutlineIsPreserved() throws IOException
	{
		BufferedImage src = load("Icons/IconPanelPet.png");
		BufferedImage out = PanelRecolorer.recolor(src, RarityTier.PET, Color.ORANGE, Color.CYAN);

		for (int y = 0; y < src.getHeight(); y++)
		{
			for (int x = 0; x < src.getWidth(); x++)
			{
				int p = src.getRGB(x, y);
				if (((p >>> 24) & 0xFF) < 250)
				{
					continue;
				}
				float[] hsb = Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, null);
				if (hsb[1] < 0.06f)
				{
					assertEquals("structural pixel at " + x + "," + y, p, out.getRGB(x, y));
				}
			}
		}
	}

	/**
	 * The overlay strips alpha before recolouring (see CollectionLogOverlay#opaque), so a translucent
	 * border must produce the same pixels as its opaque equivalent - the recolorer takes its alpha
	 * from the source art, never from the requested colour.
	 */
	@Test
	public void requestedColourAlphaDoesNotAffectOutput() throws IOException
	{
		BufferedImage src = load("Backgrounds/BackgroundPanel3.png");
		Color opaque = new Color(0x8431A6);
		Color translucent = new Color(0x84, 0x31, 0xA6, 0x40);

		BufferedImage fromOpaque = PanelRecolorer.recolor(src, RarityTier.RARE, opaque,
			PanelRecolorer.deriveBackground(opaque, 30));
		BufferedImage fromTranslucent = PanelRecolorer.recolor(src, RarityTier.RARE, translucent,
			PanelRecolorer.deriveBackground(translucent, 30));

		for (int y = 0; y < src.getHeight(); y++)
		{
			for (int x = 0; x < src.getWidth(); x++)
			{
				assertEquals("pixel at " + x + "," + y,
					fromOpaque.getRGB(x, y), fromTranslucent.getRGB(x, y));
			}
		}
	}

	private static void assertChannelsWithinOne(String name, int x, int y, int expected, int actual)
	{
		for (int shift : new int[]{24, 16, 8, 0})
		{
			int e = (expected >>> shift) & 0xFF;
			int a = (actual >>> shift) & 0xFF;
			assertTrue(name + " channel@" + shift + " at " + x + "," + y + " expected " + e + " but was " + a,
				Math.abs(e - a) <= 1);
		}
	}
}
