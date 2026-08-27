package com.snakesteak.collectionlogpopupenhanced.killcount;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import net.runelite.client.ui.FontManager;

/**
 * Guards the label budget documented on {@link KillCountKind}. The derived labels come from tables
 * that a future raid or clue tier will be added to, and an over-long one degrades silently into an
 * ellipsis rather than failing, so it is measured here instead.
 */
public class KillCountLabelWidthTest
{
	// CollectionLogOverlay#cornerTextMaxWidth at 100% scale: iconX - cornerPaddingX, where
	// iconX = (BASE_PANEL_WIDTH - BASE_ICON_CANVAS_SIZE) / 2 = (379 - 89) / 2.
	private static final int CORNER_TEXT_MAX_WIDTH = (379 - 89) / 2 - 16;

	// BASE_CORNER_LABEL_FONT_SIZE. The label is the only corner text drawn at this size, and the
	// only one the overlay truncates.
	private static final float LABEL_FONT_SIZE = 16f;

	@Test
	public void everyLabelFitsTheCornerTextBudget()
	{
		Font labelFont = FontManager.getRunescapeBoldFont().deriveFont(LABEL_FONT_SIZE);
		Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		FontMetrics metrics = graphics.getFontMetrics(labelFont);

		for (String label : KillCountKind.allLabels())
		{
			int width = metrics.stringWidth(label);
			assertTrue("\"" + label + "\" measures " + width + "px, over the " + CORNER_TEXT_MAX_WIDTH
				+ "px the panel gives a corner label - it would be drawn ellipsised",
				width <= CORNER_TEXT_MAX_WIDTH);
		}

		graphics.dispose();
	}
}
