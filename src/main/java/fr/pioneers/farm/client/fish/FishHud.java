package fr.pioneers.farm.client.fish;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.fish.FishDef;
import fr.pioneers.farm.fish.FishItems;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.PiFish;
import java.util.function.IntPredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Fiche au regard d'un poisson du mod : espece, taille, milieu, force et comportement, propriete (1.6 : une ligne chacune). */
public final class FishHud {

    private FishHud() {
    }

    public static FarmCard card(Entity e, boolean sneaking) {
        if (!(e instanceof PiFish fish)) {
            return null;
        }
        FishDef d = fish.def();
        ItemStack icon = new ItemStack(FishItems.item(d));
        FarmCard c = FarmCard.of(icon, Component.translatable("farm.pioneers.fish." + d.id()), d.colour());
        //1.6 : fiche synthetique (taille, milieu, combat, particularite)
        c.add(FarmCard.Line.pair(FarmCard.label("size"), Component.translatable("card.pioneers.cm", fish.centimetres()), 0xD0D0D0));
        c.add(FarmCard.Line.pair(FarmCard.label("habitat"), habitats(d::in), 0x6AB0D0));
        c.add(FarmCard.Line.gauge(FarmCard.label("fight"), d.strength() / 10F,
              Component.translatable("farm.pioneers.fish.behaviour." + GeneratedFish.BEHAVIOURS[d.behaviour()]), 0xE08040));
        if (!d.prop().isEmpty()) {
            c.add(FarmCard.Line.pair(FarmCard.label("special"), Component.translatable("farm.pioneers.fish.prop." + d.prop()), d.legendary() ? 0xF0C040 : 0xB080F0));
        }
        return c;
    }

    /** 1.6 : premier milieu, puis « +n » pour les autres (la fiche reste sur une ligne). */
    public static Component habitats(IntPredicate in) {
        Component first = null;
        int more = 0;
        for (int i = 0; i < GeneratedFish.HABITATS.length; i++) {
            if (in.test(i)) {
                if (first == null) {
                    first = Component.translatable("farm.pioneers.fish.habitat." + GeneratedFish.HABITATS[i]);
                } else {
                    more++;
                }
            }
        }
        if (first == null) {
            return FarmCard.word("none");
        }
        return more == 0 ? first : first.copy().append(Component.translatable("card.pioneers.more", more));
    }
}
