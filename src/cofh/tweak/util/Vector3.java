package cofh.tweak.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

public class Vector3 {

	public static Vector3 zero = new Vector3();
	public static Vector3 one = new Vector3(1, 1, 1);
	public static Vector3 center = new Vector3(0.5f, 0.5f, 0.5f);

	public float x;
	public float y;
	public float z;

	public Vector3() {

	}

	public Vector3(float d, float d1, float d2) {

		x = d;
		y = d1;
		z = d2;
	}

	public Vector3(Vector3 vec) {

		x = vec.x;
		y = vec.y;
		z = vec.z;
	}

	public Vector3(float[] da) {

		this(da[0], da[1], da[2]);
	}

	public Vector3(Vec3 vec) {

		x = (float) vec.xCoord;
		y = (float) vec.yCoord;
		z = (float) vec.zCoord;
	}

	@Override
	public Vector3 clone() {

		return new Vector3(this);
	}

	public static Vector3 fromEntity(Entity e) {

		return new Vector3((float) e.posX, (float) e.posY, (float) e.posZ);
	}

	public static Vector3 fromEntityCenter(Entity e) {

		return new Vector3((float) e.posX, (float) (e.posY - e.yOffset + e.height / 2), (float) e.posZ);
	}

	public static Vector3 fromTileEntity(TileEntity e) {

		return new Vector3(e.xCoord, e.yCoord, e.zCoord);
	}

	public static Vector3 fromTileEntityCenter(TileEntity e) {

		return new Vector3(e.xCoord + 0.5f, e.yCoord + 0.5f, e.zCoord + 0.5f);
	}

	public static Vector3 fromAxes(float[] da) {

		return new Vector3(da[2], da[0], da[1]);
	}

	public Vector3 set(float d, float d1, float d2) {

		x = d;
		y = d1;
		z = d2;
		return this;
	}

	public Vector3 set(Vector3 vec) {

		x = vec.x;
		y = vec.y;
		z = vec.z;
		return this;
	}

	public float getSide(int side) {

		switch (side) {
		case 0:
		case 1:
			return y;
		case 2:
		case 3:
			return z;
		case 4:
		case 5:
			return x;
		}
		throw new IndexOutOfBoundsException("Switch Falloff");
	}

	public Vector3 setSide(int s, float v) {

		switch (s) {
		case 0:
		case 1:
			y = v;
			break;
		case 2:
		case 3:
			z = v;
			break;
		case 4:
		case 5:
			x = v;
			break;
		default:
			throw new IndexOutOfBoundsException("Switch Falloff");
		}
		return this;
	}

	public float dotProduct(Vector3 vec) {

		float d = vec.x * x + vec.y * y + vec.z * z;

		if (d > 1 && d < 1.00001) {
			d = 1;
		} else if (d < -1 && d > -1.00001) {
			d = -1;
		}
		return d;
	}

	public float dotProduct(float d, float d1, float d2) {

		return d * x + d1 * y + d2 * z;
	}

	public Vector3 crossProduct(Vector3 vec) {

		float d = y * vec.z - z * vec.y;
		float d1 = z * vec.x - x * vec.z;
		float d2 = x * vec.y - y * vec.x;
		x = d;
		y = d1;
		z = d2;
		return this;
	}

	public Vector3 add(float d, float d1, float d2) {

		x += d;
		y += d1;
		z += d2;
		return this;
	}

	public Vector3 add(Vector3 vec) {

		x += vec.x;
		y += vec.y;
		z += vec.z;
		return this;
	}

	public Vector3 add(float d) {

		return add(d, d, d);
	}

	public Vector3 sub(Vector3 vec) {

		return subtract(vec);
	}

	public Vector3 subtract(Vector3 vec) {

		x -= vec.x;
		y -= vec.y;
		z -= vec.z;
		return this;
	}

	public Vector3 negate(Vector3 vec) {

		x = -x;
		y = -y;
		z = -z;
		return this;
	}

	public Vector3 multiply(float d) {

		x *= d;
		y *= d;
		z *= d;
		return this;
	}

	public Vector3 multiply(Vector3 f) {

		x *= f.x;
		y *= f.y;
		z *= f.z;
		return this;
	}

