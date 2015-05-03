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

		set(view, perspective);
		x = y = z = 0;
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
			r = dot(c[i], x1, y1, z1, x2, y2, z2);
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
		c[0] = new Vector4();
		c[0].x = clip.m03 - clip.m00;
		c[0].y = clip.m13 - clip.m10;
		c[0].z = clip.m23 - clip.m20;
		c[0].w = clip.m33 - clip.m30;
		c[0].normalize();
		c[1] = new Vector4();
		c[1].x = clip.m03 + clip.m00;
		c[1].y = clip.m13 + clip.m10;
		c[1].z = clip.m23 + clip.m20;
		c[1].w = clip.m33 + clip.m30;
		c[1].normalize();
		c[2] = new Vector4();
		c[2].x = clip.m03 + clip.m01;
		c[2].y = clip.m13 + clip.m11;
		c[2].z = clip.m23 + clip.m21;
		c[2].w = clip.m33 + clip.m31;
		c[2].normalize();
		c[3] = new Vector4();
		c[3].x = clip.m03 - clip.m01;
		c[3].y = clip.m13 - clip.m11;
		c[3].z = clip.m23 - clip.m21;
		c[3].w = clip.m33 - clip.m31;
		c[3].normalize();
		c[4] = new Vector4();
		c[4].x = clip.m03 - clip.m02;
		c[4].y = clip.m13 - clip.m12;
		c[4].z = clip.m23 - clip.m22;
		c[4].w = clip.m33 - clip.m32;
		c[4].normalize();
		c[5] = new Vector4();
		c[5].x = clip.m03 + clip.m02;
		c[5].y = clip.m13 + clip.m12;
		c[5].z = clip.m23 + clip.m22;
		c[5].w = clip.m33 + clip.m32;
		c[5].normalize();

		return this;
	}

}
