package cofh.tweak.util;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.util.AxisAlignedBB;

public class Frustrum implements ICamera {

	private Vector4[] c = new Vector4[6];

	private float x, y, z;

	public Frustrum() {

		this(new Matrix4(), new Matrix4());
	}

	public Frustrum(Matrix4 view, Matrix4 perspective) {

		this(view, perspective, 0, 0, 0);
	}

	public Frustrum(Matrix4 view, Matrix4 perspective, float x, float y, float z) {

		for (int i = 0; i < c.length; ++i) {
			c[i] = new Vector4();
		}
		set(view, perspective).setPosition(x, y, z);
	}

	@Override
	public void setPosition(double x, double y, double z) {

		setPosition((float) x, (float) y, (float) z);
	}

	public void setPosition(float x, float y, float z) {

		this.x = x;
		this.y = y;
		this.z = z;
	}

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB bb) {

		bb = bb.copy().offset(-x, -y, -z);
		return inFrustum((float) bb.minX, (float) bb.minY, (float) bb.minZ, (float) bb.maxX, (float) bb.maxY, (float) bb.maxZ);
	}

	public boolean inFrustum(float x1, float y1, float z1, float x2, float y2, float z2) {

		boolean r;
		int i = 5;
		do {
			r = !dot(c[i], x1, y1, z1, x2, y2, z2);
		} while (r && i --> 0);
		return r;
	}

	private boolean dot(Vector4 c, float x1, float y1, float z1, float x2, float y2, float z2) {

		return c.dotProduct(x1, y1, z1) <= 0.0f &&
				c.dotProduct(x2, y1, z1) <= .0f &&
				c.dotProduct(x1, y2, z1) <= .0f &&
				c.dotProduct(x2, y2, z1) <= .0f &&
				c.dotProduct(x1, y1, z2) <= .0f &&
				c.dotProduct(x2, y1, z2) <= .0f &&
				c.dotProduct(x1, y2, z2) <= .0f &&
				c.dotProduct(x2, y2, z2) <= .0f;
	}

	public Frustrum set(Matrix4 view, Matrix4 perspective) {

		Matrix4 clip = new Matrix4(view).multiply(perspective);
		int i = 0;
		c[i].x = clip.m03 - clip.m00;
		c[i].y = clip.m13 - clip.m10;
		c[i].z = clip.m23 - clip.m20;
		c[i].w = clip.m33 - clip.m30;
		c[i].normalizeFrustrum();

		++i;
		c[i].x = clip.m03 + clip.m00;
		c[i].y = clip.m13 + clip.m10;
		c[i].z = clip.m23 + clip.m20;
		c[i].w = clip.m33 + clip.m30;
		c[i].normalizeFrustrum();

		++i;
		c[i].x = clip.m03 + clip.m01;
		c[i].y = clip.m13 + clip.m11;
		c[i].z = clip.m23 + clip.m21;
		c[i].w = clip.m33 + clip.m31;
		c[i].normalizeFrustrum();

		++i;
		c[i].x = clip.m03 - clip.m01;
		c[i].y = clip.m13 - clip.m11;
		c[i].z = clip.m23 - clip.m21;
		c[i].w = clip.m33 - clip.m31;
		c[i].normalizeFrustrum();

		++i;
		c[i].x = clip.m03 - clip.m02;
		c[i].y = clip.m13 - clip.m12;
		c[i].z = clip.m23 - clip.m22;
		c[i].w = clip.m33 - clip.m32;
		c[i].normalizeFrustrum();

		++i;
		c[i].x = clip.m03 + clip.m02;
		c[i].y = clip.m13 + clip.m12;
		c[i].z = clip.m23 + clip.m22;
		c[i].w = clip.m33 + clip.m32;
		c[i].normalizeFrustrum();
		++i;

		return this;
	}

}
