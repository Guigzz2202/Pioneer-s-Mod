package fr.pioneers.client.farm.herd;

import fr.pioneers.Pioneers;
import fr.pioneers.client.farm.FarmHud;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.herd.GeneratedHerd;
import fr.pioneers.farm.herd.HerdData;
import fr.pioneers.farm.herd.HerdRules;
import fr.pioneers.farm.herd.PiHerd;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 1.0.4-beta (lot 9) : client de l'elevage des lapins et des chevaux : couches des modeles ({@link HerdModels}), rendus du lapin et du
 * cheval (remplacent ceux du vanilla), fiche au regard ({@link #card}, par {@link FarmHud#ENTITY_PROVIDERS}) : race et role ; accroupi :
 * les stats (lapin : croissance, portee, viande, peau, laine ; cheval / ane / mulet : vitesse, saut, sante, TRAIT et BAT en kg).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HerdClient {

    private HerdClient() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HerdModels.RABBIT, () -> HerdModels.rabbit(false, 0));
        event.registerLayerDefinition(HerdModels.RABBIT_LOP, () -> HerdModels.rabbit(true, 0));
        event.registerLayerDefinition(HerdModels.RABBIT_FUR, () -> HerdModels.rabbit(false, HerdModels.FUR));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.RABBIT, HerdRabbitRenderer::new);
        event.registerEntityRenderer(EntityType.HORSE, HerdHorseRenderer::new);
        //1.0.4-beta (lot 28) : une entite par race (lapins, chevaux, anes, lamas) : memes rendus, robe et echelle de la race
        for (int i = 0; i < fr.pioneers.farm.herd.HerdBreeds.TYPES.size(); i++) {
            fr.pioneers.farm.herd.GeneratedHerd.HerdType t = fr.pioneers.farm.herd.GeneratedHerd.TYPES[i];
            EntityType<?> type = fr.pioneers.farm.herd.HerdBreeds.TYPES.get(i).get();
            register(event, type, t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void register(EntityRenderersEvent.RegisterRenderers event, EntityType<?> type, fr.pioneers.farm.herd.GeneratedHerd.HerdType t) {
        switch (t.species()) {
            case fr.pioneers.farm.herd.HerdBreeds.RABBIT -> event.registerEntityRenderer(
                  (EntityType<net.minecraft.world.entity.animal.Rabbit>) type, HerdRabbitRenderer::new);
            case fr.pioneers.farm.herd.HerdBreeds.HORSE -> event.registerEntityRenderer(
                  (EntityType<net.minecraft.world.entity.animal.horse.Horse>) type, HerdHorseRenderer::new);
            case fr.pioneers.farm.herd.HerdBreeds.DONKEY -> {
                fr.pioneers.farm.herd.GeneratedHerd.HorseBreed b = fr.pioneers.farm.herd.GeneratedHerd.HORSES[t.breed()];
                String tex = "donkey".equals(b.id()) ? "donkey" : b.id();
                event.registerEntityRenderer((EntityType<net.minecraft.world.entity.animal.horse.Donkey>) type,
                      ctx -> new fr.pioneers.client.caravan.HerdDonkeyRenderer<>(ctx, net.minecraft.client.model.geom.ModelLayers.DONKEY,
                            0.87F * b.scale(), tex));
            }
            default -> {
                fr.pioneers.farm.herd.GeneratedHerd.HorseBreed b = fr.pioneers.farm.herd.GeneratedHerd.HORSES[t.breed()];
                event.registerEntityRenderer((EntityType<net.minecraft.world.entity.animal.horse.Llama>) type,
                      ctx -> new HerdLlamaRenderer(ctx, b.id(), b.scale()));
            }
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> FarmHud.ENTITY_PROVIDERS.add(HerdClient::card));
    }

    /** Fiche au regard d'un lapin ou d'un cheval du mod (null : pas concerne, ou race pas encore recue). */
    public static FarmCard card(Entity e, boolean sneaking) {
        HerdData d = PiHerd.get(e);
        if (d == null || !d.init || d.breed < 0 || !(e instanceof LivingEntity le)) {
            return null;
        }
        boolean young = le.isBaby();
        if (e instanceof Rabbit) {
            GeneratedHerd.RabbitBreed b = d.rabbit();
            FarmCard c = FarmCard.of(new ItemStack(Items.RABBIT_SPAWN_EGG), Component.translatable("farm.pioneers.herd.rabbit." + b.id()), 0xC89A68);
            c.add(FarmCard.Line.text(Component.translatable("farm.pioneers.herd.rabbit." + b.id() + ".desc"), 0xD0D0D0));
            if (b.wool() > 0 && !young) {
                c.add(FarmCard.Line.pair(Component.translatable("farm.pioneers.herd.wool"), d.woolTimer > 0
                      ? FarmCard.duration((long) d.woolTimer) : Component.translatable("farm.pioneers.herd.wool_ready"), d.woolTimer > 0 ? FarmCard.DIM
                      : FarmCard.OK));
            }
            if (sneaking) {
                c.add(stat("growth", pct(d.growth)));
                c.add(FarmCard.Line.pair(Component.translatable("farm.pioneers.herd.litter"),
                      Component.translatable("farm.pioneers.herd.litter_value", String.format(Locale.ROOT, "%.1f", d.litter)), FarmCard.INFO));
                c.add(stat("meat", pct(d.meat)));
                c.add(stat("pelt", pct(d.pelt)));
                if (d.wool > 0) {
                    c.add(stat("wool", pct(d.wool)));
                }
            } else {
                c.add(FarmCard.Line.hint(Component.translatable("farm.pioneers.herd.hint")));
            }
            return c;
        }
        if (!(e instanceof AbstractHorse horse)) {
            return null;
        }
        GeneratedHerd.HorseBreed b = d.horse();
        ItemStack egg = new ItemStack(b.kind() == 1 ? Items.DONKEY_SPAWN_EGG : b.kind() == 2 ? Items.MULE_SPAWN_EGG : b.kind() == 3 ? Items.CAMEL_SPAWN_EGG
              : b.kind() == 4 ? fr.pioneers.caravan.PiCaravan.DROMEDARY_EGG.get() : Items.HORSE_SPAWN_EGG);
        FarmCard c = FarmCard.of(egg, Component.translatable("farm.pioneers.herd.horse." + b.id()), 0xA87850);
        c.add(FarmCard.Line.text(Component.translatable("farm.pioneers.herd.horse." + b.id() + ".desc"), 0xD0D0D0));
        //Trait et bat toujours visibles (les caravanes du lot 10 les liront)
        c.add(FarmCard.Line.pair(Component.translatable("farm.pioneers.herd.draft"), Component.translatable("farm.pioneers.herd.kg",
              young ? d.draft / 2 : d.draft), 0xE0B060));
        c.add(FarmCard.Line.pair(Component.translatable("farm.pioneers.herd.pack"), Component.translatable("farm.pioneers.herd.kg",
              young ? d.pack / 2 : d.pack), 0xE0B060));
        if (sneaking) {
            double speed = horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * GeneratedHerd.BLOCKS_PER_SPEED;
            double jump = HerdRules.jumpHeight(horse.getAttributeBaseValue(Attributes.JUMP_STRENGTH));
            c.add(FarmCard.Line.pair(Component.translatable("farm.pioneers.herd.speed"), Component.translatable("farm.pioneers.herd.speed_value",
                  String.format(Locale.ROOT, "%.1f", speed)), FarmCard.INFO));
            c.add(FarmCard.Line.pair(Component.translatable("farm.pioneers.herd.jump"), Component.translatable("farm.pioneers.herd.jump_value",
                  String.format(Locale.ROOT, "%.1f", jump)), FarmCard.INFO));
            c.add(FarmCard.Line.pair(Component.translatable("farm.pioneers.herd.health"), Component.translatable("farm.pioneers.herd.health_value",
                  String.format(Locale.ROOT, "%.1f", horse.getMaxHealth() / 2F)), FarmCard.INFO));
        } else {
            c.add(FarmCard.Line.hint(Component.translatable("farm.pioneers.herd.hint")));
        }
        return c;
    }

    private static FarmCard.Line stat(String key, Component v) {
        return FarmCard.Line.pair(Component.translatable("farm.pioneers.herd." + key), v, FarmCard.INFO);
    }

    private static Component pct(float v) {
        return Component.literal(Math.round(v * 100) + " %");
    }
}
