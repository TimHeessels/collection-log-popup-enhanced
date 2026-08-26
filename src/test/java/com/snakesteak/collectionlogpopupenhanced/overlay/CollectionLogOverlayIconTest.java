package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.CollectionLogPopupEnhancedConfig;
import com.snakesteak.collectionlogpopupenhanced.killcount.KillCountKind;
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
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the icon path's tolerance of items the client can't hand back a sprite for. The panel is
 * rendered for real against an offscreen image, so a null dereference in the draw path surfaces here
 * as a failing test rather than as an "Error during overlay rendering" line in a user's log.
 */
public class CollectionLogOverlayIconTest
{
	// A real id whose sprite came back null in practice, taking the panel's rendering down with it.
	private static final int ITEM_ID = 29265;

	// The icon only starts drawing once the fold-open animation has played out (FOLD_MILLIS), so a
	// null sprite goes unnoticed before then - every assertion here has to render past it.
	private static final long PAST_FOLD_MILLIS = 700;

	private ItemManager itemManager;
	private CollectionLogOverlay overlay;

	@Before
	public void before()
	{
		Client client = mock(Client.class);
		itemManager = mock(ItemManager.class);
		CollectionLogPopupEnhancedConfig config = mock(CollectionLogPopupEnhancedConfig.class);
		SoundManager soundManager = mock(SoundManager.class);

		when(client.isResized()).thenReturn(true);
		when(client.getRealDimensions()).thenReturn(new Dimension(1280, 720));

		when(config.overlayScalePercent()).thenReturn(100);
		when(config.overlayDisplaySeconds()).thenReturn(5);
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

	private void renderPastFold() throws InterruptedException
	{
		BufferedImage canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		try
		{
			// First frame starts the notification's clock; the icon is still folded shut here.
			assertNotNull(overlay.render(graphics));
			Thread.sleep(PAST_FOLD_MILLIS);
			// Second frame lands inside the icon's pop window, which is what draws the sprite.
			assertNotNull(overlay.render(graphics));
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void rendersWhenTheItemHasNoSprite() throws InterruptedException
	{
		// The client returns null for an id it has no sprite for, even when the id itself is valid.
		when(itemManager.getImage(anyInt())).thenReturn(null);

		overlay.enqueue("Guild hunter top", ITEM_ID, RarityTier.COMMON, 480, true, 480, 15.4,
			null, null, null, List.of());

		renderPastFold();
	}

	@Test
	public void rendersWhenTheItemHasASprite() throws InterruptedException
	{
		AsyncBufferedImage sprite = mock(AsyncBufferedImage.class);
		// Item sprites are a fixed 36x32; the icon path scales from these, so a 0x0 mock would
		// divide by zero and mask what this test is actually checking.
		when(sprite.getWidth()).thenReturn(36);
		when(sprite.getHeight()).thenReturn(32);
		when(itemManager.getImage(anyInt())).thenReturn(sprite);

		overlay.enqueue("Guild hunter top", ITEM_ID, RarityTier.COMMON, 480, true, 480, 15.4,
			312, KillCountKind.RUMOURS, null, List.of());

		renderPastFold();
	}

	@Test
	public void rendersWhenTheItemIdItselfIsUnresolved() throws InterruptedException
	{
		// The pre-existing sentinel case: a negative id short-circuits before the sprite lookup.
		overlay.enqueue("Some unknown item", -1, RarityTier.COMMON, 0, false, 0, null,
			null, null, null, List.of());

		renderPastFold();
	}
}
