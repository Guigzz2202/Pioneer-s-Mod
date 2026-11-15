package fr.pioneers.farm.animal;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fr.pioneers.farm.animal.breed.BreedAnimal;
import fr.pioneers.farm.animal.breed.PiBreeds;
import org.jetbrains.annotations.Nullable;

/** Especes d'elevage : animaux vanilla etendus (races, sexe, besoins). Meme ordre que {@code animals.py SPECIES}. */
public enum AnimalSpecies {
    COW(Items.COW_SPAWN_EGG, true, true),
    SHEEP(Items.SHEEP_SPAWN_EGG, true, false),
    PIG(Items.PIG_SPAWN_EGG, false, false),
    CHICKEN(Items.CHICKEN_SPAWN_EGG, false, false),
    GOAT(Items.GOAT_SPAWN_EGG, true, true);

    public final Item egg;
    /** Broute l'herbe ; donne du lait (femelles). */
    public final boolean grazes, milked;
    private Map<Item, Integer> items;
    private Map<TagKey<Item>, Integer> tags;

    AnimalSpecies(Item egg, boolean grazes, boolean milked) {
        this.egg = egg;
        this.grazes = grazes;
        this.milked = milked;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public BreedDef[] breeds() {
        return GeneratedAnimals.BREEDS[ordinal()];
    }

    /** Race d'une entite de l'espece : celle de son type (entite de race), sinon celle de ses donnees. */
    public static int breedIndex(Entity entity, @Nullable AnimalData d) {
        if (entity instanceof BreedAnimal b) {
            return b.breed();
        }
        return d == null ? 0 : d.breed;
    }

    /** Oeuf d'apparition d'une race (1.0.3-beta lot 8). */
    public ItemStack egg(int breed) {
        BreedDef[] b = breeds();
        return new ItemStack(PiBreeds.EGGS.get(ordinal() * b.length + Math.max(0, Math.min(b.length - 1, breed))).get());
    }

    public BreedDef breed(int i) {
        BreedDef[] b = breeds();
        return b[Math.max(0, Math.min(b.length - 1, i))];
    }

    /**
     * Espece d'une entite : une entite de race (1.0.3-beta lot 8, {@link BreedAnimal}) ou exactement le type vanilla (la mooshroom n'en est
     * pas une ; une vache vanilla est convertie en sa race des son entree dans le monde, {@link fr.pioneers.farm.animal.breed.BreedConversion}).
     */
    @Nullable
    public static AnimalSpecies of(Entity entity) {
        if (entity instanceof BreedAnimal b) {
            return b.species();
        }
        return vanilla(entity.getType());
    }

    /** Espece d'un type vanilla d'elevage (vache, mouton, cochon, poule, chevre), sinon null. */
    @Nullable
    public static AnimalSpecies vanilla(EntityType<?> t) {
        if (t == EntityType.COW) {
            return COW;
        } else if (t == EntityType.SHEEP) {
            return SHEEP;
        } else if (t == EntityType.PIG) {
            return PIG;
        } else if (t == EntityType.CHICKEN) {
            return CHICKEN;
        } else if (t == EntityType.GOAT) {
            return GOAT;
        }
        return null;
    }

    /** Valeur nourrissante d'un aliment pour l'espece (0 = n'en mange pas). */
    public int feedValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (items == null) {
            Map<Item, Integer> it = new HashMap<>();
            Map<TagKey<Item>, Integer> tg = new HashMap<>();
            for (String[] f : GeneratedAnimals.FEEDS[ordinal()]) {
                int v = Integer.parseInt(f[1]);
                if (f[0].startsWith("#")) {
                    tg.put(TagKey.create(Registries.ITEM, ResourceLocation.parse(f[0].substring(1))), v);
                } else {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(f[0]));
                    if (item != Items.AIR) {
                        it.put(item, v);
                    }
                }
            }
            tags = tg;
            items = it;
        }
        Integer v = items.get(stack.getItem());
        if (v != null) {
            return v;
        }
        //1.0.4-beta (lot 4) : la poule mange toute graine (vanilla, du mod, des astres), comme celles de #forge:seeds
        if (this == CHICKEN && PetFood.seed(stack)) {
            return 12;
        }
        for (Map.Entry<TagKey<Item>, Integer> e : tags.entrySet()) {
            if (stack.is(e.getKey())) {
                return e.getValue();
            }
        }
        return 0;
    }

    /** Un aliment convient-il a une espece quelconque (mangeoire) ? */
    public static boolean anyFeed(ItemStack stack) {
        for (AnimalSpecies s : values()) {
            if (s.feedValue(stack) > 0) {
                return true;
            }
        }
        return false;
    }
}
