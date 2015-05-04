package cofh.tweak.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public class Matrix4 {

	// m<row><column>
	public float m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33;

	public Matrix4() {

		m00 = m11 = m22 = m33 = 1;
	}

	public Matrix4(float d00, float d01, float d02, float d03,
			float d10, float d11, float d12, float d13,
			float d20, float d21, float d22, float d23,
			float d30, float d31, float d32, float d33) {

		m00 = d00;
		m01 = d01;
		m02 = d02;
		m03 = d03;
		m10 = d10;
		m11 = d11;
		m12 = d12;
		m13 = d13;
		m20 = d20;
		m21 = d21;
		m22 = d22;
		m23 = d23;
		m30 = d30;
		m31 = d31;
		m32 = d32;
		m33 = d33;
	}

	public Matrix4(float[] d) {

		set(d);
	}

	public Matrix4(Matrix4 mat) {

		set(mat);
	}

	public Matrix4 setIdentity() {

		m00 = m11 = m22 = m33 = 1;
		m01 = m02 = m03 = m10 = m12 = m13 = m20 = m21 = m23 = m30 = m31 = m32 = 0;

		return this;
	}

	public Matrix4 orthogonal(float left, float right, float bottom, float top, float near, float far) {

		setIdentity();
		transpose();

		m00 = 2 / (right - left);
		m11 = 2 / (top - bottom);
		m22 = -2 / (far - near);
		m32 = (far + near) / (far - near);
		m30 = (right + left) / (right - left);
		m31 = (top + bottom) / (top - bottom);
		transpose();

		return this;
	}

	public Matrix4 orthogonal(float width, float height, float near, float far) {

		return orthogonal(-width, width, -height, height, near, far);
	}

	public Matrix4 orthogonal(float size, float near, float far) {

		return orthogonal(-size, size, -size, size, near, far);
	}

	public Matrix4 perspective(float left, float right, float bottom, float top, float near, float far) {

		setIdentity();
		transpose();

		m00 = 2 * near / (right - left);
		m11 = 2 * near / (top - bottom);
		m22 = -(far + near) / (far - near);
		m23 = -1;
		m32 = -2 * far * near / (far - near);
		m20 = (right + left) / (right - left);
		m21 = (top + bottom) / (top - bottom);
		m33 = 0;
		transpose();

		return this;
	}

	public Matrix4 perspective(float width, float height, float near, float far) {

		return perspective(-width, width, -height, height, near, far);
	}

	public Matrix4 perspective(float size, float near, float far) {

		return perspective(-size, size, -size, size, near, far);
	}

	public Matrix4 camera(Vector3 facing) {

		Vector3 x, y, z;
		z = facing.clone().normalize();
		x = new Vector3(0, 1, 0).crossProduct(z);
		y = z.clone().crossProduct(x).normalize();
		x.normalize();

		m00 = x.x;
		m01 = x.y;
		m02 = x.z;
		m03 = -x.dotProduct(facing);
		m10 = y.x;
		m11 = y.y;
		m12 = y.z;
		m13 = -y.dotProduct(facing);
		m20 = z.x;
		m21 = z.y;
		m22 = z.z;
		m23 = -z.dotProduct(facing);
		m30 = 0;
		m31 = 0;
		m32 = 0;
		m33 = 1;

		return this;
	}

	public Matrix4 translate(Vector3 vec) {

		m03 += m00 * vec.x + m01 * vec.y + m02 * vec.z;
		m13 += m10 * vec.x + m11 * vec.y + m12 * vec.z;
		m23 += m20 * vec.x + m21 * vec.y + m22 * vec.z;
		m33 += m30 * vec.x + m31 * vec.y + m32 * vec.z;

		return this;
	}

	public Matrix4 translate(float x, float y, float z) {

		m03 += m00 * x + m01 * y + m02 * z;
		m13 += m10 * x + m11 * y + m12 * z;
		m23 += m20 * x + m21 * y + m22 * z;
		m33 += m30 * x + m31 * y + m32 * z;

		return this;
	}

	public Matrix4 scale(Vector3 vec) {

		m00 *= vec.x;
		m10 *= vec.x;
		m20 *= vec.x;
		m30 *= vec.x;
		m01 *= vec.y;
		m11 *= vec.y;
		m21 *= vec.y;
		m31 *= vec.y;
		m02 *= vec.z;
		m12 *= vec.z;
		m22 *= vec.z;
		m32 *= vec.z;

		return this;
	}

	public Matrix4 rotate(float angle, Vector3 axis) {

		float c = (float) Math.cos(angle);
		float s = (float) Math.sin(angle);
		float mc = 1.0f - c;
		float xy = axis.x * axis.y;
		float yz = axis.y * axis.z;
		float xz = axis.x * axis.z;
		float xs = axis.x * s;
		float ys = axis.y * s;
		float zs = axis.z * s;

		float f00 = axis.x * axis.x * mc + c;
		float f10 = xy * mc + zs;
		float f20 = xz * mc - ys;

		float f01 = xy * mc - zs;
		float f11 = axis.y * axis.y * mc + c;
		float f21 = yz * mc + xs;

		float f02 = xz * mc + ys;
		float f12 = yz * mc - xs;
		float f22 = axis.z * axis.z * mc + c;

		float t00 = m00 * f00 + m01 * f10 + m02 * f20;
		float t10 = m10 * f00 + m11 * f10 + m12 * f20;
		float t20 = m20 * f00 + m21 * f10 + m22 * f20;
		float t30 = m30 * f00 + m31 * f10 + m32 * f20;
		float t01 = m00 * f01 + m01 * f11 + m02 * f21;
		float t11 = m10 * f01 + m11 * f11 + m12 * f21;
		float t21 = m20 * f01 + m21 * f11 + m22 * f21;
		float t31 = m30 * f01 + m31 * f11 + m32 * f21;
		m02 = m00 * f02 + m01 * f12 + m02 * f22;
		m12 = m10 * f02 + m11 * f12 + m12 * f22;
		m22 = m20 * f02 + m21 * f12 + m22 * f22;
		m32 = m30 * f02 + m31 * f12 + m32 * f22;
		m00 = t00;
		m10 = t10;
		m20 = t20;
		m30 = t30;
		m01 = t01;
		m11 = t11;
		m21 = t21;
		m31 = t31;

		return this;
	}

	public Matrix4 leftMultiply(Matrix4 mat) {

		float n00 = m00 * mat.m00 + m10 * mat.m01 + m20 * mat.m02 + m30 * mat.m03;
		float n01 = m01 * mat.m00 + m11 * mat.m01 + m21 * mat.m02 + m31 * mat.m03;
		float n02 = m02 * mat.m00 + m12 * mat.m01 + m22 * mat.m02 + m32 * mat.m03;
		float n03 = m03 * mat.m00 + m13 * mat.m01 + m23 * mat.m02 + m33 * mat.m03;
		float n10 = m00 * mat.m10 + m10 * mat.m11 + m20 * mat.m12 + m30 * mat.m13;
		float n11 = m01 * mat.m10 + m11 * mat.m11 + m21 * mat.m12 + m31 * mat.m13;
		float n12 = m02 * mat.m10 + m12 * mat.m11 + m22 * mat.m12 + m32 * mat.m13;
		float n13 = m03 * mat.m10 + m13 * mat.m11 + m23 * mat.m12 + m33 * mat.m13;
		float n20 = m00 * mat.m20 + m10 * mat.m21 + m20 * mat.m22 + m30 * mat.m23;
		float n21 = m01 * mat.m20 + m11 * mat.m21 + m21 * mat.m22 + m31 * mat.m23;
		float n22 = m02 * mat.m20 + m12 * mat.m21 + m22 * mat.m22 + m32 * mat.m23;
		float n23 = m03 * mat.m20 + m13 * mat.m21 + m23 * mat.m22 + m33 * mat.m23;
		float n30 = m00 * mat.m30 + m10 * mat.m31 + m20 * mat.m32 + m30 * mat.m33;
		float n31 = m01 * mat.m30 + m11 * mat.m31 + m21 * mat.m32 + m31 * mat.m33;
		float n32 = m02 * mat.m30 + m12 * mat.m31 + m22 * mat.m32 + m32 * mat.m33;
		float n33 = m03 * mat.m30 + m13 * mat.m31 + m23 * mat.m32 + m33 * mat.m33;

		m00 = n00;
		m01 = n01;
		m02 = n02;
		m03 = n03;
		m10 = n10;
		m11 = n11;
		m12 = n12;
		m13 = n13;
		m20 = n20;
		m21 = n21;
		m22 = n22;
		m23 = n23;
		m30 = n30;
		m31 = n31;
		m32 = n32;
		m33 = n33;

		return this;
	}

	public Matrix4 multiply(Matrix4 mat) {

		float n00 = m00 * mat.m00 + m01 * mat.m10 + m02 * mat.m20 + m03 * mat.m30;
		float n01 = m00 * mat.m01 + m01 * mat.m11 + m02 * mat.m21 + m03 * mat.m31;
		float n02 = m00 * mat.m02 + m01 * mat.m12 + m02 * mat.m22 + m03 * mat.m32;
		float n03 = m00 * mat.m03 + m01 * mat.m13 + m02 * mat.m23 + m03 * mat.m33;
		float n10 = m10 * mat.m00 + m11 * mat.m10 + m12 * mat.m20 + m13 * mat.m30;
		float n11 = m10 * mat.m01 + m11 * mat.m11 + m12 * mat.m21 + m13 * mat.m31;
		float n12 = m10 * mat.m02 + m11 * mat.m12 + m12 * mat.m22 + m13 * mat.m32;
		float n13 = m10 * mat.m03 + m11 * mat.m13 + m12 * mat.m23 + m13 * mat.m33;
		float n20 = m20 * mat.m00 + m21 * mat.m10 + m22 * mat.m20 + m23 * mat.m30;
		float n21 = m20 * mat.m01 + m21 * mat.m11 + m22 * mat.m21 + m23 * mat.m31;
		float n22 = m20 * mat.m02 + m21 * mat.m12 + m22 * mat.m22 + m23 * mat.m32;
		float n23 = m20 * mat.m03 + m21 * mat.m13 + m22 * mat.m23 + m23 * mat.m33;
		float n30 = m30 * mat.m00 + m31 * mat.m10 + m32 * mat.m20 + m33 * mat.m30;
		float n31 = m30 * mat.m01 + m31 * mat.m11 + m32 * mat.m21 + m33 * mat.m31;
		float n32 = m30 * mat.m02 + m31 * mat.m12 + m32 * mat.m22 + m33 * mat.m32;
		float n33 = m30 * mat.m03 + m31 * mat.m13 + m32 * mat.m23 + m33 * mat.m33;

		m00 = n00;
		m01 = n01;
		m02 = n02;
		m03 = n03;
		m10 = n10;
		m11 = n11;
		m12 = n12;
		m13 = n13;
		m20 = n20;
		m21 = n21;
		m22 = n22;
		m23 = n23;
		m30 = n30;
		m31 = n31;
		m32 = n32;
		m33 = n33;

		return this;
	}

	public Matrix4 transpose() {

		float n00 = m00;
		float n10 = m01;
		float n20 = m02;
		float n30 = m03;
		float n01 = m10;
		float n11 = m11;
		float n21 = m12;
		float n31 = m13;
		float n02 = m20;
		float n12 = m21;
		float n22 = m22;
		float n32 = m23;
		float n03 = m30;
		float n13 = m31;
		float n23 = m32;
		float n33 = m33;

		m00 = n00;
		m01 = n01;
		m02 = n02;
		m03 = n03;
		m10 = n10;
		m11 = n11;
		m12 = n12;
		m13 = n13;
		m20 = n20;
		m21 = n21;
		m22 = n22;
		m23 = n23;
		m30 = n30;
		m31 = n31;
		m32 = n32;
		m33 = n33;

		return this;
	}

	@Override
	public Matrix4 clone() {

		return new Matrix4(this);
	}

	public Matrix4 set(Matrix4 mat) {

		m00 = mat.m00;
		m01 = mat.m01;
		m02 = mat.m02;
		m03 = mat.m03;
		m10 = mat.m10;
		m11 = mat.m11;
		m12 = mat.m12;
		m13 = mat.m13;
		m20 = mat.m20;
		m21 = mat.m21;
		m22 = mat.m22;
		m23 = mat.m23;
		m30 = mat.m30;
		m31 = mat.m31;
		m32 = mat.m32;
		m33 = mat.m33;

		return this;
	}

	public Matrix4 set(float[] mat) {

		m00 = mat[0];
		m01 = mat[1];
		m02 = mat[2];
		m03 = mat[3];
		m10 = mat[4];
		m11 = mat[5];
		m12 = mat[6];
		m13 = mat[7];
		m20 = mat[8];
		m21 = mat[9];
		m22 = mat[10];
		m23 = mat[11];
		m30 = mat[12];
		m31 = mat[13];
		m32 = mat[14];
		m33 = mat[15];
		transpose();

		return this;
	}

	private void mult3x3(Vector3 vec) {

		float x = m00 * vec.x + m01 * vec.y + m02 * vec.z;
		float y = m10 * vec.x + m11 * vec.y + m12 * vec.z;
		float z = m20 * vec.x + m21 * vec.y + m22 * vec.z;

		vec.x = x;
		vec.y = y;
		vec.z = z;
	}

	public void apply(Vector3 vec) {

		mult3x3(vec);
		vec.add(m03, m13, m23);
	}

	public void applyN(Vector3 vec) {

		mult3x3(vec);
		vec.normalize();
	}

	@Override
	public String toString() {

		DecimalFormat dec = new DecimalFormat("+0.00000;-0.00000");
		dec.setRoundingMode(RoundingMode.HALF_UP);
		StringBuffer r = new StringBuffer(1 + (10 + 1) * 16);
		r.append('[').append(dec.format(m00)).append(',').append(dec.format(m01)).append(',').append(dec.format(m02)).
				append(',').append(dec.format(m03)).append(']').append('\n');
		r.append('[').append(dec.format(m10)).append(',').append(dec.format(m11)).append(',').append(dec.format(m12)).
				append(',').append(dec.format(m13)).append(']').append('\n');
		r.append('[').append(dec.format(m20)).append(',').append(dec.format(m21)).append(',').append(dec.format(m22)).
				append(',').append(dec.format(m23)).append(']').append('\n');
		r.append('[').append(dec.format(m30)).append(',').append(dec.format(m31)).append(',').append(dec.format(m32)).
				append(',').append(dec.format(m33)).append(']');
		return r.toString();
	}
}
