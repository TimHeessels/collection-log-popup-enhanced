package com.snakesteak.collectionlogpopupenhanced.overlay;

import com.snakesteak.collectionlogpopupenhanced.rarity.RarityTier;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import lombok.Builder;
import lombok.Value;

/**
 * The optional "Rarity effects": icon twinkles, icon halo, sparkles, a burst and an edge glow, each
 * tier adding to the one below (see {@link Profile}). The overlay decides when they start (see
 * CollectionLogOverlay#effectsFrame).
 *
 * <p>Everything is drawn in the panel's own coordinates, top-left at 0,0. The timing maths lives in
 * static helpers so it can be tested without a Graphics.
 */
class RarityEffects
{
	private static final long HALO_PULSE_MILLIS = 1200;
	private static final long EDGE_PULSE_MILLIS = 1400;
	private static final long RAINBOW_MILLIS = 2500;
	// Holds the burst until after the screenshot (~400 ms into the effects), so it never covers the icon in it.
	static final long BURST_DELAY_MILLIS = 700;
	// How far, at 100% scale, anything may spill past the panel - further still limited by the room on screen.
	static final int BASE_MAX_OVERFLOW = 60;

	private static final Color NEUTRAL_EFFECT_COLOR = new Color(255, 226, 140);

	private static final Map<RarityTier, Profile> PROFILES = new EnumMap<>(RarityTier.class);

	static
	{
		PROFILES.put(RarityTier.COMMON, new Profile(0, false, 0, 0, false, false));
		PROFILES.put(RarityTier.UNCOMMON, new Profile(3, false, 0, 0, false, false));
		PROFILES.put(RarityTier.RARE, new Profile(3, true, 0, 0, false, false));
		PROFILES.put(RarityTier.VERY_RARE, new Profile(3, true, 8, 0, false, false));
		PROFILES.put(RarityTier.PET, new Profile(3, true, 20, 28, true, true));
	}

	/**
	 * What each tier gets. Every field only ever grows tier to tier, so a rarer unlock never has less.
	 */
	@Value
	static class Profile
	{
		int twinkles;
		boolean halo;
		int sparkles;
		int burstParticles;
		boolean edgeGlow;
		boolean rainbow;

		boolean hasAny()
		{
			return twinkles > 0 || halo || sparkles > 0 || burstParticles > 0 || edgeGlow;
		}
	}

	/** Everything one frame of effects needs, worked out by the overlay. */
	@Value
	@Builder
	static class Frame
	{
		RarityTier tier;
		// Since the effects started.
		long millis;
		// Fade-out, opacity and fade-in combined.
		float alpha;
		float scale;
		int panelWidth;
		int panelHeight;
		// Panel plus whatever hangs below it (the extended icon frame's bottom half).
		int totalHeight;
		float iconCenterX;
		float iconCenterY;
		int iconSize;
		Color color;
		long seed;
		// Room left on screen on each side of the panel, already capped at the max overflow.
		Insets room;
	}

	@Value
	static class Particle
	{
		float x;
		float y;
		float size;
		float alpha;
		float rotation;
		float hue;
	}

	static Profile profile(RarityTier tier)
	{
		return PROFILES.get(tier);
	}

	static boolean hasEffects(RarityTier tier)
	{
		return profile(tier).hasAny();
	}

	/** {@code color} blended {@code amount} of the way to white - reads as light over any tier colour. */
	static Color effectColor(Color tierColor, boolean neutral)
	{
		if (neutral)
		{
			return NEUTRAL_EFFECT_COLOR;
		}
		float amount = 0.5f;
		return new Color(
			Math.round(tierColor.getRed() + (255 - tierColor.getRed()) * amount),
			Math.round(tierColor.getGreen() + (255 - tierColor.getGreen()) * amount),
			Math.round(tierColor.getBlue() + (255 - tierColor.getBlue()) * amount));
	}

