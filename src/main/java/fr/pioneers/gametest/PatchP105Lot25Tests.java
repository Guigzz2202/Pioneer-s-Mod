package fr.pioneers.gametest;

import fr.pioneers.farm.aquarium.AquariumBlock;
import fr.pioneers.farm.aquarium.AquariumCompose;
import fr.pioneers.farm.aquarium.AquariumDecor;
import fr.pioneers.farm.aquarium.AquariumRules;
import fr.pioneers.farm.aquarium.AquariumTile;
import fr.pioneers.farm.aquarium.Aquariums;
import fr.pioneers.farm.aquarium.PiAquarium;
import fr.pioneers.farm.fish.FishSpawns;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.LiveRelease;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 25 (retours de jeu 2e serie : aquarium et peche, GameTests) : AQUARIUM NEUF VIDE (aucune composition, sauvegarde
 * marquee) et ANCIEN aquarium converti en composition equivalente ; Maj + clic droit (evenement {@code RightClickBlock}) pris par
 * l'aquarium quel que soit l'objet, clic droit simple laisse a ses gestes ; COMPOSITION posee (objets pris), refusee sans sol pour une
 * plante, retiree (objets rendus, plante enracinee comprise), puis case CASSEE (objets rendus au sol) ; REQUIN GRIS : refuse dans un bassin de
 * 2 cases, accepte dans un bassin de 18 ; jamais tire pour une apparition, tire a la peche ; relache = vrai requin.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot25Tests {

    private PatchP105Lot25Tests() {
    }

    static BlockState glass(boolean wet) {
        return PiAquarium.AQUARIUM.getBlock().defaultBlockState().setValue(AquariumBlock.FILLED, wet);
    }

    static int shark() {
        for (int i = 0; i < GeneratedFish.SPECIES.length; i++) {
            if (GeneratedFish.SPECIES[i].id().equals("gray_reef_shark")) {
                return i;
            }
        }
        return -1;
    }

    static int count(Player p, Item item) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) {
            n += st.is(item) ? st.getCount() : 0;
        }
        return n;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_25_vide", timeoutTicks = 20)
    public static void p105_25_aquarium_vide(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        h.getLevel().setBlock(pos, glass(true), 3);
        AquariumTile t = (AquariumTile) h.getLevel().getBlockEntity(pos);
        Gt.equal(0L, (long) t.decorCount(), "aquarium neuf : aucun decor (eau seule)");
        CompoundTag saved = t.saveWithoutMetadata();
        Gt.equal(1L, (long) saved.getInt("decor_v"), "sauvegarde marquee : plus jamais de decor tire au hasard");
        //ancien aquarium (NBT d'avant le lot 25 : habitants, pas de decor_v) : composition equivalente au decor d'avant
        CompoundTag old = new CompoundTag();
        old.put("dwellers", new ListTag());
        t.load(old);
        t.convertLegacy();
        Gt.check(t.decorCount() >= 5, "ancien aquarium : decor converti en composition (" + t.decorCount() + " elements)");
        int sand = 0;
        for (int i = 0; i < AquariumDecor.SLOTS; i++) {
            sand += t.ground(i) != null && t.ground(i).key().equals("sand") ? 1 : 0;
        }
        Gt.check(sand >= 3, "ancien aquarium : fond de sable");
        Gt.check(t.decorItems().size() == t.decorCount(), "chaque element a son objet");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_25_maj", timeoutTicks = 20)
    public static void p105_25_maj_clic_droit(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        h.getLevel().setBlock(pos, glass(true), 3);
        Player p = h.makeMockPlayer();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        for (ItemStack held : new ItemStack[]{ItemStack.EMPTY, new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.COD), new ItemStack(Items.SAND)}) {
            p.setItemInHand(InteractionHand.MAIN_HAND, held);
            p.setShiftKeyDown(true);
            PlayerInteractEvent.RightClickBlock e = ForgeHooks.onRightClickBlock(p, InteractionHand.MAIN_HAND, pos, hit);
            Gt.check(e.isCanceled(), "Maj + clic droit avec " + held + " : pris par l'aquarium (interface)");
            p.setShiftKeyDown(false);
            e = ForgeHooks.onRightClickBlock(p, InteractionHand.MAIN_HAND, pos, hit);
            Gt.check(!e.isCanceled(), "clic droit simple avec " + held + " : laisse aux gestes de l'aquarium");
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_25_compo", timeoutTicks = 40)
    public static void p105_25_composition(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        h.getLevel().setBlock(pos, glass(true), 3);
        Player p = h.makeMockPlayer();
        p.getInventory().add(new ItemStack(Items.SAND, 2));
        p.getInventory().add(new ItemStack(Items.KELP, 1));
        p.getInventory().add(new ItemStack(Items.STONE, 1));
        int sand = AquariumDecor.index(AquariumDecor.of("sand")), kelp = AquariumDecor.index(AquariumDecor.of("kelp")),
              rock = AquariumDecor.index(AquariumDecor.of("rock"));
        Gt.equal(null, AquariumCompose.edit(p, pos, pos, 0, 0, sand), "sable pose");
        Gt.equal(1L, (long) count(p, Items.SAND), "un sable pris");
        Gt.equal("aquarium.pioneers.decor.refuse.no_ground", AquariumCompose.edit(p, pos, pos, 1, 1, kelp), "varech sans sol : refuse");
        Gt.equal(1L, (long) count(p, Items.KELP), "varech refuse : rien pris");
        Gt.equal(null, AquariumCompose.edit(p, pos, pos, 0, 1, kelp), "varech enracine dans le sable");
        Gt.equal(null, AquariumCompose.edit(p, pos, pos, 3, 1, rock), "roche posee sur le verre");
        Gt.equal("aquarium.pioneers.decor.refuse.need", AquariumCompose.edit(p, pos, pos, 2, 1, rock), "plus de pierre : refuse");
        AquariumTile t = (AquariumTile) h.getLevel().getBlockEntity(pos);
        Gt.equal(3L, (long) t.decorCount(), "trois elements poses");
        //retrait du sol : la plante enracinee revient aussi
        Gt.equal(null, AquariumCompose.edit(p, pos, pos, 0, 0, -1), "sol retire");
        Gt.check(count(p, Items.SAND) == 2 && count(p, Items.KELP) == 1, "sable et varech rendus");
        Gt.equal(1L, (long) t.decorCount(), "reste la roche");
        //la case cassee rend la roche
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        Gt.check(drops.stream().anyMatch(e -> e.getItem().is(Items.STONE)), "case cassee : la pierre de la roche est rendue");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_25_requin", timeoutTicks = 40)
    public static void p105_25_requin_gris(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int sh = shark();
        Gt.check(sh >= 0, "requin gris parmi les especes de poisson");
        Gt.check(AquariumRules.shark(GeneratedFish.SPECIES[sh]) && FishSpawns.catchOnly(GeneratedFish.SPECIES[sh]), "requin gris : peche seulement");
        Item item = ForgeRegistries.ITEMS.getValue(fr.pioneers.Pioneers.rl("gray_reef_shark"));
        Gt.check(item != null && item != Items.AIR, "objet pioneers:gray_reef_shark");
        //bassin de 2 cases : refuse
        BlockPos a = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlock(a, glass(true), 3);
        h.getLevel().setBlock(a.east(), glass(true), 3);
        Player p = h.makeMockPlayer();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
        Aquariums.use(h.getLevel().getBlockState(a), h.getLevel(), a, p, InteractionHand.MAIN_HAND);
        Gt.equal(2L, (long) p.getMainHandItem().getCount(), "requin refuse dans un bassin de 2 cases (objet garde)");
        //bassin de 3 x 2 x 3 = 18 cases : accepte
        BlockPos b = h.absolutePos(new BlockPos(4, 2, 4));
        for (BlockPos q : BlockPos.betweenClosed(b, b.offset(2, 1, 2))) {
            h.getLevel().setBlock(q, glass(true), 3);
        }
        Aquariums.use(h.getLevel().getBlockState(b), h.getLevel(), b, p, InteractionHand.MAIN_HAND);
        Gt.equal(1L, (long) p.getMainHandItem().getCount(), "requin accepte dans un bassin de 18 cases");
        long in = 0;
        for (BlockPos q : BlockPos.betweenClosed(b, b.offset(2, 1, 2))) {
            in += ((AquariumTile) h.getLevel().getBlockEntity(q)).count(false);
        }
        Gt.equal(1L, in, "un requin dans le bassin");
        //tirages : jamais en apparition, parfois a la peche (mer chaude)
        RandomSource r = RandomSource.create(25);
        int wild = 0, caught = 0;
        for (int i = 0; i < 20000; i++) {
            wild += FishSpawns.pickIn(FishSpawns.SEA_WARM, r, false, false) == sh ? 1 : 0;
            caught += FishSpawns.pickIn(FishSpawns.SEA_WARM, r, false) == sh ? 1 : 0;
        }
        Gt.equal(0L, (long) wild, "jamais de requin gris en apparition de poisson");
        Gt.check(caught > 40 && caught < 600, "requin gris a la peche en mer chaude : rare (" + caught + " / 20000)");
        Gt.check(LiveRelease.liveFish(h.getLevel(), sh, 1F) instanceof fr.pioneers.world.wild.Shark, "relache : le vrai requin gris");
        s.done();
    }
}
