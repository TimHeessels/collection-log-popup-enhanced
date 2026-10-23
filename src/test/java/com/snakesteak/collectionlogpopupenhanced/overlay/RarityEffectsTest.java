package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static com.snakesteak.collectionlogpopupenhanced.overlay.CollectionLogOverlay.EFFECTS_START_MILLIS;
import static com.snakesteak.collectionlogpopupenhanced.overlay.CollectionLogOverlay.NATIVE_OPEN_TIMEOUT_MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RarityEffectsTest
{
	private static final int WIDTH = 379;
	private static final int HEIGHT = 128;
	private static final int TOTAL_HEIGHT = HEIGHT + 44;
	private static final Insets NO_ROOM = new Insets(0, 0, 0, 0);
	private static final Insets ROOM = new Insets(60, 60, 60, 60);

	@Test
	public void commonHasNoEffects()
	{
		assertFalse(RarityEffects.hasEffects(RarityTier.COMMON));
	}

	@Test
	public void eachTierHasAtLeastWhatTheOneBelowHas()
	{
		RarityTier[] tiers = RarityTier.values();
		for (int i = 1; i < tiers.length; i++)
		{
			RarityEffects.Profile lower = RarityEffects.profile(tiers[i - 1]);
			RarityEffects.Profile higher = RarityEffects.profile(tiers[i]);
			String pair = tiers[i - 1] + " -> " + tiers[i];
			assertTrue(pair, RarityEffects.hasEffects(tiers[i]));
			assertTrue(pair, !lower.hasShine() || higher.hasShine());
			// A shorter cycle is a more frequent shine; ONCE is the longest possible.
			assertTrue(pair, !lower.hasShine() || higher.getSweepCycleMillis() <= lower.getSweepCycleMillis());
			assertTrue(pair, higher.getShineAlpha() >= lower.getShineAlpha());
			assertTrue(pair, !lower.isHalo() || higher.isHalo());
			assertTrue(pair, higher.getSparkles() >= lower.getSparkles());
			assertTrue(pair, higher.getBurstParticles() >= lower.getBurstParticles());
			assertTrue(pair, !lower.isEdgeGlow() || higher.isEdgeGlow());
		}
	}

	@Test
	public void uncommonShinesOnce()
	{
		RarityEffects.Profile uncommon = RarityEffects.profile(RarityTier.UNCOMMON);
		assertEquals(-1f, RarityEffects.sweepProgress(-1, uncommon), 0f);
		assertEquals(0f, RarityEffects.sweepProgress(0, uncommon), 0f);
		assertEquals(0.5f, RarityEffects.sweepProgress(350, uncommon), 0.001f);
		assertEquals(-1f, RarityEffects.sweepProgress(10_000, uncommon), 0f);
	}

	@Test
	public void rareShineRepeats()
	{
		RarityEffects.Profile rare = RarityEffects.profile(RarityTier.RARE);
		long cycle = rare.getSweepCycleMillis();
		assertEquals(-1f, RarityEffects.sweepProgress(cycle - 1, rare), 0f);
		assertEquals(RarityEffects.sweepProgress(100, rare), RarityEffects.sweepProgress(cycle * 3 + 100, rare), 0f);
		for (long t = 0; t < cycle * 2; t += 37)
		{
			float progress = RarityEffects.sweepProgress(t, rare);
			assertTrue(progress == -1f || (progress >= 0f && progress <= 1f));
		}
	}

	@Test
	public void sparklesAreStableForASeed()
	{
		for (int i = 0; i < 20; i++)
		{
			assertEquals(RarityEffects.sparkle(42, i, 1234, WIDTH, HEIGHT, ROOM, 1f),
				RarityEffects.sparkle(42, i, 1234, WIDTH, HEIGHT, ROOM, 1f));
		}
	}

	@Test
	public void sparklesStayInsideTheRoomGiven()
	{
		for (long t = 0; t < 5000; t += 50)
		{
			for (int i = 0; i < 20; i++)
			{
				RarityEffects.Particle sparkle = RarityEffects.sparkle(7, i, t, WIDTH, HEIGHT, NO_ROOM, 1f);
				if (sparkle != null)
				{
					assertTrue(sparkle.getX() >= 0 && sparkle.getX() <= WIDTH);
					assertTrue(sparkle.getY() >= 0 && sparkle.getY() <= HEIGHT);
				}
			}
		}
	}

	@Test
	public void burstStaysOnScreenAtTheTop()
	{
		// Panel flush against the top of the screen: nothing may go above it.
		Insets room = new Insets(0, 60, 60, 60);
		float centerX = WIDTH / 2f;
		float centerY = HEIGHT;
		int drawn = 0;
		for (long t = 0; t < 2000; t += 25)
		{
			for (int i = 0; i < 28; i++)
			{
				RarityEffects.Particle piece = RarityEffects.burstParticle(99, i, t, centerX, centerY, WIDTH, TOTAL_HEIGHT, room, 1f);
				if (piece == null)
				{
					continue;
				}
				drawn++;
				assertTrue(piece.getY() >= 0);
				assertTrue(piece.getY() <= TOTAL_HEIGHT + 60);
				assertTrue(piece.getX() >= -60 && piece.getX() <= WIDTH + 60);
			}
		}
		assertTrue("the burst should actually play", drawn > 0);
	}

	@Test
	public void burstPlaysOnceThenEnds()
	{
		for (int i = 0; i < 28; i++)
		{
			assertEquals(null, RarityEffects.burstParticle(1, i, 5000, 0, 0, WIDTH, TOTAL_HEIGHT, ROOM, 1f));
		}
	}

	@Test
	public void effectsWaitForTheIconPop()
	{
		assertFalse(CollectionLogOverlay.effectsMayShow(EFFECTS_START_MILLIS - 1, 10_000, -1, 0));
		assertTrue(CollectionLogOverlay.effectsMayShow(EFFECTS_START_MILLIS, 10_000, -1, 0));
	}

	@Test
	public void effectsWaitForTheScreenshot()
	{
		long now = 10_000;
		// Game notification still opening: the screenshot hasn't been taken yet.
		assertFalse(CollectionLogOverlay.effectsMayShow(5000, now, now - 1000, 0));
		// Opened, but still inside the grace period after it.
		assertFalse(CollectionLogOverlay.effectsMayShow(5000, now, -1, now + 1));
		assertTrue(CollectionLogOverlay.effectsMayShow(5000, now, -1, now));
	}

	@Test
	public void aNotificationThatNeverFinishesOpeningStopsHoldingEffects()
	{
		long now = 20_000;
		assertTrue(CollectionLogOverlay.effectsMayShow(5000, now, now - NATIVE_OPEN_TIMEOUT_MILLIS, 0));
	}

	@Test
	public void drawsEveryTierWithoutError()
	{
		BufferedImage background = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		BufferedImage canvas = new BufferedImage(WIDTH + 120, TOTAL_HEIGHT + 120, BufferedImage.TYPE_INT_ARGB);
		RarityEffects effects = new RarityEffects();
		for (RarityTier tier : RarityTier.values())
		{
			for (long t = 0; t < 4000; t += 400)
			{
				RarityEffects.Frame frame = RarityEffects.Frame.builder()
					.tier(tier).millis(t).alpha(1f).scale(1f)
					.panelWidth(WIDTH).panelHeight(HEIGHT).totalHeight(TOTAL_HEIGHT)
					.background(background)
					.iconCenterX(WIDTH / 2f).iconCenterY(HEIGHT).iconSize(89)
					.color(RarityEffects.effectColor(Color.MAGENTA, false))
					.seed(123).room(ROOM)
					.build();
				Graphics2D graphics = canvas.createGraphics();
				try
				{
					graphics.translate(60, 60);
					effects.drawUnderIcon(graphics, frame);
					effects.drawOverIcon(graphics, frame);
				}
				finally
				{
					graphics.dispose();
				}
			}
		}
	}
}