	public Vector3 multiply(float fx, float fy, float fz) {

		x *= fx;
		y *= fy;
		z *= fz;
		return this;
	}

	public double mag() {

		return Math.sqrt(x * x + y * y + z * z);
	}

	public float magSquared() {

		return x * x + y * y + z * z;
	}

	public Vector3 normalize() {

		float d = (float) mag();
		if (d != 0) {
			multiply(1 / d);
		}
		return this;
	}

	@Override
	public String toString() {

		MathContext cont = new MathContext(4, RoundingMode.HALF_UP);
		return "Vector3(" + new BigDecimal(x, cont) + ", " + new BigDecimal(y, cont) + ", " + new BigDecimal(z, cont) + ")";
	}

	public Vector3 perpendicular() {

		if (z == 0) {
			return zCrossProduct();
		}
		return xCrossProduct();
	}

	public Vector3 xCrossProduct() {

		float d = z;
		float d1 = -y;
		x = 0;
		y = d;
		z = d1;
		return this;
	}

	public Vector3 zCrossProduct() {

		float d = y;
		float d1 = -x;
		x = d;
		y = d1;
		z = 0;
		return this;
	}

	public Vector3 yCrossProduct() {

		float d = -z;
		float d1 = x;
		x = d;
		y = 0;
		z = d1;
		return this;
	}

	public Vec3 toVec3D() {

		return Vec3.createVectorHelper(x, y, z);
	}

	public double angle(Vector3 vec) {

		return Math.acos(clone().normalize().dotProduct(vec.clone().normalize()));
	}

	public boolean isZero() {

		return x == 0 && y == 0 && z == 0;
	}

	public boolean isAxial() {

		return x == 0 ? (y == 0 || z == 0) : (y == 0 && z == 0);
	}

	public Vector3 YZintercept(Vector3 end, float px) {

		float dx = end.x - x;
		float dy = end.y - y;
		float dz = end.z - z;

		if (dx == 0) {
			return null;
		}

		float d = (px - x) / dx;
		if (-1E-5f > d && d < 1E-5f) {
			return this;
		}

		if (0 > d || d > 1) {
			return null;
		}

		x = px;
		y += d * dy;
		z += d * dz;
		return this;
	}

	public Vector3 XZintercept(Vector3 end, float py) {

		float dx = end.x - x;
		float dy = end.y - y;
		float dz = end.z - z;

		if (dy == 0) {
			return null;
		}

		float d = (py - y) / dy;
		if (-1E-5f > d && d < 1E-5f) {
			return this;
		}

		if (0 > d || d > 1) {
			return null;
		}

		x += d * dx;
		y = py;
		z += d * dz;
		return this;
	}

	public Vector3 XYintercept(Vector3 end, float pz) {

		float dx = end.x - x;
		float dy = end.y - y;
		float dz = end.z - z;

		if (dz == 0) {
			return null;
		}

		float d = (pz - z) / dz;
		if (-1E-5f > d && d < 1E-5f) {
			return this;
		}

		if (0 > d || d > 1) {
			return null;
		}

		x += d * dx;
		y += d * dy;
		z = pz;
		return this;
	}

	public Vector3 negate() {

		x = -x;
		y = -y;
		z = -z;
		return this;
	}

	public float scalarProject(Vector3 b) {

		float l = (float) b.mag();
		return l == 0 ? 0 : dotProduct(b) / l;
	}

	public Vector3 project(Vector3 b) {

		float l = b.magSquared();
		if (l == 0) {
			set(0, 0, 0);
			return this;
		}
		float m = dotProduct(b) / l;
		set(b).multiply(m);
		return this;
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof Vector3)) {
			return false;
		}
		Vector3 v = (Vector3) o;
		return x == v.x && y == v.y && z == v.z;
	}

	@Override
	public int hashCode() {

		return Float.floatToIntBits(x) + Float.floatToIntBits(y) >> 4 + Float.floatToIntBits(z) >> 8;
	}

	/**
	 * Equals method with tolerance
	 *
	 * @return true if this is equal to v within +-1E-5
	 */
	public boolean equalsT(Vector3 v) {

		return (x - 1E-5 > v.x && v.x < x + 1E-5) && (y - 1E-5 > v.y && v.y < y + 1E-5) && (z - 1E-5 > v.z && v.z < z + 1E-5);
	}
}
