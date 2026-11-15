package fr.pioneers.client.wild;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.wild.GeneratedWild;
import fr.pioneers.wild.Hare;
import fr.pioneers.wild.WildAnimal;
import fr.pioneers.wild.WildRules;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 6) : RENDU du gibier, un par espece : silhouette du male ou de la femelle (le petit prend celle de la femelle, tete
 * x 1,35, echelle 0,55), robe du male, de la femelle ou du petit (marcassin raye, faon tachete...), lievre blanc en hiver ou dans la neige
 * ({@link Hare#winterCoat}). Les modeles sont prets a la construction et partages : pose initiale remise a chaque image
 * ({@link WildModel#restore}).
 */
public class WildRenderer<T extends WildAnimal> extends MobRenderer<T, WildModel<T>> {

    /**
     * Toutes les silhouettes : gibier du lot 6 ({@link GeneratedWildShapes}), puis lot 7 ({@link GeneratedWild7Shapes}), puis savane et
     * jungle du lot 8 ({@link GeneratedWild8Shapes}), puis mer et cotes du lot 9 ({@link GeneratedWild9Shapes}).
     */
    public static final GeneratedWildShapes.Shape[] SHAPES = all();
    /** Couches : une par silhouette de {@link #SHAPES} (meme ordre ; les 14 premieres = celles du lot 6). */
    public static final ModelLayerLocation[] LAYERS = new ModelLayerLocation[SHAPES.length];

    static {
        for (int i = 0; i < LAYERS.length; i++) {
            GeneratedWildShapes.Shape s = SHAPES[i];
            LAYERS[i] = new ModelLayerLocation(Pioneers.rl("wild_" + s.species() + "_" + (s.sex() == 0 ? "male" : "female")), "main");
        }
    }

    static GeneratedWildShapes.Shape[] all() {
        GeneratedWildShapes.Shape[] a = GeneratedWildShapes.SHAPES, b = GeneratedWild7Shapes.SHAPES, c = GeneratedWild8Shapes.SHAPES,
              d = GeneratedWild9Shapes.SHAPES;
        GeneratedWildShapes.Shape[] out = java.util.Arrays.copyOf(a, a.length + b.length + c.length + d.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        System.arraycopy(c, 0, out, a.length + b.length, c.length);
        System.arraycopy(d, 0, out, a.length + b.length + c.length, d.length);      //1.0.5-beta (lot 9) : mer et cotes
        return out;
    }

    public final int kind;
    final GeneratedWild.Species species;
    /** Robes du lot 7 (variantes) : nom -> texture. */
    final java.util.Map<String, ResourceLocation> coats = new java.util.HashMap<>();
    /** Modele du male, de la femelle (= male si l'espece n'a qu'une silhouette). */
    final WildModel<T>[] models;
    final ResourceLocation male, female, baby, winter;

    @SuppressWarnings("unchecked")
    public WildRenderer(EntityRendererProvider.Context ctx, int kind) {
        super(ctx, model(ctx, kind, 0), 0.5F);
        this.kind = kind;
        this.species = WildAnimal.speciesOf(kind);
        models = new WildModel[]{this.model, model(ctx, kind, 1)};
        String id = species.id();
        if (kind == WildRules.HARE) {
            male = female = baby = tex("hare");
            winter = tex("hare_winter");
        } else {
            male = tex(id + "_male");
            female = tex(id + "_female");
            baby = tex(id + "_baby");
            winter = null;
        }
    }

    static ResourceLocation tex(String name) {
        return Pioneers.rl("textures/entity/wild/" + name + ".png");
    }

    /** Index de la silhouette d'une espece et d'un sexe (repli : la premiere de l'espece). */
    public static int shapeIndex(int kind, int sex) {
        String id = WildAnimal.speciesOf(kind).id();
        int first = -1;
        for (int i = 0; i < SHAPES.length; i++) {
            GeneratedWildShapes.Shape s = SHAPES[i];
            if (s.species().equals(id)) {
                if (first < 0) {
                    first = i;
                }
                if (s.sex() == sex) {
                    return i;
                }
            }
        }
        return first;
    }

    static <T extends WildAnimal> WildModel<T> model(EntityRendererProvider.Context ctx, int kind, int sex) {
        int i = shapeIndex(kind, sex);
        return new WildModel<>(ctx.bakeLayer(LAYERS[i]), SHAPES[i]);
    }

    /** Silhouette d'une bete : femelle pour le petit. */
    public WildModel<T> modelOf(T e) {
        return models[e.isBaby() || e.sex() == WildRules.FEMALE ? 1 : 0];
    }

    @Override
    public void render(@NotNull T e, float yaw, float pt, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        this.model = modelOf(e);
        this.shadowRadius = species.width() * 0.55F * (e.isBaby() ? 0.55F : 1F) * (e.isInWater() ? 0F : 1F);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    protected void scale(@NotNull T e, @NotNull PoseStack pose, float pt) {
        float s = species.scale() * (e.isBaby() ? 0.55F : 1F);
        pose.scale(s, s, s);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull T e) {
        String coat = e.coat();
        if (coat != null) {
            return coats.computeIfAbsent(coat, WildRenderer::tex);                 //lot 7 : robe de la variante ou du petit
        }
        if (kind == WildRules.HARE) {
            return e instanceof Hare h && h.winterCoat() ? winter : male;
        }
        return e.isBaby() ? baby : e.sex() == WildRules.FEMALE ? female : male;
    }
}
