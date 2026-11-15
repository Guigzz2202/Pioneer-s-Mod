package fr.pioneers.logistics.caravan.client;

import fr.pioneers.Pioneers;
import fr.pioneers.logistics.caravan.CaravanData;
import fr.pioneers.logistics.caravan.PiCaravan;
import fr.pioneers.farm.client.FarmHud;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.herd.HerdData;
import fr.pioneers.farm.herd.PiHerd;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 1.0.4-beta (lot 10) : client des caravanes : couches des chameaux, rendus (chameau de Bactriane, dromadaire, ane et mulet du mod ; le bat
 * s'ajoute aussi au rendu des chevaux du lot 9), ecrans (sacoches, feuille de route), FICHE AU REGARD d'une bete de caravane ou batee
 * (role, etat, etape et distance, bat, proprietaire ; accroupi : la fiche de race du lot 9).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CaravanClient {

    private CaravanClient() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CaravanModels.BACTRIAN, () -> CaravanModels.camel(GeneratedCamels.BACTRIAN));
        event.registerLayerDefinition(CaravanModels.DROMEDARY, () -> CaravanModels.camel(GeneratedCamels.DROMEDARY));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.CAMEL, ctx -> new HerdCamelRenderer(ctx, CaravanModels.BACTRIAN, "bactrian"));
        event.registerEntityRenderer(PiCaravan.DROMEDARY.get(), ctx -> new HerdCamelRenderer(ctx, CaravanModels.DROMEDARY, "dromedary"));
        event.registerEntityRenderer(EntityType.DONKEY, ctx -> new HerdDonkeyRenderer<>(ctx, ModelLayers.DONKEY, 0.87F, "donkey"));
        event.registerEntityRenderer(EntityType.MULE, ctx -> new HerdDonkeyRenderer<>(ctx, ModelLayers.MULE, 0.92F, "mule"));
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(PiCaravan.PACK_MENU.get(), PackScreen::new);
            MenuScreens.register(PiCaravan.ROUTE_MENU.get(), RouteScreen::new);
            FarmHud.ENTITY_PROVIDERS.add(0, CaravanClient::card);
        });
    }

    /** Fiche au regard d'une bete de caravane ou batee (null : pas concernee, ou accroupi : la fiche de race). */
    public static FarmCard card(Entity e, boolean sneaking) {
        CaravanData d = PiCaravan.carrier(e) ? PiCaravan.get(e) : null;
        if (sneaking || d == null || !d.pack && d.role == CaravanData.NONE) {
            return null;
        }
        HerdData h = PiHerd.get(e);
        Component title = h != null && h.init && h.breed >= 0 && h.kind == HerdData.HORSE
                          ? Component.translatable("farm.pioneers.herd.horse." + h.horse().id()) : e.getName();
        FarmCard c = FarmCard.of(new ItemStack(PiCaravan.PACK_SADDLE.get()), title, 0xC8964A);
        if (d.role == CaravanData.HEAD) {
            c.add(FarmCard.Line.text(Component.translatable("card.pioneers.caravan.head"), 0xE0B060));
        } else if (d.role == CaravanData.FOLLOWER) {
            c.add(FarmCard.Line.text(Component.translatable("card.pioneers.caravan.follower", d.index), 0xE0B060));
        }
        if (d.role != CaravanData.NONE) {
            String st = d.state == CaravanData.STOPPED ? "stopped" : d.state == CaravanData.STOP ? "stop" : d.state == CaravanData.TRAVEL
                        ? (d.clientWaiting ? "wait" : "travel") : "idle";
            Component state = Component.translatable("card.pioneers.caravan.state." + st);
            if (d.clientHungry) {
                state = state.copy().append(" \u00B7 ").append(Component.translatable("card.pioneers.caravan.hungry"));
            }
            c.add(FarmCard.Line.pair(Component.translatable("card.pioneers.caravan.state"), state,
                  d.state == CaravanData.STOPPED || d.clientHungry ? FarmCard.BAD : d.state == CaravanData.STOP ? FarmCard.WARN : FarmCard.OK));
            if (!d.clientNext.isEmpty() && d.state != CaravanData.IDLE) {
                c.add(FarmCard.Line.pair(Component.translatable("card.pioneers.caravan.next"),
                      Component.translatable("card.pioneers.caravan.next_value", d.clientNext, d.clientNextDist), FarmCard.INFO));
            }
        }
        if (d.pack) {
            float r = d.slots <= 0 ? 0 : d.clientUsed / (float) d.slots;
            c.add(FarmCard.Line.gauge(Component.translatable("card.pioneers.caravan.pack"), r,
                  Component.translatable("card.pioneers.caravan.pack_value", d.clientUsed, d.slots), 0xC8964A));
        }
        if (d.role != CaravanData.NONE && !d.ownerName.isEmpty()) {
            c.add(FarmCard.Line.pair(Component.translatable("card.pioneers.caravan.owner"), Component.literal(d.ownerName), FarmCard.DIM));
        }
        if (d.pack) {
            c.add(FarmCard.Line.hint(Component.translatable("card.pioneers.caravan.hint")));
        }
        return c;
    }
}
