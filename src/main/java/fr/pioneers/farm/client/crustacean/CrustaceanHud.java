package fr.pioneers.farm.client.crustacean;

import fr.pioneers.farm.client.fish.FishHud;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.crustacean.Crustacean;
import fr.pioneers.farm.crustacean.CrustaceanDef;
import fr.pioneers.farm.crustacean.PiCrustaceans;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** 1.4 : fiche au regard d'un crustace : espece, taille, milieux, aquatique ou de rivage, comment l'attraper. */
public final class CrustaceanHud {

    private CrustaceanHud() {
    }

    public static FarmCard card(Entity e, boolean sneaking) {
        if (!(e instanceof Crustacean c)) {
            return null;
        }
        CrustaceanDef d = c.def();
        var raw = PiCrustaceans.RAW.get(d.id());
        ItemStack icon = raw != null ? new ItemStack(raw.get()) : new ItemStack(PiCrustaceans.SEASHELL.get());
        FarmCard card = FarmCard.of(icon, Component.translatable("farm.pioneers.crustacean." + d.id()), d.colour());
        //1.6 : fiche synthetique (taille, milieu, ou il vit, sante)
        card.add(FarmCard.Line.pair(FarmCard.label("size"), Component.translatable("card.pioneers.cm", c.centimetres()), 0xD0D0D0));
        card.add(FarmCard.Line.pair(FarmCard.label("habitat"), FishHud.habitats(d::in), 0x6AB0D0));
        card.add(FarmCard.Line.pair(FarmCard.label("lives"), FarmCard.word(d.aquatic() ? "crustacean.aquatic" : "crustacean.shore"), 0xA0C8E0));
        card.add(FarmCard.Line.gauge(FarmCard.label("health"), c.getHealth() / c.getMaxHealth(), FarmCard.percent(c.getHealth() / c.getMaxHealth()), 0xE05050));
        return card;
    }
}
