package cofh.tweak.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class Vector4 {

	public float x;
	public float y;
	public float z;
	public float w;

	public Vector4() {

	}

	public Vector4(float d, float d1, float d2, float d3) {

		x = d;
		y = d1;
		z = d2;
		w = d3;
	}

	public Vector4(Vector4 vec) {

		set(vec);
	}

	public Vector4(float[] da) {

		this(da[0], da[1], da[2], da[3]);
	}

	@Override
	public Vector4 clone() {

		return new Vector4(this);
	}

	public Vector4 set(float d, float d1, float d2, float d3) {

		x = d;
		y = d1;
		z = d2;
		w = d3;
		return this;
	}

	public Vector4 set(Vector4 vec) {

		x = vec.x;
		y = vec.y;
		z = vec.z;
		w = vec.w;
		return this;
	}

	public float dotProduct(Vector4 vec) {

		float d = vec.x * x + vec.y * y + vec.z * z + vec.w * w;

		if (d > 1 && d < 1.00001) {
			d = 1;
		} else if (d < -1 && d > -1.00001) {
			d = -1;
		}
		return d;
	}

	public float dotProduct(float d, float d1, float d2, float d3) {

		return d * x + d1 * y + d2 * z + d3 * w;
	}

	public float dotProduct(float d, float d1, float d2) {

		float r = d * x + d1 * y + d2 * z + w;
		return r;
	}

	public Vector4 crossProduct(Vector4 vec) {

		float d = y * vec.z - z * vec.y;
		float d1 = z * vec.x - x * vec.z;
		float d2 = x * vec.y - y * vec.x;
		x = d;
		y = d1;
		z = d2;
		return this;
	}

	public Vector4 add(float d, float d1, float d2, float d3) {

		x += d;
		y += d1;
		z += d2;
		w += d3;
		return this;
	}

	public Vector4 add(Vector4 vec) {

		x += vec.x;
		y += vec.y;
		z += vec.z;
		w += vec.w;
		return this;
	}

	public Vector4 add(float d) {

		return add(d, d, d, d);
	}

	public Vector4 sub(Vector4 vec) {

		return subtract(vec);
	}

	public Vector4 subtract(Vector4 vec) {

		x -= vec.x;
		y -= vec.y;
		z -= vec.z;
		w -= vec.w;
		return this;
	}

	public Vector4 negate(Vector4 vec) {

		x = -x;
		y = -y;
		z = -z;
		w = -w;
		return this;
	}

	public Vector4 multiply(float d) {

		x *= d;
		y *= d;
		z *= d;
		w *= d;
		return this;
	}

	public Vector4 multiply(Vector4 f) {

		x *= f.x;
		y *= f.y;
		z *= f.z;
		w *= f.w;
		return this;
	}

	public Vector4 multiply(float fx, float fy, float fz, float fw) {

		x *= fx;
		y *= fy;
		z *= fz;
		w *= fw;
		return this;
	}

	public double mag() {

		return Math.sqrt(x * x + y * y + z * z + w * w);
	}

	public float magSquared() {

		return x * x + y * y + z * z + w * w;
	}

	public Vector4 normalize() {

		float d = (float) mag();
		if (d != 0) {
			multiply(1 / d);
		}
		return this;
	}

	public Vector4 normalizeFrustrum() {

		float w = this.w;
		this.w = 0;
		float d = (float) mag();
		this.w = w;
		if (d != 0) {
			multiply(1 / d);
		}
		return this;
	}

	@Override
	public String toString() {

		MathContext cont = new MathContext(4, RoundingMode.HALF_UP);
		return "Vector4(" + new BigDecimal(x, cont) + ", " +
				new BigDecimal(y, cont) + ", " +
				new BigDecimal(z, cont) + ", " +
				new BigDecimal(w, cont) + ")";
	}

	public double angle(Vector4 vec) {

		return Math.acos(clone().normalize().dotProduct(vec.clone().normalize()));
	}

	public boolean isZero() {

		return x == 0 && y == 0 && z == 0 && w == 0;
	}

	public Vector4 negate() {

		x = -x;
		y = -y;
		z = -z;
		w = -w;
		return this;
	}

	public float scalarProject(Vector4 b) {

		float l = (float) b.mag();
		return l == 0 ? 0 : dotProduct(b) / l;
	}

	public Vector4 project(Vector4 b) {

		float l = b.magSquared();
		if (l == 0) {
			set(0, 0, 0, 0);
			return this;
		}
		float m = dotProduct(b) / l;
		set(b).multiply(m);
		return this;
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof Vector4)) {
			return false;
		}
		Vector4 v = (Vector4) o;
		return x == v.x && y == v.y && z == v.z && w == v.w;
	}

	@Override
	public int hashCode() {

		return Float.floatToIntBits(x) + Float.floatToIntBits(y) >> 4 + Float.floatToIntBits(z) >> 8 + Float.floatToIntBits(w) >> 12;
	}

	/**
	 * Equals method with tolerance
	 *
	 * @return true if this is equal to v within +-1E-5
	 */
	public boolean equalsT(Vector4 v) {

		return (x - 1E-5 > v.x && v.x < x + 1E-5) &&
				(y - 1E-5 > v.y && v.y < y + 1E-5) &&
				(z - 1E-5 > v.z && v.z < z + 1E-5) &&
				(w - 1E-5 > v.w && v.w < w + 1E-5);
	}
}
