package fr.pioneers.logistics.caravan.client;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.0.4-beta (lot 10) : modeles des caravanes, construits depuis les tables GENEREES par {@code tools/gc/caravan/caravan.py} : couches du
 * chameau de Bactriane et du dromadaire ({@link GeneratedCamels} : memes parties que {@code CamelModel}, animations vanilla) ; BAT par style
 * et niveau de remplissage ({@link GeneratedPack}, repere du corps du modele, rabats a part pour l'ouverture). Banc hors jeu :
 * {@code tools/farmtest/CaravanGeom.java}.
 */
public final class CaravanModels {

    private CaravanModels() {
    }

    public static final ModelLayerLocation BACTRIAN = new ModelLayerLocation(Pioneers.rl("bactrian_camel"), "main");
    public static final ModelLayerLocation DROMEDARY = new ModelLayerLocation(Pioneers.rl("dromedary"), "main");
    public static final ResourceLocation PACK_TEXTURE = Pioneers.rl("textures/entity/caravan/pack.png");

    /** Couche d'un chameau du mod (128 x 128). */
    public static LayerDefinition camel(GeneratedCamels.Part[] parts) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        defs.put("", mesh.getRoot());
        for (GeneratedCamels.Part p : parts) {
            CubeListBuilder b = CubeListBuilder.create();
            for (float[] c : p.boxes()) {
                b.texOffs((int) c[0], (int) c[1]).addBox(c[2], c[3], c[4], c[5], c[6], c[7], new CubeDeformation(c[8]));
            }
            defs.put(p.name(), defs.get(p.parent()).addOrReplaceChild(p.name(), b, PartPose.offset(p.x(), p.y(), p.z())));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Bat cuit : racine, rabats (enfants a tourner) et leur sens d'ouverture. */
    public record Pack(ModelPart root, List<ModelPart> flaps, float[] dirs) {
    }

    private static final Pack[][] PACKS = new Pack[GeneratedPack.STYLES][GeneratedPack.STAGES];

    /** Mesh du bat d'un style a un niveau de remplissage (boites visibles a ce niveau ; rabats en enfants " f<i> "). */
    public static MeshDefinition packMesh(int style, int stage) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        int i = 0;
        for (float[] b : GeneratedPack.BOXES) {
            i++;
            if ((int) b[0] != style || stage < (int) b[3] || stage > (int) b[4]) {
                continue;
            }
            CubeListBuilder c = CubeListBuilder.create().texOffs((int) b[11], (int) b[12]).addBox(b[5], b[6], b[7], b[8], b[9], b[10]);
            if (b[1] > 0) {
                root.addOrReplaceChild("f" + i, c, PartPose.offset(b[13], b[14], b[15]));
            } else {
                root.addOrReplaceChild("s" + i, c, PartPose.ZERO);
            }
        }
        return mesh;
    }

    public static Pack pack(int style, int stage) {
        int s = Math.max(0, Math.min(GeneratedPack.STYLES - 1, style)), st = Math.max(0, Math.min(GeneratedPack.STAGES - 1, stage));
        if (PACKS[s][st] == null) {
            ModelPart root = LayerDefinition.create(packMesh(s, st), GeneratedPack.TEX_W, GeneratedPack.TEX_H).bakeRoot();
            List<ModelPart> flaps = new ArrayList<>();
            List<Float> dirs = new ArrayList<>();
            int i = 0;
            for (float[] b : GeneratedPack.BOXES) {
                i++;
                if ((int) b[0] == s && b[1] > 0 && st >= (int) b[3] && st <= (int) b[4]) {
                    flaps.add(root.getChild("f" + i));
                    dirs.add(b[16]);
                }
            }
            float[] d = new float[dirs.size()];
            for (int k = 0; k < d.length; k++) {
                d[k] = dirs.get(k);
            }
            PACKS[s][st] = new Pack(root, flaps, d);
        }
        return PACKS[s][st];
    }
}
