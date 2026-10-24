package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RarityEffectsTest
{
	private static final int WIDTH = 379;
	private static final int HEIGHT = 128;
	private static final int ICON = 89;
	private static final int TOTAL_HEIGHT = HEIGHT + ICON / 2;
	private static final Insets NO_ROOM = new Insets(0, 0, 0, 0);
	private static final Insets ROOM = new Insets(60, 60, 60, 60);

	@Test
	public void commonHasNoEffects()
	{
		for (RarityEffectsLevel level : RarityEffectsLevel.values())
		{
			assertFalse(RarityEffects.hasEffects(RarityTier.COMMON, level));
		}
	}

	@Test
	public void offHasNoEffects()
	{
		for (RarityTier tier : RarityTier.values())
		{
			assertFalse(RarityEffects.hasEffects(tier, RarityEffectsLevel.OFF));
		}
	}

	@Test
	public void eachTierHasAtLeastWhatTheOneBelowHas()
	{
		RarityTier[] tiers = RarityTier.values();
		for (RarityEffectsLevel level : new RarityEffectsLevel[]{RarityEffectsLevel.SUBTLE, RarityEffectsLevel.FULL})
		{
			for (int i = 1; i < tiers.length; i++)
			{
				String pair = level + ": " + tiers[i - 1] + " -> " + tiers[i];
				assertTrue(pair, RarityEffects.hasEffects(tiers[i], level));
				assertNoMore(pair, RarityEffects.profile(tiers[i - 1], level), RarityEffects.profile(tiers[i], level));
			}
		}
	}

	@Test
	public void subtleNeverHasMoreThanFull()
	{
		for (RarityTier tier : RarityTier.values())
		{
			assertNoMore(tier.toString(), RarityEffects.profile(tier, RarityEffectsLevel.SUBTLE),
				RarityEffects.profile(tier, RarityEffectsLevel.FULL));
		}
		assertTrue(RarityEffectsLevel.SUBTLE.getIntensity() < RarityEffectsLevel.FULL.getIntensity());
	}

	private static void assertNoMore(String message, RarityEffects.Profile lower, RarityEffects.Profile higher)
	{
		assertTrue(message, higher.getTwinkles() >= lower.getTwinkles());
		assertTrue(message, !lower.isHalo() || higher.isHalo());
		assertTrue(message, higher.getSparkles() >= lower.getSparkles());
		assertTrue(message, higher.getBurstParticles() >= lower.getBurstParticles());
		assertTrue(message, !lower.isEdgeGlow() || higher.isEdgeGlow());
	}

	@Test
	public void twinklesAreStableForASeed()
	{
		for (int i = 0; i < 3; i++)
		{
			assertEquals(RarityEffects.twinkle(42, i, 1234, WIDTH / 2f, HEIGHT, ICON, 1f),
				RarityEffects.twinkle(42, i, 1234, WIDTH / 2f, HEIGHT, ICON, 1f));
		}
	}

	@Test
	public void twinklesStayOnTheIconFrame()
	{
		float centerX = WIDTH / 2f;
		float centerY = HEIGHT;
		float half = ICON / 2f;
		int drawn = 0;
		int belowCenter = 0;
		for (long seed = 0; seed < 20; seed++)
		{
			for (long t = 0; t < 5000; t += 40)
			{
				for (int i = 0; i < 3; i++)
				{
					RarityEffects.Particle twinkle = RarityEffects.twinkle(seed, i, t, centerX, centerY, ICON, 1f);
					if (twinkle != null)
					{
						drawn++;
						// The frame's edge, give or take the 4px in / 6px out each twinkle may sit.
						assertTrue(twinkle.getX() >= centerX - half - 6 && twinkle.getX() <= centerX + half + 6);
						assertTrue(twinkle.getY() >= centerY - half - 6 && twinkle.getY() <= centerY + half + 6);
						if (twinkle.getY() > centerY)
						{
							belowCenter++;
						}
					}
				}
			}
		}
		assertTrue("twinkles should actually show", drawn > 0);
		assertTrue("twinkles should reach the bottom half too", belowCenter > 0);
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
	public void burstWaitsUntilAfterTheScreenshot()
	{
		for (long t = 0; t <= RarityEffects.BURST_DELAY_MILLIS; t += 25)
		{
			for (int i = 0; i < 28; i++)
			{
				assertEquals(null, RarityEffects.burstParticle(1, i, t, 0, 0, WIDTH, TOTAL_HEIGHT, ROOM, 1f));
			}
		}
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
	public void drawsEveryTierWithoutError()
	{
		BufferedImage canvas = new BufferedImage(WIDTH + 120, TOTAL_HEIGHT + 120, BufferedImage.TYPE_INT_ARGB);
		RarityEffects effects = new RarityEffects();
		for (RarityEffectsLevel level : RarityEffectsLevel.values())
		{
			for (RarityTier tier : RarityTier.values())
			{
				for (long t = 0; t < 4000; t += 400)
				{
					RarityEffects.Frame frame = RarityEffects.Frame.builder()
						.tier(tier).level(level).millis(t).alpha(1f).scale(1f)
						.panelWidth(WIDTH).panelHeight(HEIGHT).totalHeight(TOTAL_HEIGHT)
						.iconCenterX(WIDTH / 2f).iconCenterY(HEIGHT).iconSize(ICON)
						.color(RarityEffects.effectColor(Color.MAGENTA))
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
}
