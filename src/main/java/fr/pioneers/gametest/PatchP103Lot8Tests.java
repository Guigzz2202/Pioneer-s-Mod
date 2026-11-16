package fr.pioneers.gametest;

import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.BreedDef;
import fr.pioneers.farm.animal.PiAnimals;
import fr.pioneers.farm.animal.breed.BreedAnimal;
import fr.pioneers.farm.animal.breed.BreedConversion;
import fr.pioneers.farm.animal.breed.PiBreeds;
import fr.pioneers.farm.animal.breed.PiCow;
import fr.pioneers.farm.animal.breed.PiGoat;
import fr.pioneers.farm.animal.breed.PiSheep;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.PiFish;
import fr.pioneers.logistics.robotics.ZoneScanner;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.3-beta, lot 8 (GameTests) : une entite par race (25) et par espece de poisson (54), conversion des animaux vanilla et des
 * poissons generiques sans perte (mondes existants, apparitions, /summon, autres mods), reproduction entre races (50 / 50), tailles,
 * troupeaux des robots par espece.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot8Tests {

    private PatchP103Lot8Tests() {
    }

    private static Entity byUuid(GameTestHelper h, UUID id) {
        return ((ServerLevel) h.getLevel()).getEntity(id);
    }

    // ================================================================== registres
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_breeds", timeoutTicks = 20)
    public static void p103_8_une_entite_par_race(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int n = 0;
        for (AnimalSpecies sp : AnimalSpecies.values()) {
            Gt.equal(5, sp.breeds().length, sp.id() + " : 5 races");
            for (int i = 0; i < sp.breeds().length; i++) {
                BreedDef b = sp.breed(i);
                EntityType<? extends Animal> t = PiBreeds.type(sp, i);
                Gt.equal(i, PiBreeds.breedOf(t), "race du type " + b.id());
                Gt.equal(sp, PiBreeds.speciesOf(t), "espece du type " + b.id());
                Gt.check(t.is(PiBreeds.TAGS.get(sp.ordinal())), b.id() + " dans le tag de son espece");
                Entity e = t.create(h.getLevel());
                Gt.check(e instanceof BreedAnimal ba && ba.breed() == i && AnimalSpecies.of(e) == sp, b.id() + " : entite de race");
                Class<?> vanilla = switch (sp) {
                    case COW -> Cow.class;
                    case SHEEP -> Sheep.class;
                    case PIG -> Pig.class;
                    case CHICKEN -> Chicken.class;
                    case GOAT -> Goat.class;
                };
                Gt.check(vanilla.isInstance(e), b.id() + " etend la classe vanilla (traite, tonte, ponte, laisse, autres mods)");
                Gt.check(PiAnimals.get(e) != null, b.id() + " porte les donnees d'elevage (sexe, faim, soif)");
                ItemStack egg = sp.egg(i);
                Gt.check(egg.getItem() instanceof ForgeSpawnEggItem se && se.getType(null) == t, b.id() + " : oeuf d'apparition");
                Gt.check(t.getWidth() < 1F && t.getHeight() < 2F, b.id() + " : boite < 1 x 2 (portes, enclos)");
                Gt.check(EntityType.COW.is(PiBreeds.TAGS.get(0)), "le type vanilla compte dans pioneers:cows");
                //le butin est celui de l'entite (PiCow, PiSheep... renvoient la table vanilla), pas la table par defaut du type, toujours
                //nommee d'apres l'identifiant pioneers:<race>
                Gt.check(!((net.minecraft.world.entity.LivingEntity) e).getLootTable().getNamespace().equals("pioneers"), b.id() + " : butin vanilla de l'espece");
                e.discard();
                n++;
            }
        }
        Gt.equal(25, n, "25 entites de race");
        Gt.equal(GeneratedFish.SPECIES.length, PiFishing.SPECIES_TYPES.size(), "une entite par espece de poisson");
        Gt.check(PiBreeds.type(AnimalSpecies.COW, 0).getHeight() > PiBreeds.type(AnimalSpecies.SHEEP, 3).getHeight() * 1.8F,
              "une Prim'Holstein bien plus haute qu'une Ouessant");
        Gt.check(PiBreeds.type(AnimalSpecies.CHICKEN, 4).getHeight() < 0.5F, "la Soie est toute petite");
        Gt.check(PiBreeds.type(AnimalSpecies.GOAT, 1).getWidth() < PiBreeds.type(AnimalSpecies.SHEEP, 1).getWidth(), "la chevre plus fine que le mouton");
        int pike = index("pike"), roach = index("roach");
        Gt.check(PiFishing.speciesType(pike).getWidth() > PiFishing.speciesType(roach).getWidth() * 2F, "un brochet bien plus long qu'un gardon");
        s.done();
    }

    private static int index(String id) {
        for (int i = 0; i < GeneratedFish.SPECIES.length; i++) {
            if (GeneratedFish.SPECIES[i].id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    // ================================================================== conversion d'une vache vanilla (monde existant, autre mod, /summon)
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_breeds_cow", timeoutTicks = 60)
    public static void p103_8_vache_vanilla_convertie_sans_perte(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        h.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_FENCE.defaultBlockState());
        Cow cow = h.spawn(EntityType.COW, new BlockPos(4, 2, 4));
        UUID id = cow.getUUID();
        AnimalData d = PiAnimals.get(cow);
        Gt.check(d != null && d.init, "vache vanilla : race tiree au climat a l'apparition");
        d.breed = 3;
        d.male = true;
        d.domestic = true;
        d.hunger = 42;
        d.thirst = 37;
        d.milkTimer = 123;
        cow.setCustomName(Component.literal("Marguerite"));
        cow.setHealth(6);
        cow.setAge(-12000);
        cow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 1));
        cow.getPersistentData().putInt("autre_mod", 7);
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)));
        cow.setLeashedTo(knot, true);
        h.runAfterDelay(10, () -> {
            Entity e = byUuid(h, id);
            Gt.check(cow.isRemoved(), "l'ancienne vache vanilla a disparu");
            Gt.check(e instanceof PiCow pc && pc.breed() == 3 && pc.getType() == PiBreeds.type(AnimalSpecies.COW, 3),
                  "meme UUID : la vache est devenue une Highland (race de ses donnees) : " + e);
            PiCow c = (PiCow) e;
            AnimalData nd = PiAnimals.get(c);
            Gt.check(nd != null && nd.init && nd.male && nd.domestic && nd.breed == 3, "sexe, domestication et race gardes");
            Gt.check(Math.abs(nd.hunger - 42) < 2 && Math.abs(nd.thirst - 37) < 2, "faim et soif gardees : " + nd.hunger + " / " + nd.thirst);
            Gt.check(Math.abs(nd.milkTimer - 123) < 5, "minuterie du lait gardee");
            Gt.equal("Marguerite", c.getCustomName() == null ? null : c.getCustomName().getString(), "nom garde");
            Gt.check(Math.abs(c.getHealth() - 6) < 0.01F, "vie gardee");
            Gt.check(c.isBaby(), "age garde (petit)");
            Gt.check(c.hasEffect(MobEffects.MOVEMENT_SPEED), "effets gardes");
            Gt.equal(7, c.getPersistentData().getInt("autre_mod"), "donnees des autres mods gardees");
            Gt.check(c.isLeashed() && c.getLeashHolder() instanceof LeashFenceKnotEntity, "laisse gardee (noeud de cloture)");
            s.done();
        });
    }

    // ================================================================== mouton tondu, couleur
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_breeds_sheep", timeoutTicks = 40)
    public static void p103_8_mouton_vanilla_tondu(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Sheep sheep = h.spawn(EntityType.SHEEP, new BlockPos(4, 2, 4));
        UUID id = sheep.getUUID();
        AnimalData d = PiAnimals.get(sheep);
        d.breed = 0;
        d.woolTimer = 200;
        sheep.setSheared(true);
        sheep.setColor(DyeColor.LIGHT_BLUE);
        h.runAfterDelay(5, () -> {
            Entity e = byUuid(h, id);
            Gt.check(e instanceof PiSheep ps && ps.breed() == 0, "mouton vanilla -> Merinos");
            PiSheep ps = (PiSheep) e;
            Gt.check(ps.isSheared(), "toison tondue gardee");
            Gt.equal(DyeColor.LIGHT_BLUE, ps.getColor(), "couleur de la laine gardee");
            Gt.check(Math.abs(PiAnimals.get(ps).woolTimer - 200) < 10, "repousse de la toison gardee");
            Gt.check(ps.getDefaultLootTable().getNamespace().equals("minecraft"), "butin vanilla du mouton tondu");
            s.done();
        });
    }

    // ================================================================== bete sans race (donnees jamais initialisees) : race au climat
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_breeds_climate", timeoutTicks = 20)
    public static void p103_8_bete_sans_race(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
        PiAnimals.get(pig).init = false;
        Entity e = BreedConversion.convert((ServerLevel) h.getLevel(), pig);
        Gt.check(e instanceof BreedAnimal b && b.species() == AnimalSpecies.PIG, "cochon vanilla sans race -> une race de cochon (climat)");
        Gt.check(pig.isRemoved() && !e.isRemoved(), "un seul cochon reste");
        Gt.check(BreedConversion.target((ServerLevel) h.getLevel(), e) == null, "une entite de race n'est jamais reconvertie");
        s.done();
    }

    // ================================================================== reproduction entre races
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_breeds_cross", timeoutTicks = 20)
    public static void p103_8_croisement_cinquante_cinquante(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        PiCow a = (PiCow) h.spawn(PiBreeds.type(AnimalSpecies.COW, 0), new BlockPos(3, 2, 4));
        PiCow b = (PiCow) h.spawn(PiBreeds.type(AnimalSpecies.COW, 1), new BlockPos(5, 2, 4));
        Gt.check(a.getClass() == b.getClass(), "deux races de vache : meme classe (Animal.canMate)");
        a.setInLove(null);
        b.setInLove(null);
        Gt.check(a.canMate(b), "une Prim'Holstein et une Jersiaise en amour peuvent s'accoupler");
        int[] n = new int[5];
        Set<EntityType<?>> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            Cow child = a.getBreedOffspring((ServerLevel) h.getLevel(), b);
            Gt.check(child instanceof PiCow, "le petit est une entite de race");
            n[((PiCow) child).breed()]++;
            seen.add(child.getType());
            child.discard();
        }
        Gt.check(n[2] + n[3] + n[4] == 0, "le petit prend la race de l'un des deux parents");
        Gt.check(n[0] > 140 && n[1] > 140, "50 / 50 entre les deux races : " + n[0] + " / " + n[1]);
        PiCow c = (PiCow) h.spawn(PiBreeds.type(AnimalSpecies.COW, 0), new BlockPos(4, 2, 2));
        for (int i = 0; i < 20; i++) {
            Gt.equal(0, ((PiCow) c.getBreedOffspring((ServerLevel) h.getLevel(), a)).breed(), "meme race -> meme race");
        }
        PiGoat g1 = (PiGoat) h.spawn(PiBreeds.type(AnimalSpecies.GOAT, 0), new BlockPos(2, 2, 6));
        PiGoat g2 = (PiGoat) h.spawn(PiBreeds.type(AnimalSpecies.GOAT, 1), new BlockPos(6, 2, 6));
        g1.setInLove(null);
        g2.setInLove(null);
        Gt.check(g1.canMate(g2), "deux races de chevre s'accouplent (cerveau : BreedMakeLove)");
        Gt.equal(ZoneScanner.herdKey(a), ZoneScanner.herdKey(b), "robot eleveur : un troupeau par espece, toutes races");
        s.done();
    }

    // ================================================================== poissons : type generique -> espece
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_breeds_fish", timeoutTicks = 40)
    public static void p103_8_poisson_generique_converti(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (int x = 1; x < 8; x++) {
            for (int z = 1; z < 8; z++) {
                for (int y = 1; y < 4; y++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
                }
            }
        }
        int pike = index("pike");
        PiFish f = h.spawn(PiFishing.FISH.get(), new BlockPos(4, 2, 4));
        f.set(pike, 1.15F);
        f.setFromBucket(true);
        UUID id = f.getUUID();
        //Seau : un seau d'avant (type generique) relache un poisson qui devient son espece
        CompoundTag bucket = new CompoundTag();
        bucket.putInt("Species", index("roach"));
        bucket.putFloat("Size", 0.9F);
        PiFish g = h.spawn(PiFishing.FISH.get(), new BlockPos(3, 2, 3));
        g.loadFromBucketTag(bucket);
        UUID gid = g.getUUID();
        h.runAfterDelay(5, () -> {
            Entity e = byUuid(h, id);
            Gt.check(e instanceof PiFish p && p.getType() == PiFishing.speciesType(pike), "poisson generique -> pioneers:fish_pike : " + e);
            PiFish p = (PiFish) e;
            Gt.equal(pike, p.species(), "espece gardee");
            Gt.check(Math.abs(p.individual() - 1.15F) < 1e-4, "taille de l'individu gardee");
            Gt.check(p.fromBucket(), "poisson de seau (ne disparait pas) garde");
            Gt.check(byUuid(h, gid) instanceof PiFish r && r.getType() == PiFishing.speciesType(index("roach")), "seau d'avant : gardon");
            Gt.check(BreedConversion.target((ServerLevel) h.getLevel(), p) != null && !BreedConversion.needs(p), "l'espece n'est plus convertie");
            s.done();
        });
    }
}