	/**
	 * A sparkle twinkling somewhere along the icon frame, just inside or outside its edge. Like
	 * {@link #sparkle}, each lives ~1-1.6 s and reappears elsewhere, fixed by {@code seed}.
	 *
	 * @return null between twinkles
	 */
	static Particle twinkle(long seed, int index, long millis, float centerX, float centerY, int iconSize, float scale)
	{
		// Negative salts keep these clear of the sparkles' and the burst's streams.
		Random timing = new Random(mix(seed, index, -2));
		long lifetime = 1000 + timing.nextInt(600);
		long offset = timing.nextInt((int) lifetime);
		long cycle = (millis + offset) / lifetime;
		float u = ((millis + offset) % lifetime) / (float) lifetime;
		float twinkle = (float) Math.sin(Math.PI * u);
		if (twinkle <= 0.01f)
		{
			return null;
		}

		Random place = new Random(mix(seed, index, -3 - cycle));
		float half = iconSize / 2f;
		float along = place.nextFloat() * 4;
		float edge = half + (place.nextFloat() * 10 - 4) * scale;
		float f = along % 1;
		float x;
		float y;
		if (along < 1)
		{
			x = centerX - half + f * iconSize;
			y = centerY - edge;
		}
		else if (along < 2)
		{
			x = centerX + edge;
			y = centerY - half + f * iconSize;
		}
		else if (along < 3)
		{
			x = centerX + half - f * iconSize;
			y = centerY + edge;
		}
		else
		{
			x = centerX - edge;
			y = centerY + half - f * iconSize;
		}
		float size = (4 + place.nextFloat() * 4) * 0.9f * scale * twinkle;
		return new Particle(x, y, size, twinkle, u * 0.8f, place.nextFloat());
	}

	/**
	 * A sparkle twinkling somewhere along the panel's border. Each one lives for ~1-1.5 s, then
	 * reappears elsewhere; where and when is fixed by {@code seed} so a popup's sparkles are stable.
	 *
	 * @return null between twinkles
	 */
	static Particle sparkle(long seed, int index, long millis, int width, int height, Insets room, float scale)
	{
		Random timing = new Random(mix(seed, index, 0));
		long lifetime = 900 + timing.nextInt(700);
		long offset = timing.nextInt((int) lifetime);
		long cycle = (millis + offset) / lifetime;
		float u = ((millis + offset) % lifetime) / (float) lifetime;
		float twinkle = (float) Math.sin(Math.PI * u);
		if (twinkle <= 0.01f)
		{
			return null;
		}

		Random place = new Random(mix(seed, index, cycle + 1));
		float along = place.nextFloat() * 2 * (width + height);
		// Negative sits inside the panel edge, positive outside it.
		float out = (place.nextFloat() * 1.6f - 0.6f) * 12 * scale;
		float x;
		float y;
		if (along < width)
		{
			x = along;
			y = -Math.min(out, room.top);
		}
		else if (along < width + height)
		{
			x = width + Math.min(out, room.right);
			y = along - width;
		}
		else if (along < 2 * width + height)
		{
			x = 2 * width + height - along;
			y = height + Math.min(out, room.bottom);
		}
		else
		{
			x = -Math.min(out, room.left);
			y = 2 * (width + height) - along;
		}
		float size = (3 + place.nextFloat() * 4) * scale * twinkle;
		return new Particle(x, y, size, twinkle, u * 0.8f, place.nextFloat());
	}

	/**
	 * One piece of the Pet burst, flying out of the icon once as the effects start. A piece headed off
	 * screen is turned back toward the open side, and anything still past the room is clamped to it.
	 *
	 * @return null before the piece launches and after it has faded
	 */
	static Particle burstParticle(long seed, int index, long millis, float centerX, float centerY,
		int width, int totalHeight, Insets room, float scale)
	{
		Random random = new Random(mix(seed, index, -1));
		double angle = random.nextDouble() * 2 * Math.PI;
		float distance = (50 + random.nextFloat() * 70) * scale;
		long lifetime = 1100 + random.nextInt(700);
		long delay = BURST_DELAY_MILLIS + random.nextInt(150);
		float size = (4 + random.nextFloat() * 4) * scale;
		float spin = (random.nextFloat() * 2 - 1) * 6;
		float hue = random.nextFloat();

		float u = (millis - delay) / (float) lifetime;
		if (u <= 0f || u >= 1f)
		{
			return null;
		}

		float minX = -room.left;
		float maxX = width + room.right;
		float minY = -room.top;
		float maxY = totalHeight + room.bottom;
		float gravity = 20 * scale;

		float dx = (float) Math.cos(angle) * distance;
		float dy = (float) Math.sin(angle) * distance;
		if (centerX + dx < minX || centerX + dx > maxX)
		{
			dx = -dx;
		}
		if (centerY + dy + gravity < minY || centerY + dy + gravity > maxY)
		{
			dy = -dy;
		}

		float travel = 1 - (1 - u) * (1 - u) * (1 - u);
		float x = clamp(centerX + dx * travel, minX, maxX);
		float y = clamp(centerY + dy * travel + gravity * u * u, minY, maxY);
		return new Particle(x, y, size * (1 - u * 0.5f), 1 - u * u, spin * u, hue);
	}

