package com.mirko.shiftmenu.util;

import com.mirko.shiftmenu.module.ModuleManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Frame-rate independent smoothing for UI animations (hover fades, toggles, scrolling, open/close).
 * Call {@link #frame()} once per frame, then {@link #get} for each animated value.
 */
public final class Anim {
	private final Map<String, float[]> values = new HashMap<>();
	private long last = System.nanoTime();
	private float dt;

	public void frame() {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
	}

	public float dt() {
		return dt;
	}

	/** Moves the value named key towards target. Higher speed = snappier (8 = smooth, 20 = quick). */
	public float get(String key, float target, float speed) {
		float[] v = values.get(key);
		if (v == null) {
			v = new float[]{target};
			values.put(key, v);
			return target;
		}
		if (!ModuleManager.SETTINGS.animations.on()) {
			v[0] = target;
		} else {
			float mult = ModuleManager.SETTINGS.animationSpeed.getF();
			v[0] += (target - v[0]) * (1f - (float) Math.exp(-speed * mult * dt));
			if (Math.abs(target - v[0]) < 0.0015f) v[0] = target;
		}
		return v[0];
	}

	/** Same, but starts from a given value the first time (for "animate in" effects). */
	public float get(String key, float target, float speed, float start) {
		if (!values.containsKey(key)) values.put(key, new float[]{start});
		return get(key, target, speed);
	}

	public void set(String key, float value) {
		values.put(key, new float[]{value});
	}

	public void reset(String key) {
		values.remove(key);
	}

	/** Smooth ease-out curve for 0..1 progress. */
	public static float easeOut(float t) {
		t = Math.max(0, Math.min(1, t));
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}
}
