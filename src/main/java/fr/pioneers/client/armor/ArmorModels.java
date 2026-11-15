package fr.pioneers.client.armor;

import fr.pioneers.Pioneers;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 0.19.15 : armures portees en 3D (demande de Guillaume : « ameliore largement les modeles 3D des armures une fois portees »).
 * Les geometries viennent de {@link GeneratedArmorModels} (genere par tools/gc/core/armor3d.py, avec les atlas
 * {@code textures/models/armor/<id>_atlas.png}) : une couche de modele par armure et par emplacement, cuite une fois en
 * {@link HumanoidModel} puis rendue a la place du modele vanilla (Forge recopie la pose et la visibilite des membres).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArmorModels {

    private static final Map<String, HumanoidModel<LivingEntity>> BAKED = new HashMap<>();

    private ArmorModels() {
    }

    static ModelLayerLocation layer(String id, int slot) {
        return new ModelLayerLocation(Pioneers.rl("armor/" + id), "slot" + slot);
    }

    static int slotIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            default -> 3;
        };
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (String id : GeneratedArmorModels.IDS) {
            for (int slot = 0; slot < 4; slot++) {
                final int s = slot;
                event.registerLayerDefinition(layer(id, slot), () -> GeneratedArmorModels.create(id, s));
            }
        }
    }

    /** Les modeles cuits sont refaits apres un rechargement des ressources. */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        BAKED.clear();
    }

    /** Modele porte de l'armure {@code id} a cet emplacement (cuit a la premiere demande). */
    public static HumanoidModel<LivingEntity> model(String id, EquipmentSlot slot) {
        int index = slotIndex(slot);
        return BAKED.computeIfAbsent(id + "/" + index,
                k -> new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(layer(id, index))));
    }

    /**
     * Extension client d'une piece d'armure : son modele 3D porte. L'id est lu a la demande : {@code initializeClient} est appele
     * PENDANT le constructeur de l'objet, avant que ses propres champs ne soient assignes.
     */
    public static void extensions(Supplier<String> id, Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                return model(id.get(), slot);
            }
        });
    }

    /** Texture (atlas) de l'armure {@code id}. */
    public static String texture(String id) {
        return Pioneers.MODID + ":textures/models/armor/" + id + "_atlas.png";
    }
}
