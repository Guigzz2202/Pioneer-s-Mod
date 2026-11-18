package fr.pioneers.equipment.cosmetic.client;

import fr.pioneers.Pioneers;
import fr.pioneers.equipment.cosmetic.CosmeticCatalog;
import fr.pioneers.equipment.cosmetic.CosmeticPiece;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.12.0 (lot E4) : modeles des cosmetiques portes. Geometries de {@link GeneratedCosmeticModels} (genere par tools/gc/cosmetics/gen.py,
 * avec les atlas {@code textures/models/cosmetic/<tenue>.png}) : une couche par tenue et par piece, cuite une fois en {@link HumanoidModel}.
 * Ajoute le calque {@link CosmeticLayer} aux deux rendus de joueur (bras larges et fins).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CosmeticModels {

    private static final Map<String, HumanoidModel<LivingEntity>> BAKED = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private CosmeticModels() {
    }

    static ModelLayerLocation layer(String outfit, int piece) {
        return new ModelLayerLocation(Pioneers.rl("cosmetic/" + outfit), "piece" + piece);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (String outfit : CosmeticCatalog.OUTFITS) {
            for (CosmeticPiece piece : CosmeticPiece.ALL) {
                int p = piece.ordinal();
                event.registerLayerDefinition(layer(outfit, p), () -> GeneratedCosmeticModels.create(outfit, p));
            }
        }
    }

    /** Les modeles cuits sont refaits apres un rechargement des ressources ; calque des cosmetiques sur les joueurs. */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        BAKED.clear();
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new CosmeticLayer(renderer));
            }
        }
    }

    /** Modele porte de la piece {@code piece} de la tenue {@code outfit} (cuit a la premiere demande). */
    static HumanoidModel<LivingEntity> model(String outfit, CosmeticPiece piece) {
        int p = piece.ordinal();
        return BAKED.computeIfAbsent(outfit + "/" + p, k -> new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(layer(outfit, p))));
    }

    static ResourceLocation texture(String outfit) {
        return TEXTURES.computeIfAbsent(outfit, o -> Pioneers.rl("textures/models/cosmetic/" + o + ".png"));
    }

    static boolean translucent(String outfit, CosmeticPiece piece) {
        return GeneratedCosmeticModels.TRANSLUCENT.contains(outfit + "/" + piece.ordinal());
    }
}
