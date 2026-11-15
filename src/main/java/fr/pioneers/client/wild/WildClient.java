package fr.pioneers.client.wild;

import fr.pioneers.Pioneers;
import fr.pioneers.client.farm.FarmHud;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.wild.Deer;
import fr.pioneers.wild.Hare;
import fr.pioneers.wild.PiWild;
import fr.pioneers.wild.PiWild7;
import fr.pioneers.wild.PiWild8;
import fr.pioneers.wild.Monkey;
import fr.pioneers.wild.Rat;
import fr.pioneers.wild.Wild7Rules;
import fr.pioneers.wild.Wild8Rules;
import fr.pioneers.wild.WildAnimal;
import fr.pioneers.wild.WildRules;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 1.0.5-beta (lot 6) : client du gibier : couches des silhouettes ({@link WildModel#layer}), rendus ({@link WildRenderer}, un par espece),
 * fiche au regard (espece, male / femelle / petit, description, bois du cerf, pelage du lievre).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WildClient {

    private WildClient() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (int i = 0; i < WildRenderer.SHAPES.length; i++) {
            GeneratedWildShapes.Shape s = WildRenderer.SHAPES[i];
            event.registerLayerDefinition(WildRenderer.LAYERS[i], () -> WildModel.layer(s));
        }
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PiWild.BOAR.get(), ctx -> new WildRenderer<>(ctx, WildRules.BOAR));
        event.registerEntityRenderer(PiWild.DEER.get(), ctx -> new WildRenderer<>(ctx, WildRules.DEER));
        event.registerEntityRenderer(PiWild.TURKEY.get(), ctx -> new WildRenderer<>(ctx, WildRules.TURKEY));
        event.registerEntityRenderer(PiWild.HARE.get(), ctx -> new WildRenderer<>(ctx, WildRules.HARE));
        event.registerEntityRenderer(PiWild.BISON.get(), ctx -> new WildRenderer<>(ctx, WildRules.BISON));
        //1.0.5-beta (lot 7) : predateurs et petite faune
        event.registerEntityRenderer(PiWild7.GRAY_WOLF.get(), ctx -> new WildRenderer<>(ctx, Wild7Rules.WOLF));
        event.registerEntityRenderer(PiWild7.BEAR.get(), ctx -> new WildRenderer<>(ctx, Wild7Rules.BEAR));
        event.registerEntityRenderer(PiWild7.WOODPECKER.get(), ctx -> new WildRenderer<>(ctx, Wild7Rules.WOODPECKER));
        event.registerEntityRenderer(PiWild7.RAT.get(), ctx -> new WildRenderer<>(ctx, Wild7Rules.RAT));
        event.registerEntityRenderer(PiWild7.PIGEON.get(), ctx -> new WildRenderer<>(ctx, Wild7Rules.PIGEON));
        event.registerEntityRenderer(PiWild7.SNAKE.get(), ctx -> new WildRenderer<>(ctx, Wild7Rules.SNAKE));
        //1.0.5-beta (lot 8) : savane et jungle, et la peau de banane du singe
        event.registerEntityRenderer(PiWild8.GIRAFFE.get(), ctx -> new WildRenderer<>(ctx, Wild8Rules.GIRAFFE));
        event.registerEntityRenderer(PiWild8.LION.get(), ctx -> new WildRenderer<>(ctx, Wild8Rules.LION));
        event.registerEntityRenderer(PiWild8.RHINO.get(), ctx -> new WildRenderer<>(ctx, Wild8Rules.RHINO));
        event.registerEntityRenderer(PiWild8.ELEPHANT.get(), ctx -> new WildRenderer<>(ctx, Wild8Rules.ELEPHANT));
        event.registerEntityRenderer(PiWild8.MONKEY.get(), ctx -> new WildRenderer<>(ctx, Wild8Rules.MONKEY));
        event.registerEntityRenderer(PiWild8.TIGER.get(), ctx -> new WildRenderer<>(ctx, Wild8Rules.TIGER));
        event.registerEntityRenderer(PiWild8.BANANA_PEEL.get(), BananaPeelRenderer::new);
        //1.0.5-beta (lot 9) : mer et cotes
        for (int k = 0; k < fr.pioneers.wild.PiWild9.TYPES.size(); k++) {
            int kind = fr.pioneers.wild.PiWild9.KINDS.get(k);
            event.registerEntityRenderer(fr.pioneers.wild.PiWild9.TYPES.get(k).get(), ctx -> new WildRenderer<>(ctx, kind));
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> FarmHud.ENTITY_PROVIDERS.add(WildClient::card));
    }

    /** Fiche au regard d'une bete du gibier. */
    public static FarmCard card(Entity e, boolean sneaking) {
        if (!(e instanceof WildAnimal w)) {
            return null;
        }
        String id = w.species().id();
        SpawnEggItem egg = SpawnEggItem.byId(e.getType());
        String who = w.isBaby() ? "baby" : w.sex() == WildRules.FEMALE ? "female" : "male";
        FarmCard c = FarmCard.of(egg == null ? ItemStack.EMPTY : new ItemStack(egg), Component.translatable("wild.pioneers.card.sex",
              Component.translatable("entity.pioneers." + id), Component.translatable("wild.pioneers." + id + "." + who)), 0x9C7A50);
        c.add(FarmCard.Line.text(Component.translatable("wild.pioneers." + id + ".desc"), 0xD0D0D0));
        if (e instanceof Deer d && d.isMale() && !d.isBaby()) {
            float g = d.antlers();
            c.add(FarmCard.Line.text(g <= 0.04F ? Component.translatable("wild.pioneers.card.antlers_shed")
                                                : Component.translatable("wild.pioneers.card.antlers", Math.round(g * 100)), 0xE0D2B0));
        }
        if (e instanceof Hare h) {
            c.add(FarmCard.Line.text(Component.translatable(h.winterCoat() ? "wild.pioneers.card.winter_coat" : "wild.pioneers.card.summer_coat"),
                  0xE0E0E0));
        }
        if (w.kind >= fr.pioneers.wild.Wild9Rules.BLUE_WHALE) {
            card9(c, w);
            return c;
        }
        if (w.kind >= Wild8Rules.GIRAFFE) {
            card8(c, w);
            return c;
        }
        if (w.kind >= Wild7Rules.WOLF) {
            card7(c, w);
            return c;
        }
        c.add(FarmCard.Line.hint(Component.translatable("wild.pioneers.card.hint")));
        return c;
    }

    /** Lot 7 : robe (variante), butin du rat, rappel du danger. */
    static void card7(FarmCard c, WildAnimal w) {
        String coat = w.coat();
        if (coat != null && !w.isBaby()) {
            c.add(FarmCard.Line.text(Component.translatable("wild.pioneers.card.coat", Component.translatable("wild.pioneers.coat." + coat)), 0xE0D2B0));
        }
        if (w instanceof Rat r && !r.loot().isEmpty()) {
            c.add(FarmCard.Line.text(Component.translatable("wild.pioneers.card.carrying", r.loot().getHoverName()), 0xF0B040));
        }
        String hint = switch (w.kind) {
            case Wild7Rules.WOLF -> "predator";
            case Wild7Rules.BEAR -> "bear";
            case Wild7Rules.RAT -> "vermin";
            case Wild7Rules.SNAKE -> "snake";
            default -> "bird";
        };
        c.add(FarmCard.Line.hint(Component.translatable("wild.pioneers.card." + hint)));
    }

    /** Lot 9 : robe (variante), souffle des cetaces, poisson au bec, rappel du danger. */
    static void card9(FarmCard c, WildAnimal w) {
        String coat = w.coat();
        if (coat != null && !w.isBaby()) {
            c.add(FarmCard.Line.text(Component.translatable("wild.pioneers.card.coat", Component.translatable("wild.pioneers.coat." + coat)), 0xE0D2B0));
        }
        int k = w.kind;
        if (w instanceof fr.pioneers.wild.MarineAnimal m && !m.canBreatheUnderwater() && m.isInWater()) {
            c.add(FarmCard.Line.text(Component.translatable("wild.pioneers.card.breath", Math.round(100F * m.getAirSupply() / m.getMaxAirSupply())),
                  0xA0D0F0));
        }
        if (w instanceof fr.pioneers.wild.Penguin p && p.hasFish()) {
            c.add(FarmCard.Line.text(Component.translatable("wild.pioneers.card.fish"), 0xC0D8F0));
        }
        String hint;
        if (k == fr.pioneers.wild.Wild9Rules.ORCA) {
            hint = "orca";
        } else if (fr.pioneers.wild.Wild9Rules.cetacean(k)) {
            hint = "whale";
        } else if (k == fr.pioneers.wild.Wild9Rules.GREAT_WHITE || k == fr.pioneers.wild.Wild9Rules.TIGER_SHARK) {
            hint = "shark";
        } else if (k == fr.pioneers.wild.Wild9Rules.HAMMERHEAD || k == fr.pioneers.wild.Wild9Rules.REEF_SHARK) {
            hint = "shark_blood";
        } else if (fr.pioneers.wild.Wild9Rules.pinniped(k)) {
            hint = "pinniped";
        } else if (k == fr.pioneers.wild.Wild9Rules.SEAGULL) {
            hint = "seagull";
        } else if (fr.pioneers.wild.Wild9Rules.auk(k)) {
            hint = "diver";
        } else {
            hint = "gentle";
        }
        c.add(FarmCard.Line.hint(Component.translatable("wild.pioneers.card." + hint)));
    }

    /** Lot 8 : robe (variante), banane du singe, rappel du danger. */
    static void card8(FarmCard c, WildAnimal w) {
        String coat = w.coat();
        if (coat != null && !w.isBaby()) {
            c.add(FarmCard.Line.text(Component.translatable("wild.pioneers.card.coat", Component.translatable("wild.pioneers.coat." + coat)), 0xE0D2B0));
        }
        if (w instanceof Monkey m && m.hasBanana()) {
            c.add(FarmCard.Line.text(Component.translatable("wild.pioneers.card.banana"), 0xF0D040));
        }
        String hint = switch (w.kind) {
            case Wild8Rules.GIRAFFE -> "giraffe";
            case Wild8Rules.LION -> "lion";
            case Wild8Rules.RHINO -> "rhino";
            case Wild8Rules.ELEPHANT -> "elephant";
            case Wild8Rules.MONKEY -> "monkey";
            default -> "tiger";
        };
        c.add(FarmCard.Line.hint(Component.translatable("wild.pioneers.card." + hint)));
    }
}
