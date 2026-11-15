package fr.pioneers.client.farm;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.BreedDef;
import fr.pioneers.farm.animal.PiAnimals;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Fiche au regard d'un animal d'elevage : sexe et statut ; domestique : faim, soif et production en jauges ; sauvage : la race.
 * 1.6 (fiches synthetiques) : accroupi = la race et le rendement ; plus de phrase d'aide.
 */
final class AnimalHud {

    private AnimalHud() {
    }

    static FarmCard card(Entity e, boolean sneaking) {
        AnimalSpecies sp = AnimalSpecies.of(e);
        AnimalData d = sp == null ? null : PiAnimals.get(e);
        if (d == null || !d.init) {
            return null;
        }
        int bi = AnimalSpecies.breedIndex(e, d);
        BreedDef b = sp.breed(bi);
        boolean baby = e instanceof AgeableMob a && a.isBaby();
        String who = baby ? "young" : d.male ? "male" : "female";
        //1.0.3-beta lot 8 : oeuf de la race (une entite par race)
        FarmCard c = FarmCard.of(sp.egg(bi), Component.translatable("farm.pioneers.breed." + sp.id() + "." + b.id()), d.male ? 0x5AA0E6 : 0xE67AA0);
        Component sex = Component.translatable("farm.pioneers.animal." + sp.id() + "." + who);
        c.add(FarmCard.Line.text(Component.translatable(d.male ? "farm.pioneers.animal.male" : "farm.pioneers.animal.female", sex)
              .append(" · ").append(Component.translatable(d.domestic ? "farm.pioneers.animal.domestic" : "farm.pioneers.animal.wild")), 0xD0D0D0));
        //1.0.2-beta : rendement de AnimalData (plancher de 10 % a jauges vides : l'animal ne meurt plus, il produit tres peu)
        int yield = Math.round(100 * d.production());
        if (sneaking || !d.domestic) {
            c.add(FarmCard.Line.text(Component.translatable("farm.pioneers.breed_desc." + sp.id() + "." + b.id()), 0xB0B0B0));
            if (sneaking) {
                //1.0.3-beta lot 8 : taille de la race (male 8 % plus grand, petit : moitie)
                float size = b.size() * (d.male && !baby ? 1.08F : 1F) * (baby ? 0.5F : 1F);
                c.add(FarmCard.Line.pair(Component.translatable("farm.pioneers.animal.size"), Component.translatable(sp == AnimalSpecies.CHICKEN
                      ? "farm.pioneers.animal.size_value_bird" : "farm.pioneers.animal.size_value", String.format(java.util.Locale.ROOT, "%.2f", size)),
                      0xB0B0B0));
                c.add(FarmCard.Line.pair(FarmCard.label("yield"), FarmCard.percent(yield / 100F), yield >= 100 ? 0x7CDC6A : yield >= 50 ? FarmCard.WARN : FarmCard.BAD));
            }
            return c;
        }
        c.add(FarmCard.Line.gauge(Component.translatable("farm.pioneers.animal.hunger"), d.hunger / 100F,
              d.starving > 0 ? FarmCard.word("starving").withStyle(s -> s.withColor(FarmCard.BAD)) : FarmCard.percent(d.hunger / 100F), colour(d.hunger)));
        c.add(FarmCard.Line.gauge(Component.translatable("farm.pioneers.animal.thirst"), d.thirst / 100F, FarmCard.percent(d.thirst / 100F),
              d.thirst < 30 ? FarmCard.BAD : 0x5AA0E6));
        if (!baby) {
            String key = null;
            if (sp.milked && !d.male) {
                key = "milk";
            } else if (sp == AnimalSpecies.CHICKEN && !d.male) {
                key = "egg";
            } else if (sp == AnimalSpecies.SHEEP || sp == AnimalSpecies.GOAT && b.wool() > 1) {
                key = "wool";
            }
            if (key != null) {
                boolean ready = d.produce >= 0.999F;
                c.add(FarmCard.Line.gauge(FarmCard.label(key), d.produce, ready ? FarmCard.word("done").withStyle(s -> s.withColor(0xF0C040))
                      : FarmCard.percent(d.produce), ready ? 0xF0C040 : 0xE8E8E8));
            }
        }
        return c;
    }

    private static int colour(float v) {
        return v >= 60 ? 0x7CC860 : v >= 30 ? 0xF0B040 : 0xF05050;
    }
}
