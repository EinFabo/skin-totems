package de.einfabo.skintotems.plushie;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.einfabo.skintotems.SkinTotemsClient;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Die Plushie-Form aus {@code assets/skintotems/plushie/plushie.json} (per Texture Pack ersetzbar).
 *
 * <p>Format wie Minecrafts Entity-Modelle: Teile mit Drehpunkt ({@code pivot}), Drehung in Grad,
 * Skalierung und Quadern. Ein Quader bekommt die Skin-Koordinate ({@code uv}) wie bei
 * {@code CubeListBuilder.texOffs}; die Flächen werden genau wie beim Spielermodell gemappt.
 * {@code size} ist die Größe in Skin-Pixeln, {@code scale} am Teil verkleinert/vergrößert es,
 * ohne dass sich das Mapping ändert. {@code slim_cubes} ersetzt {@code cubes} bei Alex-Skins.
 * {@code "outer": true} markiert Quader der zweiten Skin-Ebene (abschaltbar in den Einstellungen).
 * Koordinaten: 1 = 1/16 Block, Y zeigt nach unten, der Boden liegt bei y = 0, das Gesicht
 * schaut nach -Z.
 */
public final class PlushieGeometry {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Identifier LOCATION = SkinTotemsClient.id("plushie/plushie.json");

	private static final Codec<int[]> UV_CODEC = Codec.INT.listOf()
		.comapFlatMap(list -> Util.fixedSize(list, 2).map(l -> new int[]{l.get(0), l.get(1)}), uv -> List.of(uv[0], uv[1]));

	record Cube(int[] uv, Vector3fc from, Vector3fc size, float inflate, boolean mirror, boolean outer) {
		static final Codec<Cube> CODEC = RecordCodecBuilder.create(i -> i.group(
				UV_CODEC.fieldOf("uv").forGetter(Cube::uv),
				ExtraCodecs.VECTOR3F.fieldOf("from").forGetter(Cube::from),
				ExtraCodecs.VECTOR3F.fieldOf("size").forGetter(Cube::size),
				Codec.FLOAT.optionalFieldOf("inflate", 0.0F).forGetter(Cube::inflate),
				Codec.BOOL.optionalFieldOf("mirror", false).forGetter(Cube::mirror),
				Codec.BOOL.optionalFieldOf("outer", false).forGetter(Cube::outer)
			).apply(i, Cube::new));
	}

	record Part(Vector3fc pivot, Vector3fc rotation, Vector3fc scale, List<Cube> cubes, Optional<List<Cube>> slimCubes, Map<String, Part> children) {
		static final Vector3fc ZERO = new Vector3f();
		static final Vector3fc ONE = new Vector3f(1.0F);
		static final Codec<Part> CODEC = Codec.recursive("skintotems:plushie_part", self -> RecordCodecBuilder.create(i -> i.group(
				ExtraCodecs.VECTOR3F.optionalFieldOf("pivot", ZERO).forGetter(Part::pivot),
				ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", ZERO).forGetter(Part::rotation),
				ExtraCodecs.VECTOR3F.optionalFieldOf("scale", ONE).forGetter(Part::scale),
				Cube.CODEC.listOf().optionalFieldOf("cubes", List.of()).forGetter(Part::cubes),
				Cube.CODEC.listOf().optionalFieldOf("slim_cubes").forGetter(Part::slimCubes),
				Codec.unboundedMap(Codec.STRING, self).optionalFieldOf("children", Map.of()).forGetter(Part::children)
			).apply(i, Part::new)));
	}

	record Definition(int textureWidth, int textureHeight, Map<String, Part> parts) {
		static final Codec<Definition> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("texture_width", 64).forGetter(Definition::textureWidth),
				Codec.INT.optionalFieldOf("texture_height", 64).forGetter(Definition::textureHeight),
				Codec.unboundedMap(Codec.STRING, Part.CODEC).fieldOf("parts").forGetter(Definition::parts)
			).apply(i, Definition::new));
	}

	/** Index: (slim ? 1 : 0) + (mit zweiter Ebene ? 2 : 0) */
	private static final @Nullable ModelPart[] MODELS = new ModelPart[4];

	private PlushieGeometry() {
	}

	/** @return das gebackene Modell oder {@code null}, wenn die Datei fehlt/kaputt ist */
	public static @Nullable ModelPart get(boolean slimArms, boolean outerLayer) {
		return MODELS[(slimArms ? 1 : 0) + (outerLayer ? 2 : 0)];
	}

	public static void reload(ResourceManager resources) {
		java.util.Arrays.fill(MODELS, null);
		Optional<Resource> resource = resources.getResource(LOCATION);
		if (resource.isEmpty()) {
			LOGGER.warn("[Skin Totems] Keine Plushie-Form unter {} gefunden", LOCATION);
			return;
		}
		try (Reader reader = resource.get().openAsReader()) {
			JsonElement json = JsonParser.parseReader(reader);
			Definition definition = Definition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
			for (int i = 0; i < MODELS.length; i++) {
				MODELS[i] = bake(definition, (i & 1) != 0, (i & 2) != 0);
			}
		} catch (Exception e) {
			LOGGER.warn("[Skin Totems] Plushie-Form {} konnte nicht geladen werden", LOCATION, e);
		}
	}

	private static ModelPart bake(Definition definition, boolean slimArms, boolean outerLayer) {
		MeshDefinition mesh = new MeshDefinition();
		addParts(mesh.getRoot(), definition.parts(), slimArms, outerLayer);
		return LayerDefinition.create(mesh, definition.textureWidth(), definition.textureHeight()).bakeRoot();
	}

	private static void addParts(PartDefinition parent, Map<String, Part> parts, boolean slimArms, boolean outerLayer) {
		parts.forEach((name, part) -> {
			CubeListBuilder builder = CubeListBuilder.create();
			List<Cube> cubes = slimArms ? part.slimCubes().orElse(part.cubes()) : part.cubes();
			for (Cube cube : cubes) {
				if (cube.outer() && !outerLayer) {
					continue;
				}
				builder.texOffs(cube.uv()[0], cube.uv()[1])
					.mirror(cube.mirror())
					.addBox(cube.from().x(), cube.from().y(), cube.from().z(), cube.size().x(), cube.size().y(), cube.size().z(), new CubeDeformation(cube.inflate()));
			}
			Vector3fc p = part.pivot();
			Vector3fc r = part.rotation();
			Vector3fc s = part.scale();
			PartPose pose = new PartPose(
				p.x(), p.y(), p.z(),
				r.x() * Mth.DEG_TO_RAD, r.y() * Mth.DEG_TO_RAD, r.z() * Mth.DEG_TO_RAD,
				s.x(), s.y(), s.z()
			);
			PartDefinition child = parent.addOrReplaceChild(name, builder, pose);
			addParts(child, part.children(), slimArms, outerLayer);
		});
	}
}
