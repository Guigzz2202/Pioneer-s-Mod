package fr.pioneers.gametest;

import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.farm.FarmIds;
import fr.pioneers.farm.PiFishing;
import fr.pioneers.world.pet.CatBasketTile;
import fr.pioneers.world.pet.PetCat;
import fr.pioneers.world.pet.PetDog;
import fr.pioneers.world.pet.PetHomeBlock;
import fr.pioneers.world.pet.PetMode;
import fr.pioneers.world.pet.PetReact;
import fr.pioneers.world.pet.PetRegistry;
import fr.pioneers.world.pet.PiPets;
import fr.pioneers.world.season.SeasonData;
import fr.pioneers.world.season.SeasonEvents;
import fr.pioneers.world.wild.GrayWolf;
import fr.pioneers.world.wild.PiWild7;
import fr.pioneers.world.wild.PiWild9;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 28 (retours de jeu 3e serie, 2 : faune ; GameTests) :
 * <ul>
 *     <li>CHIEN (retour 35) : un chien qui SUIT son maitre court sur un vrai loup arrive a 6 blocs en au plus {@link PetReact#REACT_MAX} ticks
 *     (cible prise ET course de l'attaque lancee) ; avant : rien tant que le loup n'avait pas mordu le maitre ;</li>
 *     <li>CHAT (retour 35) : un chat lie a son panier, de jour, avec une mare a 8 blocs, va PECHER tout seul (vrai but, vraie recherche de
 *     l'eau, affut, prise : tirage de l'aptitude force) et rapporte le poisson dans son panier ;</li>
 *     <li>REQUIN GRIS (retour 36) : un seul oeuf d'apparition (celui du vrai requin, entite {@code pioneers:gray_reef_shark}) ; l'oeuf du
 *     poisson n'existe plus (registre, onglet) et un ancien oeuf de monde y est remappe.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot28Tests {

    private PatchP105Lot28Tests() {
    }

    static <T extends Entity> T add(Gt.Scope s, EntityType<T> type, BlockPos abs) {
        T e = type.create(s.level);
        Gt.check(e != null, "creation de " + type);
        BlockPos at = Gt.standing(s.level, abs);   // 1.0.6 : sur le sol du gabarit, pas dedans
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        s.level.addFreshEntity(e);
        s.onCleanup(() -> {
            if (!e.isRemoved()) {
                e.discard();
            }
            PetRegistry.get(s.server).remove(e.getUUID());
        });
        return e;
    }

    // ------------------------------------------------------------------ 1. chien contre loup

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_28_dog", timeoutTicks = 24)
    public static void p105_28_chien_court_sur_le_loup(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p28d", "Remi", new BlockPos(3, 1, 3));
        PetDog dog = add(s, PiPets.DOGS.get(1).get(), h.absolutePos(new BlockPos(5, 1, 5)));
        dog.tame(p);
        dog.setMode(PetMode.FOLLOW);
        GrayWolf wolf = add(s, PiWild7.GRAY_WOLF.get(), h.absolutePos(new BlockPos(11, 1, 5)));
        long start = s.level.getGameTime();
        h.succeedWhen(() -> {
            Gt.check(dog.getTarget() == wolf, "le chien a pris le loup pour cible");
            Gt.check(dog.isAggressive(), "la course de l'attaque est lancee (Strike)");
            Gt.check(s.level.getGameTime() - start <= PetReact.REACT_MAX, "reaction en " + (s.level.getGameTime() - start) + " ticks (au plus 20)");
            s.done();
        });
    }

    // ------------------------------------------------------------------ 2. chat pecheur

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_28_cat", timeoutTicks = 900)
    public static void p105_28_chat_va_pecher(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        long offset = SeasonData.get(s.server.overworld()).offset(), time = s.level.getDayTime();
        s.onCleanup(() -> {
            s.level.setDayTime(time);
            SeasonEvents.setOffset(s.server, offset);
        });
        s.level.setDayTime(time - Math.floorMod(time, 24000L) + 1000L);
        ServerPlayer p = s.player("p28c", "Lise", new BlockPos(2, 1, 2));
        BlockPos basket = h.absolutePos(new BlockPos(4, 1, 10));
        s.level.setBlock(basket, PiPets.BASKETS.get(2).getBlock().defaultBlockState(), 3);
        //1.0.6 : la mare au niveau du sol du gabarit (y = 1) ; en y = 0 elle etait sous la pierre, hors de portee du chat
        BlockPos water = h.absolutePos(new BlockPos(12, 1, 10));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                s.level.setBlock(water.offset(dx, 0, dz), Blocks.WATER.defaultBlockState(), 3);
            }
        }
        PetCat cat = add(s, PiPets.CATS.get(5).get(), h.absolutePos(new BlockPos(6, 1, 10)));
        cat.tame(p);
        PetHomeBlock.link(cat, s.level, basket, p);
        cat.brain().testCatch = true;
        cat.brain().nextFish = 0;
        h.succeedWhen(() -> {
            Gt.check(s.level.getBlockEntity(basket) instanceof CatBasketTile t && !t.empty(), "un poisson peche est dans le panier");
            s.done();
        });
    }

    // ------------------------------------------------------------------ 3. un seul oeuf du requin gris

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_28_egg", timeoutTicks = 20)
    public static void p105_28_un_seul_oeuf_du_requin_gris(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(!ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse("pioneers:fish_gray_reef_shark_spawn_egg")),
              "l'oeuf du poisson requin gris n'est plus enregistre");
        int eggs = 0;
        for (Item it : ForgeRegistries.ITEMS.getValues()) {
            if (it instanceof ForgeSpawnEggItem egg) {
                EntityType<?> t = egg.getType(null);
                if (t == PiWild9.GRAY_REEF_SHARK.get() || t == PiFishing.speciesType(PiFishing.SPECIES_TYPES.size() - 1)) {
                    eggs++;
                }
            }
        }
        Gt.equal(1, eggs, "oeufs d'apparition du requin gris");
        Gt.check(!FarmIds.FISH_EGGS.contains("fish_gray_reef_shark_spawn_egg"), "plus dans l'onglet des oeufs de poissons");
        Gt.equal("gray_reef_shark_spawn_egg", PiRemaps.simplifiedItem("fish_gray_reef_shark_spawn_egg"), "ancien oeuf remappe vers le vrai requin");
        Gt.check(PiFishing.speciesOf(PiFishing.speciesType(PiFishing.SPECIES_TYPES.size() - 1)) >= 0, "l'entite poisson requin reste enregistree");
        s.done();
    }
}