	/** Edge glow and halo - everything that sits beneath the icon. */
	void drawUnderIcon(Graphics2D graphics, Frame frame)
	{
		Profile profile = profile(frame.getTier());
		Composite composite = graphics.getComposite();
		Paint paint = graphics.getPaint();
		Stroke stroke = graphics.getStroke();
		Object antialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		try
		{
			if (profile.isEdgeGlow())
			{
				drawEdgeGlow(graphics, frame, profile);
			}
			if (profile.isHalo())
			{
				drawHalo(graphics, frame, profile);
			}
		}
		finally
		{
			restore(graphics, composite, paint, stroke, antialias);
		}
	}

	/** Twinkles, sparkles and the burst - drawn over the icon. */
	void drawOverIcon(Graphics2D graphics, Frame frame)
	{
		Profile profile = profile(frame.getTier());
		if (profile.getTwinkles() == 0 && profile.getSparkles() == 0 && profile.getBurstParticles() == 0)
		{
			return;
		}
		Composite composite = graphics.getComposite();
		Paint paint = graphics.getPaint();
		Stroke stroke = graphics.getStroke();
		Object antialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		try
		{
			for (int i = 0; i < profile.getTwinkles(); i++)
			{
				Particle twinkle = twinkle(frame.getSeed(), i, frame.getMillis(), frame.getIconCenterX(),
					frame.getIconCenterY(), frame.getIconSize(), frame.getScale());
				if (twinkle != null)
				{
					drawStar(graphics, twinkle, particleColor(frame, profile, twinkle.getHue()), frame.getAlpha() * 0.9f);
				}
			}
			for (int i = 0; i < profile.getSparkles(); i++)
			{
				Particle sparkle = sparkle(frame.getSeed(), i, frame.getMillis(), frame.getPanelWidth(),
					frame.getPanelHeight(), frame.getRoom(), frame.getScale());
				if (sparkle != null)
				{
					drawStar(graphics, sparkle, particleColor(frame, profile, sparkle.getHue()), frame.getAlpha());
				}
			}
			for (int i = 0; i < profile.getBurstParticles(); i++)
			{
				Particle piece = burstParticle(frame.getSeed(), i, frame.getMillis(), frame.getIconCenterX(),
					frame.getIconCenterY(), frame.getPanelWidth(), frame.getTotalHeight(), frame.getRoom(), frame.getScale());
				if (piece == null)
				{
					continue;
				}
				Color color = particleColor(frame, profile, piece.getHue());
				// Every third piece a star, the rest confetti.
				if (i % 3 == 0)
				{
					drawStar(graphics, piece, color, frame.getAlpha());
				}
				else
				{
					drawConfetti(graphics, piece, color, frame.getAlpha());
				}
			}
		}
		finally
		{
			restore(graphics, composite, paint, stroke, antialias);
		}
	}

