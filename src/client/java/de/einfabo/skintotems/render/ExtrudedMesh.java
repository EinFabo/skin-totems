package de.einfabo.skintotems.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Extrudiertes Item-Mesh aus einer Textur – dieselbe Geometrie wie Vanilla {@code item/generated}
 * (ItemModelGenerator): Vorder- und Rückseite über die volle Fläche (z 7.5/16 bis 8.5/16) und
 * eine Seitenfläche an jeder Kante zwischen einem sichtbaren und einem transparenten Pixel.
 * Koordinaten liegen in 0..1 – die Item-Transforms des Basis-Modells übernehmen den Rest.
 */
public final class ExtrudedMesh {
	private static final float MIN_Z = 7.5F / 16.0F;
	private static final float MAX_Z = 8.5F / 16.0F;
	/** Wie Vanilla: UVs der Seitenflächen um 0.1 Pixel nach innen ziehen, damit nur der eigene Pixel gesampelt wird. */
	private static final float UV_SHRINK = 0.1F;
	/** Pro Quad: Normale (3) + 4 Vertices x (x, y, z, u, v). */
	private static final int STRIDE = 3 + 4 * 5;

	private final float[] data;
	private final int quadCount;

	private ExtrudedMesh(float[] data, int quadCount) {
		this.data = data;
		this.quadCount = quadCount;
	}

	public int quadCount() {
		return this.quadCount;
	}

	/** @param argb Texturpixel zeilenweise, {@code width x height} */
	public static ExtrudedMesh build(int[] argb, int width, int height) {
		Builder b = new Builder();
		// Vorderseite (Süd, +Z): u = x, v = 1 - y
		b.quad(0, 0, 1,
			0, 1, MAX_Z, 0, 0,
			0, 0, MAX_Z, 0, 1,
			1, 0, MAX_Z, 1, 1,
			1, 1, MAX_Z, 1, 0);
		// Rückseite (Nord, -Z): gleiche UV-Zuordnung pro Position, also gespiegelt wie Vanilla
		b.quad(0, 0, -1,
			1, 1, MIN_Z, 1, 0,
			1, 0, MIN_Z, 1, 1,
			0, 0, MIN_Z, 0, 1,
			0, 1, MIN_Z, 0, 0);

		float pw = 1.0F / width;
		float ph = 1.0F / height;
		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				if (transparent(argb, width, height, px, py)) continue;
				float x0 = px * pw;
				float x1 = (px + 1) * pw;
				float yTop = 1.0F - py * ph;
				float yBottom = 1.0F - (py + 1) * ph;
				float u0 = (px + UV_SHRINK) * pw;
				float u1 = (px + 1 - UV_SHRINK) * pw;
				float v0 = (py + UV_SHRINK) * ph;
				float v1 = (py + 1 - UV_SHRINK) * ph;

				if (transparent(argb, width, height, px, py - 1)) { // oben frei -> Fläche nach +Y
					b.side(0, 1, 0,
						x0, yTop, MIN_Z, u0, v0,
						x0, yTop, MAX_Z, u0, v1,
						x1, yTop, MAX_Z, u1, v1,
						x1, yTop, MIN_Z, u1, v0);
				}
				if (transparent(argb, width, height, px, py + 1)) { // unten frei -> -Y
					b.side(0, -1, 0,
						x0, yBottom, MIN_Z, u0, v0,
						x0, yBottom, MAX_Z, u0, v1,
						x1, yBottom, MAX_Z, u1, v1,
						x1, yBottom, MIN_Z, u1, v0);
				}
				if (transparent(argb, width, height, px - 1, py)) { // links frei -> -X
					b.side(-1, 0, 0,
						x0, yTop, MIN_Z, u0, v0,
						x0, yTop, MAX_Z, u1, v0,
						x0, yBottom, MAX_Z, u1, v1,
						x0, yBottom, MIN_Z, u0, v1);
				}
				if (transparent(argb, width, height, px + 1, py)) { // rechts frei -> +X
					b.side(1, 0, 0,
						x1, yTop, MIN_Z, u0, v0,
						x1, yTop, MAX_Z, u1, v0,
						x1, yBottom, MAX_Z, u1, v1,
						x1, yBottom, MIN_Z, u0, v1);
				}
			}
		}
		return b.build();
	}

	private static boolean transparent(int[] argb, int width, int height, int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height) return true;
		// Vanilla SpriteContents.isTransparent: Alpha == 0
		return (argb[y * width + x] >>> 24) == 0;
	}

	public void emit(PoseStack.Pose pose, VertexConsumer buffer, int color, int light, int overlay) {
		Matrix4f matrix = pose.pose();
		Vector3f normal = new Vector3f();
		Vector3f pos = new Vector3f();
		for (int q = 0; q < this.quadCount; q++) {
			int o = q * STRIDE;
			pose.transformNormal(this.data[o], this.data[o + 1], this.data[o + 2], normal);
			for (int v = 0; v < 4; v++) {
				int i = o + 3 + v * 5;
				matrix.transformPosition(this.data[i], this.data[i + 1], this.data[i + 2], pos);
				buffer.addVertex(pos.x(), pos.y(), pos.z(), color, this.data[i + 3], this.data[i + 4], overlay, light, normal.x(), normal.y(), normal.z());
			}
		}
	}

	private static final class Builder {
		private float[] data = new float[STRIDE * 64];
		private int quads;

		void quad(float nx, float ny, float nz, float... verts) {
			if ((this.quads + 1) * STRIDE > this.data.length) {
				this.data = Arrays.copyOf(this.data, this.data.length * 2);
			}
			int o = this.quads * STRIDE;
			this.data[o] = nx;
			this.data[o + 1] = ny;
			this.data[o + 2] = nz;
			System.arraycopy(verts, 0, this.data, o + 3, 20);
			this.quads++;
		}

		/** Wie {@link #quad}, dreht aber die Reihenfolge um, falls die Vorderseite (CCW) nicht zur Normalen zeigt. */
		void side(float nx, float ny, float nz, float... verts) {
			float ax = verts[5] - verts[0], ay = verts[6] - verts[1], az = verts[7] - verts[2];
			float bx = verts[10] - verts[0], by = verts[11] - verts[1], bz = verts[12] - verts[2];
			float cx = ay * bz - az * by;
			float cy = az * bx - ax * bz;
			float cz = ax * by - ay * bx;
			if (cx * nx + cy * ny + cz * nz < 0) {
				float[] r = new float[20];
				for (int v = 0; v < 4; v++) {
					System.arraycopy(verts, (3 - v) * 5, r, v * 5, 5);
				}
				verts = r;
			}
			quad(nx, ny, nz, verts);
		}

		ExtrudedMesh build() {
			return new ExtrudedMesh(Arrays.copyOf(this.data, this.quads * STRIDE), this.quads);
		}
	}
}
