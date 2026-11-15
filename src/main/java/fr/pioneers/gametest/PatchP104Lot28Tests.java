package fr.pioneers.gametest;

import fr.pioneers.farm.aquarium.AquariumBlock;
import fr.pioneers.farm.aquarium.AquariumTile;
import fr.pioneers.farm.aquarium.Aquariums;
import fr.pioneers.farm.aquarium.PiAquarium;
import fr.pioneers.farm.device.DeviceBlock;
import fr.pioneers.farm.device.HutchBlock;
import fr.pioneers.farm.device.HutchTile;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.herd.GeneratedHerd;
import fr.pioneers.farm.herd.HerdBreedEntity;
import fr.pioneers.farm.herd.HerdBreeds;
import fr.pioneers.building.home.beam.PiBeams;
import fr.pioneers.building.home.beam.TimberBeamBlock;
import fr.pioneers.tech.materials.ToolProfiles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 28, GameTests) : retours de Guillaume du 09/11 (contenu). Clapier : un ancien clapier 1 x 1 grandit en 2 x 1 quand la
 * place est libre (case principale + case laterale, tuile sur la principale), et la case principale tombe quand la laterale disparait ;
 * aquarium : deux blocs empiles forment un bassin, le seau d'eau remplit la case vide la plus basse, un seau de morue ajoute un habitant,
 * le seau vide le reprend ; races : un lapin, un cheval, un ane et un lama vanilla deviennent une entite de race (un type et un oeuf par race) ;
 * poutres : une par essence, forme de la section le long de l'axe, recettes ; contenu (fil de coton, boulangerie, seau de crustace) et
 * infobulle commune des equipements (materiau, durabilite, tier).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot28Tests {

    private PatchP104Lot28Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_28_hutch", timeoutTicks = 40)
    public static void p104_28_clapier_double(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        HutchBlock hutch = (HutchBlock) PiFarmDevices.DEVICES.get(DeviceBlock.Kind.RABBIT_HUTCH).get();
        BlockPos a = h.absolutePos(new BlockPos(4, 2, 4));
        BlockState single = hutch.defaultBlockState().setValue(HutchBlock.FACING, Direction.NORTH).setValue(HutchBlock.PART, HutchBlock.Part.SINGLE);
        Gt.equal(HutchBlock.Part.SINGLE, hutch.defaultBlockState().getValue(HutchBlock.PART), "etat par defaut = ancien clapier 1 x 1 (mondes d'avant)");
        h.getLevel().setBlock(a, single, 3);
        BlockPos side = a.relative(HutchBlock.sideOf(single));
        Gt.check(HutchBlock.grow(h.getLevel(), a, h.getLevel().getBlockState(a)), "l'ancien clapier grandit (place libre)");
        Gt.equal(HutchBlock.Part.MAIN, h.getLevel().getBlockState(a).getValue(HutchBlock.PART), "case principale");
        Gt.equal(HutchBlock.Part.SIDE, h.getLevel().getBlockState(side).getValue(HutchBlock.PART), "case laterale a cote");
        Gt.check(h.getLevel().getBlockEntity(a) instanceof HutchTile, "la tuile reste sur la case principale");
        Gt.equal(a, HutchBlock.mainOf(h.getLevel().getBlockState(side), side), "la case laterale retrouve la principale");
        Gt.check(!HutchBlock.grow(h.getLevel(), a, h.getLevel().getBlockState(a)), "un clapier double ne grandit plus");
        //Un ancien clapier sans place a cote reste simple
        BlockPos b = a.south(2);
        h.getLevel().setBlock(b.relative(HutchBlock.sideOf(single)), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(b, single, 3);
        Gt.check(!HutchBlock.grow(h.getLevel(), b, h.getLevel().getBlockState(b)), "pas de place : il reste simple");
        //La case laterale retiree : la principale tombe aussi (paire indivisible)
        h.getLevel().setBlock(side, Blocks.AIR.defaultBlockState(), 3);
        h.runAfterDelay(2, () -> {
            Gt.check(!(h.getLevel().getBlockState(a).getBlock() instanceof HutchBlock), "sans sa case laterale, la case principale disparait");
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_28_aquarium", timeoutTicks = 40)
    public static void p104_28_aquarium(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos lo = h.absolutePos(new BlockPos(4, 2, 4)), hi = lo.above();
        BlockState glass = PiAquarium.AQUARIUM.getBlock().defaultBlockState();
        h.getLevel().setBlock(lo, glass, 3);
        h.getLevel().setBlock(hi, glass, 3);
        Gt.equal(2L, (long) Aquariums.tank(h.getLevel(), hi).size(), "deux aquariums empiles = un bassin");
        Player p = h.makeMockPlayer();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        Aquariums.use(h.getLevel().getBlockState(hi), h.getLevel(), hi, p, InteractionHand.MAIN_HAND);
        Gt.check(h.getLevel().getBlockState(lo).getValue(AquariumBlock.FILLED), "le seau d'eau remplit la case la plus basse");
        Gt.check(!h.getLevel().getBlockState(hi).getValue(AquariumBlock.FILLED), "la case du haut reste vide");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        Aquariums.use(h.getLevel().getBlockState(hi), h.getLevel(), hi, p, InteractionHand.MAIN_HAND);
        Gt.check(h.getLevel().getBlockState(hi).getValue(AquariumBlock.FILLED), "second seau : la case du haut");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD_BUCKET));
        Aquariums.use(h.getLevel().getBlockState(hi), h.getLevel(), hi, p, InteractionHand.MAIN_HAND);
        AquariumTile tile = (AquariumTile) h.getLevel().getBlockEntity(hi);
        Gt.equal(1L, (long) tile.count(false), "la morue nage dans la case visee");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        Aquariums.use(h.getLevel().getBlockState(hi), h.getLevel(), hi, p, InteractionHand.MAIN_HAND);
        Gt.equal(0L, (long) tile.count(false), "le seau vide reprend le poisson");
        Gt.check(h.getLevel().getBlockState(hi).getValue(AquariumBlock.FILLED), "l'eau reste quand on reprend un habitant");
        Gt.check(PiAquarium.CRUSTACEAN_BUCKET.get() != null, "seau de crustace enregistre");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_28_breeds", timeoutTicks = 40)
    public static void p104_28_races(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal((long) GeneratedHerd.TYPES.length, (long) HerdBreeds.TYPES.size(), "un type d'entite par race");
        Gt.equal((long) GeneratedHerd.TYPES.length, (long) HerdBreeds.EGGS.size(), "un oeuf d'apparition par race");
        for (GeneratedHerd.HerdType t : GeneratedHerd.TYPES) {
            Gt.check(BuiltInRegistries.ITEM.containsKey(new ResourceLocation("pioneers", t.id() + "_spawn_egg")), "oeuf de " + t.id());
        }
        List<EntityType<?>> vanilla = List.of(EntityType.RABBIT, EntityType.HORSE, EntityType.DONKEY, EntityType.LLAMA);
        List<Entity> made = new ArrayList<>();
        int i = 0;
        for (EntityType<?> type : vanilla) {
            Entity e = type.create(h.getLevel());
            Gt.check(e != null, "entite vanilla " + type);
            e.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(2 + 2 * i++, 2, 4)));
            h.getLevel().addFreshEntity(e);
            Entity ne = HerdBreeds.convert(h.getLevel(), e);
            Gt.check(ne instanceof HerdBreedEntity, "converti en entite de race : " + type);
            Gt.check(e.isRemoved(), "l'animal vanilla est retire : " + type);
            made.add(ne);
        }
        Gt.equal(HerdBreeds.RABBIT, (long) ((HerdBreedEntity) made.get(0)).herdType().species(), "lapin -> race de lapin");
        Gt.equal(HerdBreeds.DONKEY, (long) ((HerdBreedEntity) made.get(2)).herdType().species(), "ane -> race d'ane");
        Gt.equal(HerdBreeds.LLAMA, (long) ((HerdBreedEntity) made.get(3)).herdType().species(), "lama -> race de lama");
        h.runAfterDelay(3, () -> {
            AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(Gt.SMALL_SIZE);
            Gt.check(h.getLevel().getEntitiesOfClass(Entity.class, box, x -> x.getType() == EntityType.RABBIT).isEmpty(), "plus de lapin vanilla");
            for (Entity e : made) {
                e.discard();
            }
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_28_beams", timeoutTicks = 20)
    public static void p104_28_poutres(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(12L, (long) PiBeams.BEAMS.size(), "12 poutres : 11 bois et le bois traite");
        BlockPos a = h.absolutePos(new BlockPos(4, 2, 4));
        BlockState beam = PiBeams.BEAMS.get(0).getBlock().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        h.getLevel().setBlock(a, beam, 3);
        AABB box = h.getLevel().getBlockState(a).getShape(h.getLevel(), a).bounds();
        Gt.check(box.minX == 0 && box.maxX == 1, "le long de l'axe X : toute la longueur");
        Gt.check(Math.abs(box.minY - TimberBeamBlock.FROM / 16) < 1e-6 && Math.abs(box.maxY - TimberBeamBlock.TO / 16) < 1e-6, "section de 12 px");
        var recipes = h.getLevel().getRecipeManager();
        for (String w : PiBeams.WOODS) {
            Gt.check(recipes.byKey(new ResourceLocation("pioneers", "beams/" + PiBeams.id(w))).isPresent(), "recette de la poutre " + w);
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_28_content", timeoutTicks = 20)
    public static void p104_28_contenu_infobulles(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (String id : new String[]{"cotton_thread", "baguette", "brioche", "chocolate_croissant", "raisin_swirl", "madeleines", "brownies",
              "pound_cake", "kouign_amann", "pretzel", "apple_turnover", "raw_baguette", "crustacean_bucket", "aquarium"}) {
            Gt.check(BuiltInRegistries.ITEM.containsKey(new ResourceLocation("pioneers", id)), "objet " + id);
        }
        Gt.check(h.getLevel().getRecipeManager().byKey(new ResourceLocation("pioneers", "cotton/spin_cotton")).isPresent(), "filage du coton");
        //Infobulle commune : pioche en acier usee -> materiau, minage, durabilite, tier
        ItemStack pick = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("pioneers", "steel_pickaxe")));
        pick.setDamageValue(pick.getMaxDamage() - 10);
        List<Component> lines = new ArrayList<>();
        lines.add(pick.getHoverName());
        ToolProfiles.ToolClientEvents.tooltip(new ItemTooltipEvent(pick, null, lines, TooltipFlag.Default.NORMAL));
        List<String> keys = new ArrayList<>();
        for (Component c : lines) {
            collect(c, keys);
        }
        for (String k : new String[]{"tooltip.pioneers.gear.material", "tooltip.pioneers.gear.mining", "tooltip.pioneers.gear.durability",
              "tooltip.pioneers.tier"}) {
            Gt.check(keys.contains(k), "infobulle commune : " + k);
        }
        s.done();
    }

    /** Cles de langue d'une ligne (et de ses arguments). */
    private static void collect(Component c, List<String> out) {
        if (c.getContents() instanceof TranslatableContents t) {
            out.add(t.getKey());
            for (Object a : t.getArgs()) {
                if (a instanceof Component sub) {
                    collect(sub, out);
                }
            }
        }
        for (Component sib : c.getSiblings()) {
            collect(sib, out);
        }
    }
}
