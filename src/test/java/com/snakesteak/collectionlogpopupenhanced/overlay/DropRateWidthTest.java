package com.snakesteak.collectionlogpopupenhanced.overlay;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The corner's single-line values are drawn without truncation, so an over-wide drop rate range
 * clips silently under the icon. Measured against the real font, the way KillCountLabelWidthTest
 * does for labels.
 */
public class DropRateWidthTest
{
	// CollectionLogOverlay#cornerTextMaxWidth at 100% scale: iconX - cornerPaddingX, where
	// iconX = (BASE_PANEL_WIDTH - BASE_ICON_CANVAS_SIZE) / 2 = (379 - 89) / 2.
	private static final int CORNER_TEXT_MAX_WIDTH = (379 - 89) / 2 - 16;

	// BASE_CORNER_VALUE_FONT_SIZE - the size a single-line value is drawn at.
	private static final float VALUE_FONT_SIZE = 19f;

	private static final FontMetrics METRICS;

	static
	{
		Font font = FontManager.getRunescapeBoldFont().deriveFont(VALUE_FONT_SIZE);
		Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		METRICS = graphics.getFontMetrics(font);
	}

	// The widest range the shipped dataset produces (Heron, across 6 sources). It does not fit, which
	// is the whole reason the compact form exists - if this ever starts fitting, the compact path is
	// dead code rather than broken.
	@Test
	public void theWidestRealRangeDoesNotFitExact()
	{
		assertTrue("\"1/5000 - 1/1845k\" now fits - re-check whether the compact form is still needed",
			METRICS.stringWidth("1/5000 - 1/1845k") > CORNER_TEXT_MAX_WIDTH);
	}

	// Its compact rendering must fit, or the fallback buys nothing.
	@Test
	public void theCompactFormOfTheWidestRealRangeFits()
	{
		assertFits("1/5k - 1/1845k");
	}

	// The compact form only helps where a 4-digit denominator becomes one decimal of k; these are
	// the widest such ranges in the dataset.
	@Test
	public void compactFormsOfTheOtherWideRangesFit()
	{
		assertFits("1/5.8k - 1/313k");
		assertFits("1/1.5k - 1/3k");
		assertFits("1/1.2k - 1/3k");
		assertFits("1/4.5k - 1/5.4k");
	}

	// Ranges that already fit are shown exactly, so they have to keep fitting.
	@Test
	public void typicalExactRangesStillFit()
	{
		assertFits("1/1232 - 1/3003");
		assertFits("1/1500 - 1/3000");
		assertFits("1/540 - 1/1350");
		assertFits("1/160k - 1/800k");
	}

	private static void assertFits(String text)
	{
		int width = METRICS.stringWidth(text);
		assertTrue("\"" + text + "\" measures " + width + "px, over the " + CORNER_TEXT_MAX_WIDTH
			+ "px the panel gives a corner value - it would be drawn clipped", width <= CORNER_TEXT_MAX_WIDTH);
	}
}