	private static void drawHalo(Graphics2D graphics, Frame frame, Profile profile)
	{
		float pulse = 0.25f + 0.25f * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * frame.getMillis() / HALO_PULSE_MILLIS));
		float radius = frame.getIconSize() * (profile.isRainbow() ? 1.2f : 0.9f);
		Color color = cycleColor(frame, profile, 0.5f);
		graphics.setComposite(alpha(pulse * frame.getAlpha()));
		graphics.setPaint(new RadialGradientPaint(
			new Point2D.Float(frame.getIconCenterX(), frame.getIconCenterY()), radius,
			new float[]{0f, 1f},
			new Color[]{withAlpha(color, 255), withAlpha(color, 0)}));
		graphics.fill(new Rectangle2D.Float(frame.getIconCenterX() - radius, frame.getIconCenterY() - radius, radius * 2, radius * 2));
	}

	private static void drawEdgeGlow(Graphics2D graphics, Frame frame, Profile profile)
	{
		float pulse = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * frame.getMillis() / EDGE_PULSE_MILLIS);
		Insets room = frame.getRoom();
		float scale = frame.getScale();
		graphics.setStroke(new BasicStroke(3 * scale));
		graphics.setColor(cycleColor(frame, profile, 0.25f));
		for (int ring = 1; ring <= 4; ring++)
		{
			float spread = ring * 3 * scale;
			float left = Math.min(spread, room.left);
			float top = Math.min(spread, room.top);
			float right = Math.min(spread, room.right);
			float bottom = Math.min(spread, room.bottom);
			graphics.setComposite(alpha(0.35f * pulse * (1 - ring / 5f) * frame.getAlpha()));
			graphics.draw(new RoundRectangle2D.Float(-left, -top,
				frame.getPanelWidth() + left + right, frame.getPanelHeight() + top + bottom,
				12 * scale, 12 * scale));
		}
	}

	/** A four-pointed glint with a white core. */
	private static void drawStar(Graphics2D graphics, Particle particle, Color color, float alpha)
	{
		AffineTransform transform = graphics.getTransform();
		graphics.translate(particle.getX(), particle.getY());
		graphics.rotate(particle.getRotation());
		float size = particle.getSize();
		float waist = size * 0.25f;
		Path2D.Float star = new Path2D.Float();
		star.moveTo(0, -size);
		star.lineTo(waist, -waist);
		star.lineTo(size, 0);
		star.lineTo(waist, waist);
		star.lineTo(0, size);
		star.lineTo(-waist, waist);
		star.lineTo(-size, 0);
		star.lineTo(-waist, -waist);
		star.closePath();
		graphics.setComposite(alpha(particle.getAlpha() * alpha));
		graphics.setColor(color);
		graphics.fill(star);
		graphics.setColor(Color.WHITE);
		graphics.fill(new Rectangle2D.Float(-waist / 2, -waist / 2, waist, waist));
		graphics.setTransform(transform);
	}

	private static void drawConfetti(Graphics2D graphics, Particle particle, Color color, float alpha)
	{
		AffineTransform transform = graphics.getTransform();
		graphics.translate(particle.getX(), particle.getY());
		graphics.rotate(particle.getRotation());
		float size = particle.getSize();
		graphics.setComposite(alpha(particle.getAlpha() * alpha));
		graphics.setColor(color);
		graphics.fill(new Rectangle2D.Float(-size / 2, -size / 4, size, size / 2));
		graphics.setTransform(transform);
	}

	/** The frame's colour, or for a rainbow tier a hue cycling over time, offset by {@code hueOffset}. */
	private static Color cycleColor(Frame frame, Profile profile, float hueOffset)
	{
		if (!profile.isRainbow())
		{
			return frame.getColor();
		}
		float hue = frame.getMillis() / (float) RAINBOW_MILLIS + hueOffset;
		return new Color(Color.HSBtoRGB(hue - (float) Math.floor(hue), 0.55f, 1f));
	}

	private static Color particleColor(Frame frame, Profile profile, float hue)
	{
		return profile.isRainbow() ? new Color(Color.HSBtoRGB(hue, 0.6f, 1f)) : frame.getColor();
	}

	/**
	 * Scrambles the inputs into a seed (SplitMix64's finalizer). {@link Random}'s first values barely
	 * differ between neighbouring seeds, so seeding by index directly lines every particle up.
	 */
	private static long mix(long seed, int index, long salt)
	{
		long z = seed * 0x9E3779B97F4A7C15L + index * 0xBF58476D1CE4E5B9L + salt * 0x94D049BB133111EBL;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static Color withAlpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	private static AlphaComposite alpha(float alpha)
	{
		return AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(alpha, 0f, 1f));
	}

	private static float clamp(float value, float min, float max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static void restore(Graphics2D graphics, Composite composite, Paint paint, Stroke stroke, Object antialias)
	{
		graphics.setComposite(composite);
		graphics.setPaint(paint);
		graphics.setStroke(stroke);
		if (antialias != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
		}
	}
}
