package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.rarity.PreviewTier;
import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import com.snakesteak.collectionlogpopupenhanced.sound.SoundManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the "delay CoX popups until chest" queue behaviour. Each item is given a distinct tier so
 * the sound call - which fires once as an item is dequeued - identifies which one is showing;
 * nothing else about the current item is observable from outside.
 */
public class CollectionLogOverlayHoldTest
{
	// FOLD_MILLIS + ICON_POP_MILLIS + FADE_MILLIS, plus a margin. overlayDisplaySeconds is stubbed
	// to 0 below, so this is the whole lifetime of an item on screen.
	private static final long TOTAL_ANIMATION_MILLIS = 550 + 400 + 400 + 150;

	private SoundManager soundManager;
	private CollectionLogOverlay overlay;

	@Before
	public void before()
	{
		Client client = mock(Client.class);
		ItemManager itemManager = mock(ItemManager.class);
		CollectionLogPopupEnhancedConfig config = mock(CollectionLogPopupEnhancedConfig.class);
		soundManager = mock(SoundManager.class);

		// Unstubbed these return 0, sending render() down its degenerate-viewport fallback.
		when(client.getViewportXOffset()).thenReturn(4);
		when(client.getViewportWidth()).thenReturn(1272);
		when(client.getRealDimensions()).thenReturn(new Dimension(1280, 720));

		when(config.overlayScalePercent()).thenReturn(100);
		when(config.overlayDisplaySeconds()).thenReturn(0);
		when(config.backgroundDarkness()).thenReturn(50);
		when(config.previewTier()).thenReturn(PreviewTier.NONE);
		when(config.textRenderMode()).thenReturn(TextRenderMode.SMOOTH);
		when(config.valueDisplayMode()).thenReturn(ValueDisplayMode.GE_VALUE);
		when(config.leftPanelStat()).thenReturn(LeftPanelStat.KILL_COUNT);
		when(config.rightPanelStat()).thenReturn(RightPanelStat.DROP_RATE);
		when(config.showProgressBar()).thenReturn(true);
		when(config.bulkUnlockSfx()).thenReturn(false);
		when(config.colourStatLabel()).thenReturn(Color.WHITE);
		when(config.colourStatValue()).thenReturn(Color.WHITE);
		when(config.colourCaption()).thenReturn(Color.WHITE);
		when(config.colourCommonTier()).thenReturn(Color.WHITE);
		when(config.colourUncommonTier()).thenReturn(Color.GREEN);
		when(config.colourRareTier()).thenReturn(Color.BLUE);
		when(config.colourVeryRareTier()).thenReturn(Color.MAGENTA);
		when(config.colourPetTier()).thenReturn(Color.YELLOW);

		overlay = new CollectionLogOverlay(client, itemManager, config, soundManager);
	}

	private void enqueue(String name, RarityTier tier, boolean held)
	{
		overlay.enqueue(name, 1, tier, 0, false, 0, null, null, null, null, null, null, List.of(), held);
	}

	/** One frame is enough to dequeue the next item - the clock only matters for expiry. */
	private void renderFrame()
	{
		BufferedImage canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		try
		{
			overlay.render(graphics);
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void heldItemDoesNotShowUntilReleased()
	{
		enqueue("Twisted bow", RarityTier.VERY_RARE, true);

		renderFrame();
		verify(soundManager, never()).play(RarityTier.VERY_RARE);
		assertFalse("a held item still counts as queued", overlay.isIdle());

		overlay.releaseHeld();
		renderFrame();
		verify(soundManager).play(RarityTier.VERY_RARE);
	}

	/**
	 * The queue is FIFO, so a held item sitting at the head must not stall the ones behind it - the
	 * player would otherwise lose every unrelated unlock until they opened the chest.
	 */
	@Test
	public void heldItemDoesNotBlockLaterUnlocks()
	{
		enqueue("Twisted bow", RarityTier.VERY_RARE, true);
		enqueue("Clue scroll reward", RarityTier.COMMON, false);

		renderFrame();

		verify(soundManager).play(RarityTier.COMMON);
		verify(soundManager, never()).play(RarityTier.VERY_RARE);
	}

	/**
	 * Drains the queue by rendering past each item's expiry. overlayDisplaySeconds is stubbed to 0
	 * here so the wait is the fold/pop/fade animation only, rather than five real seconds per item.
	 */
	@Test
	public void releasedItemsShowInUnlockOrder() throws InterruptedException
	{
		enqueue("Twisted bow", RarityTier.VERY_RARE, true);
		enqueue("Kodai insignia", RarityTier.RARE, true);

		overlay.releaseHeld();

		// Three passes: dequeue the first, dequeue the second once it expires, then let the second
		// expire too so the overlay ends up genuinely idle.
		for (int i = 0; i < 3; i++)
		{
			renderFrame();
			Thread.sleep(TOTAL_ANIMATION_MILLIS);
		}
		renderFrame();

		InOrder order = inOrder(soundManager);
		order.verify(soundManager).play(RarityTier.VERY_RARE);
		order.verify(soundManager).play(RarityTier.RARE);
		assertTrue(overlay.isIdle());
	}

	/** Exactly one sound per burst when bulkUnlockSfx is on, held items included. */
	@Test
	public void releasedBurstPlaysOneSound()
	{
		enqueue("Twisted bow", RarityTier.VERY_RARE, true);
		enqueue("Kodai insignia", RarityTier.RARE, true);

		overlay.releaseHeld();
		renderFrame();

		verify(soundManager, times(1)).play(RarityTier.VERY_RARE);
		verify(soundManager, never()).play(RarityTier.RARE);
	}
}
