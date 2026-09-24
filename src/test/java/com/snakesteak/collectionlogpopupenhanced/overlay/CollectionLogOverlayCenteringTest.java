package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.rarity.PreviewTier;
import com.snakesteak.collectionlogpopupenhanced.sound.SoundManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Horizontal drift of a few px isn't obvious in the drawn result, so assert the location directly. */
public class CollectionLogOverlayCenteringTest
{
	// BASE_PANEL_WIDTH at the 100% scale stubbed below.
	private static final int PANEL_WIDTH = 379;

	private Client client;
	private CollectionLogOverlay overlay;

	@Before
	public void before()
	{
		client = mock(Client.class);
		ItemManager itemManager = mock(ItemManager.class);
		CollectionLogPopupEnhancedConfig config = mock(CollectionLogPopupEnhancedConfig.class);
		SoundManager soundManager = mock(SoundManager.class);

		when(config.overlayScalePercent()).thenReturn(100);
		when(config.backgroundDarkness()).thenReturn(50);
		when(config.panelStyle()).thenReturn(PanelStyle.COLORFUL);
		when(config.previewTier()).thenReturn(PreviewTier.NONE);
		when(config.textRenderMode()).thenReturn(TextRenderMode.SMOOTH);
		when(config.colourCommonTier()).thenReturn(Color.WHITE);
		when(config.colourUncommonTier()).thenReturn(Color.GREEN);
		when(config.colourRareTier()).thenReturn(Color.BLUE);
		when(config.colourVeryRareTier()).thenReturn(Color.MAGENTA);
		when(config.colourPetTier()).thenReturn(Color.YELLOW);

		overlay = new CollectionLogOverlay(client, itemManager, config, soundManager);
	}

	private void stubViewport(int canvasWidth, int xOffset, int width)
	{
		when(client.getRealDimensions()).thenReturn(new Dimension(canvasWidth, 720));
		when(client.getViewportXOffset()).thenReturn(xOffset);
		when(client.getViewportWidth()).thenReturn(width);
	}

	/** The location is set before the no-item early return, so an idle frame is enough. */
	private Point renderAndReadLocation()
	{
		BufferedImage canvas = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		try
		{
			overlay.render(graphics);
		}
		finally
		{
			graphics.dispose();
		}
		return overlay.getPreferredLocation();
	}

	private void assertCenteredOn(int expectedCenterX)
	{
		assertEquals(expectedCenterX - PANEL_WIDTH / 2, renderAndReadLocation().x);
	}

	/** Sidebar closed: the viewport fills the canvas, so the two centers coincide. */
	@Test
	public void centersOnTheViewportInPlainResizable()
	{
		stubViewport(1280, 0, 1280);
		assertCenteredOn(640);
	}

	/** Fixed/classic offsets the viewport left of the sidebar inside a wider canvas. */
	@Test
	public void centersOnTheViewportInFixedMode()
	{
		stubViewport(1280, 108, 512);
		assertCenteredOn(364);
	}

	/** Fixed Resizable Hybrid narrows the viewport by 249px while isResized() still reads true. */
	@Test
	public void centersOnTheViewportUnderAHybridStyleNarrowedViewport()
	{
		stubViewport(1280, 0, 1280 - 249);
		assertCenteredOn((1280 - 249) / 2);
	}

	/** A 0-width viewport falls back to the canvas center rather than pinning the panel left. */
	@Test
	public void fallsBackToTheCanvasCenterWhenTheViewportIsDegenerate()
	{
		stubViewport(1280, 0, 0);
		assertCenteredOn(640);
	}

	/** y is TOP_MARGIN regardless of which branch ran. */
	@Test
	public void alwaysSitsAtTheTopMargin()
	{
		stubViewport(1280, 0, 1280);
		assertEquals(2, renderAndReadLocation().y);
	}
}
