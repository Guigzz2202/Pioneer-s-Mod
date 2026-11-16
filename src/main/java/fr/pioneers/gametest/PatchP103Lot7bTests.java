package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.BronzeBlock;
import fr.pioneers.tech.bronze.autofoundry.AutoFoundryTile;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldTile;
import fr.pioneers.tech.bronze.autofoundry.CastingChannelBlock;
import fr.pioneers.tech.bronze.autofoundry.FoundryLayout;
import fr.pioneers.tech.bronze.autofoundry.FoundryUnit;
import fr.pioneers.tech.bronze.foundry.CastingTableTile;
import fr.pioneers.tech.bronze.foundry.CrucibleFurnaceTile;
import fr.pioneers.tech.bronze.foundry.CrucibleUnit;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.MoltenLoad;
import fr.pioneers.tech.forge.casting.ClayCrucibleTile;
import fr.pioneers.tech.forge.casting.CrucibleTongsItem;
import fr.pioneers.tech.forge.casting.PlacedMoldBlock;
import fr.pioneers.tech.forge.casting.PlacedMoldTile;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiForge;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.3-beta (lot 7b, Guillaume : « beaucoup de joueurs ont bloque leur forge a cause de mauvais alliage ») : AUCUN BAIN BLOQUE. Chaque
 * scenario de blocage de l'inventaire du lot (une pepite ou un lingot en trop, deux minerais verses sans filtre, un lingot d'un autre metal,
 * deux alliages possibles, un bain d'avant 1.0.3, un verrou pose sur un bain plein, un filtre sur un metal pur) se coule maintenant, et chaque
 * test verifie la CONSERVATION EXACTE des mB (rien de perdu, rien de duplique). Regle commune : {@link Metals#plan} / {@link Metals#pour}.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot7bTests {

    private PatchP103Lot7bTests() {
    }

    private static Map<String, Integer> bath(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            Metals.addTo(m, (String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    private static int total(Map<String, Integer> m) {
        return m.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Coule tant qu'un moule de {@code need} mB se remplit ; verifie chaque coulee (quantite, fourchettes, conservation) ; renvoie ce qui sort. */
    private static Map<String, Integer> pourAll(Map<String, Integer> b, String locked, int need, String label) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int before = total(b);
        for (int guard = 0; guard < 2000; guard++) {
            Metals.Plan plan = Metals.plan(b, locked, false, m -> need);
            if (!plan.pours()) {
                break;
            }
            int was = total(b);
            Map<String, Integer> poured = Metals.pour(b, plan.metal(), need);
            Gt.equal(need, total(poured), label + " : quantite coulee");
            Gt.equal(was, total(b) + total(poured), label + " : conservation d'une coulee");
            if (Metals.isAlloy(plan.metal())) {
                Gt.equal(plan.metal(), Metals.resolve(poured), label + " : part coulee dans ses fourchettes " + poured);
            } else {
                Gt.check(poured.size() == 1 && poured.containsKey(plan.metal()), label + " : part pure d'un seul metal " + poured);
            }
            out.merge(plan.metal(), need, Integer::sum);
        }
        Gt.equal(before, total(out) + total(b), label + " : conservation totale");
        return out;
    }

    // ================================================================== regle commune : chaque scenario de blocage d'avant
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_scenarios", timeoutTicks = 20)
    public static void p103_7b_scenarios_de_blocage(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //Fourchettes centrees sur le rapport de recette (plus de tolerance de 0,5 %)
        Gt.equal("bronze", Metals.resolve(bath("copper", 432, "tin", 144)), "3:1 = bronze, au milieu de la fourchette");
        Gt.equal(108, (long) bath("bronze", 144).get("copper"), "un lingot de bronze refondu = 108 cuivre + 36 etain");
        //Une pepite d'etain en trop : toujours du bronze (72,9 % de cuivre), tout coule
        Map<String, Integer> b = bath("copper", 432, "tin", 160);
        Gt.equal("bronze", Metals.resolve(b), "pepite en trop : toujours du bronze");
        Map<String, Integer> out = pourAll(b, "", 144, "pepite en trop");
        Gt.equal(576, (long) out.getOrDefault("bronze", 0), "4 lingots de bronze");
        Gt.equal(16, total(b), "16 mB restent (une pepite de bronze)");
        //Un lingot d'etain en trop : 4 lingots de bronze puis 1 d'etain, rien ne reste
        b = bath("copper", 432, "tin", 288);
        out = pourAll(b, "", 144, "lingot en trop");
        Gt.check(out.getOrDefault("bronze", 0) == 576 && out.getOrDefault("tin", 0) == 144 && b.isEmpty(), "lingot en trop : 4 bronze + 1 etain " + out);
        //Deux minerais verses sans filtre (2 cuivre + 2 etain) : le bronze au rapport, puis l'etain en trop
        b = bath("copper", 432, "tin", 432);
        out = pourAll(b, "", 144, "minerais sans filtre");
        Gt.check(out.getOrDefault("bronze", 0) == 576 && out.getOrDefault("tin", 0) == 288 && b.isEmpty(), "2 + 2 minerais : 4 bronze + 2 etain " + out);
        //Un lingot d'un autre metal : le bronze coule, le zinc coule seul ensuite
        b = bath("copper", 432, "tin", 144, "zinc", 144);
        out = pourAll(b, "", 144, "metal etranger");
        Gt.check(b.isEmpty() && total(out) == 720, "metal etranger : tout coule " + out);
        //Deux alliages possibles : l'un puis l'autre, chacun au rapport ideal
        b = bath("copper", 864, "tin", 144, "zinc", 144);
        out = pourAll(b, "", 144, "deux alliages");
        Gt.check(out.getOrDefault("bronze", 0) == 576 && out.getOrDefault("brass", 0) == 576 && b.isEmpty(), "bronze puis laiton " + out);
        //Bain d'avant 1.0.3 (83 / 17 %, dans l'ancienne fourchette 75-92, hors de la nouvelle 70-80)
        b = bath("copper", 1075, "tin", 221);
        out = pourAll(b, "", 144, "bain d'avant");
        Gt.check(out.getOrDefault("bronze", 0) >= 864, "bain d'avant : 6 lingots de bronze au moins " + out);
        Gt.check(Metals.portions(b).stream().allMatch(p -> p.mb() < 144), "bain d'avant : plus rien d'un lingot " + b);
        //Filtre bronze : seul le bronze coule, l'etain reste, le plan attend du cuivre
        b = bath("copper", 432, "tin", 288);
        out = pourAll(b, "bronze", 144, "filtre bronze");
        Gt.check(out.getOrDefault("bronze", 0) == 576 && b.equals(Map.of("tin", 144)), "filtre bronze : l'etain reste " + b);
        Metals.Plan wait = Metals.plan(b, "bronze", false, m -> 144);
        Gt.check(!wait.pours() && wait.reason() == Metals.WHY_WAITING, "le filtre attend");
        Gt.equal("copper", wait.waiting(), "il attend du cuivre");
        //Filtre sur un metal pur dans un bain melange : sa part libre ; tout pris : la raison
        Metals.Plan free = Metals.plan(bath("copper", 720, "tin", 144), "copper", false, m -> 144);
        Gt.check(free.pours() && free.mb() == 288, "filtre cuivre : 288 mB de cuivre libre " + free);
        Gt.equal(Metals.WHY_BOUND, (long) Metals.plan(bath("copper", 432, "tin", 144), "tin", false, m -> 144).reason(), "filtre etain sur du bronze : raison");
        //Verrou pose sur un bain plein d'un autre metal : trop-plein
        Gt.check(Metals.plan(bath("copper", 1296), "bronze", true, m -> 144).pours(), "trop-plein : le cuivre coule");
        //Soudure 3:2
        b = bath("tin", 432, "lead", 288);
        out = pourAll(b, "solder", 144, "soudure");
        Gt.check(out.getOrDefault("solder", 0) == 720 && b.isEmpty(), "soudure 3:2 : 5 lingots " + out);
        //Textes de l'interface
        Metals.Plan plan = Metals.plan(bath("copper", 432, "tin", 288), "", false, m -> 144);
        Gt.equal("gui.pioneers.bath.pours", key(Metals.pourLine(plan)), "ligne « Coule : ... »");
        Gt.equal("gui.pioneers.bath.rest", key(Metals.restLine(plan)), "ligne « Reste : ... »");
        Gt.equal(Map.of("tin", 144), plan.rest(), "reste : etain 144 mB");
        s.done();
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    // ================================================================== conservation exacte sur 3 000 bains tires au hasard
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_hasard", timeoutTicks = 40)
    public static void p103_7b_conservation_au_hasard(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Random r = new Random(11);
        String[] metals = {"copper", "tin", "zinc", "lead", "iron", "gold", "bronze", "brass", "solder"};
        int[] sizes = {16, 36, 144, 216};
        String[] locks = {"", "bronze", "brass", "solder", "copper", "tin"};
        int[] needs = {16, 144, 1296};
        for (int i = 0; i < 3000; i++) {
            Map<String, Integer> b = new LinkedHashMap<>();
            for (int k = 1 + r.nextInt(10); k > 0; k--) {
                Metals.addTo(b, metals[r.nextInt(metals.length)], sizes[r.nextInt(sizes.length)]);
            }
            String lock = locks[r.nextInt(locks.length)];
            int need = needs[r.nextInt(needs.length)];
            Gt.equal(total(b), Metals.portions(b).stream().mapToInt(Metals.Portion::mb).sum(), "decomposition exacte " + b);
            pourAll(b, lock, need, "bain " + i);
            pourAll(b, "", need, "bain " + i + " sans filtre");
            for (Metals.Portion p : Metals.portions(b)) {
                Gt.check(p.mb() < need, "bain " + i + " bloque : " + p);
            }
        }
        s.done();
    }

    // ================================================================== dosage pepite a pepite : le milieu, pas les bornes
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_dosage", timeoutTicks = 20)
    public static void p103_7b_dosage_au_milieu(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (String alloy : List.of("bronze", "brass", "solder")) {
            Map<String, Integer> w = Metals.weights(alloy);
            List<String> comps = new ArrayList<>(w.keySet());
            Map<String, Integer> d = new LinkedHashMap<>();
            for (int i = 0; i < 400 && total(d) < 1296; i++) {
                Map<String, Integer> add = Map.of(comps.get(i % comps.size()), 16);
                if (Metals.dosed(d, alloy, add)) {
                    Metals.addTo(d, add.keySet().iterator().next(), 16);
                }
            }
            String first = comps.get(0);
            int sum = w.values().stream().mapToInt(Integer::intValue).sum();
            double ideal = w.get(first) * 100.0 / sum, pct = d.getOrDefault(first, 0) * 100.0 / Math.max(1, total(d));
            Gt.check(Math.abs(pct - ideal) <= 3, alloy + " dose a " + Math.round(pct) + " % (ideal " + Math.round(ideal) + ")");
            Gt.equal(alloy, Metals.resolve(d), alloy + " dose : dans ses fourchettes, loin des bornes");
        }
        s.done();
    }

    // ================================================================== fourneau a creuset
    private static final BlockPos FURNACE = new BlockPos(3, 2, 4), TABLE = new BlockPos(4, 2, 4);

    private static CrucibleFurnaceTile furnace(GameTestHelper h, boolean table) {
        h.setBlock(FURNACE, PiBronze.CRUCIBLE_FURNACE.getBlock().defaultBlockState().setValue(BronzeBlock.FACING, Direction.EAST));
        CrucibleFurnaceTile f = (CrucibleFurnaceTile) h.getBlockEntity(FURNACE);
        Gt.check(f != null, "fourneau pose");
        f.guiSlots().get(0).setStack(new ItemStack(PiBronze.CRUCIBLE.get()));
        if (table) {
            h.setBlock(TABLE, PiBronze.CASTING_TABLE.getBlock());
            CastingTableTile t = (CastingTableTile) h.getBlockEntity(TABLE);
            t.moldSlot().setStack(new ItemStack(PiBronze.MOLDS.get("ingot").get()));
            Gt.check(f.table() == t, "table raccordee");
        }
        return f;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation("pioneers", id));
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_fourneau_trop", timeoutTicks = 100)
    public static void p103_7b_fourneau_lingot_en_trop(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        CrucibleFurnaceTile f = furnace(h, true);
        Metals.addTo(f.mix(), "copper", 432);
        Metals.addTo(f.mix(), "tin", 288);
        f.setTemperature(1400);
        Metals.Plan plan = f.plan();
        Gt.check(plan.pours() && "bronze".equals(plan.metal()) && plan.mb() == 576, "plan : bronze 4 lingots " + plan);
        Gt.equal(Map.of("tin", 144), plan.rest(), "plan : reste etain 144 mB");
        h.runAfterDelay(40, () -> {
            CastingTableTile t = (CastingTableTile) h.getBlockEntity(TABLE);
            Gt.equal("bronze", t.metal(), "la table recoit du bronze (avant : rien, « melange sans alliage »)");
            Gt.equal(720, (long) f.total() + t.amount(), "conservation : bain + table = 720 mB");
            Gt.equal(324, (long) f.mix().getOrDefault("copper", 0), "cuivre du bain apres un lingot de bronze (432 - 108)");
            Gt.equal(252, (long) f.mix().getOrDefault("tin", 0), "l'etain en trop reste dans le bain (288 - 36)");
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_fourneau_filtre", timeoutTicks = 400)
    public static void p103_7b_fourneau_filtre_minerais(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        CrucibleFurnaceTile f = furnace(h, false);
        Gt.check(f.setLockedRecipe(CrucibleUnit.choiceId("bronze")), "filtre bronze");
        ItemStack copper = new ItemStack(item("raw_copper"), 6), tin = new ItemStack(item("raw_tin"), 6);
        int unit = ForgeMetals.meltOf(copper).mb();
        //Entonnoirs, cables, robots : un metal par case (4 cases de cuivre et l'etain ne passait plus)
        Gt.check(f.guiSlots().get(2).insert(copper.copy(), Action.EXECUTE, Automation.EXTERNAL).isEmpty(), "cuivre par l'entonnoir");
        Gt.check(!f.guiSlots().get(3).insert(copper.copy(), Action.SIMULATE, Automation.EXTERNAL).isEmpty(), "pas de cuivre dans une 2e case");
        Gt.check(f.guiSlots().get(3).insert(tin.copy(), Action.EXECUTE, Automation.EXTERNAL).isEmpty(), "l'etain trouve sa case");
        Gt.check(!f.guiSlots().get(4).insert(new ItemStack(item("raw_zinc"), 1), Action.SIMULATE, Automation.EXTERNAL).isEmpty(), "zinc refuse (filtre)");
        int[] t = {0};
        h.onEachTick(() -> {
            f.setTemperature(1400);
            t[0]++;
        });
        h.runAfterDelay(300, () -> {
            int left = f.guiSlots().get(2).getStack().getCount() + f.guiSlots().get(3).getStack().getCount();
            Gt.equal(12L * unit, (long) f.total() + (long) left * unit, "conservation : bain + minerais restants");
            Gt.check(left > 0, "ce qui ne sert pas reste dans sa case (jamais detruit)");
            int cu = f.mix().getOrDefault("copper", 0), sn = f.mix().getOrDefault("tin", 0);
            Gt.check(Math.abs(cu / 3.0 - sn) <= unit, "dosage au rapport 3:1 a un minerai pres : " + f.mix());
            Gt.check(f.plan().pours() && "bronze".equals(f.plan().metal()), "le bain coulera du bronze " + f.plan());
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_fourneau_avant", timeoutTicks = 100)
    public static void p103_7b_fourneau_bain_davant(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        CrucibleFurnaceTile f = furnace(h, true);
        //Sauvegarde d'avant : bain 83 / 17 (hors des nouvelles fourchettes) + un lingot de zinc ; il etait bloque (« melange sans alliage »)
        CompoundTag tag = f.saveWithoutMetadata();
        CompoundTag mix = new CompoundTag();
        mix.putInt("copper", 1075);
        mix.putInt("tin", 221);
        tag.put("mix", mix);
        f.load(tag);
        Gt.equal(1296, (long) f.total(), "bain d'avant relu");
        Gt.equal("", Metals.resolve(f.mix()), "plus exactement un alliage");
        f.setTemperature(1400);
        Gt.check(f.plan().pours(), "le bain d'avant se coule");
        h.runAfterDelay(40, () -> {
            CastingTableTile t = (CastingTableTile) h.getBlockEntity(TABLE);
            Gt.equal("bronze", t.metal(), "bronze coule");
            Gt.equal(1296, (long) f.total() + t.amount(), "aucune perte");
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_fourneau_plein", timeoutTicks = 100)
    public static void p103_7b_fourneau_trop_plein(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        CrucibleFurnaceTile f = furnace(h, true);
        Metals.addTo(f.mix(), "copper", CrucibleFurnaceTile.CAPACITY);
        Gt.check(f.setLockedRecipe(CrucibleUnit.choiceId("bronze")), "filtre bronze pose sur un bain plein de cuivre");
        f.guiSlots().get(2).setStack(new ItemStack(item("raw_tin"), 2));
        Gt.check(f.furnace().overflow(), "trop-plein : l'etain ne tient plus");
        f.setTemperature(1400);
        h.runAfterDelay(40, () -> {
            CastingTableTile t = (CastingTableTile) h.getBlockEntity(TABLE);
            Gt.equal("copper", t.metal(), "le surplus de cuivre coule (avant : fourneau bloque)");
            Gt.equal(CrucibleFurnaceTile.CAPACITY, (long) f.total() + t.amount() - (f.mix().getOrDefault("tin", 0)), "conservation");
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_vider", timeoutTicks = 20)
    public static void p103_7b_vider_le_bain(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        CrucibleFurnaceTile f = furnace(h, false);
        Metals.addTo(f.mix(), "copper", 200);
        Metals.addTo(f.mix(), "tin", 40);
        Metals.addTo(f.mix(), "zinc", 3);
        List<ItemStack> out = f.furnace().dump();
        Gt.check(f.mix().isEmpty(), "bain vide apres « Vider »");
        int mb = 0, slag = 0;
        for (ItemStack st : out) {
            var melt = ForgeMetals.meltOf(st);
            if (st.is(PiBronze.SLAG.get())) {
                slag += st.getCount();
            } else {
                Gt.check(melt != null, "objet rendu fondable : " + st);
                mb += melt.mb() * st.getCount();
            }
        }
        //decomposition : bronze 160 (120 + 40) = 1 lingot + 1 pepite, cuivre 80 = 5 pepites, zinc 3 : reste -> une scorie
        Gt.equal(240, mb, "lingots et pepites rendus " + out);
        Gt.check(out.stream().anyMatch(st -> st.is(ForgeMetals.ingot("bronze", 1).getItem())), "le bronze revient en lingot de bronze " + out);
        Gt.equal(1, slag, "les restes de moins d'une pepite : une scorie");
        //Casse : le bain est rendu (il etait perdu)
        Metals.addTo(f.mix(), "bronze", 288);
        List<ItemStack> drops = new ArrayList<>();
        f.dropContents(drops::add);
        Gt.check(drops.stream().anyMatch(st -> st.is(ForgeMetals.ingot("bronze", 1).getItem()) && st.getCount() == 2), "2 lingots de bronze rendus a la casse " + drops);
        s.done();
    }

    // ================================================================== fonderie automatique : deux alliages dans la meme cuve
    private static AutoFoundryTile foundry(GameTestHelper h, BlockPos rel) {
        BlockPos c = h.absolutePos(rel);
        for (FoundryLayout.Cell cell : FoundryLayout.cells(FoundryLayout.Size.SMALL, FoundryLayout.defaultOffset(FoundryLayout.Size.SMALL))) {
            BlockPos p = FoundryLayout.at(c, Direction.SOUTH, cell.x(), cell.y(), cell.z());
            var state = FoundryLayout.block(cell.kind()).defaultBlockState();
            if (cell.kind() == FoundryLayout.Kind.CONTROLLER) {
                state = state.setValue(BronzeBlock.FACING, Direction.SOUTH);
            }
            h.getLevel().setBlock(p, state, 3);
        }
        return (AutoFoundryTile) h.getLevel().getBlockEntity(c);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_fonderie", timeoutTicks = 400)
    public static void p103_7b_fonderie_deux_alliages(GameTestHelper h) {
        BlockPos door = new BlockPos(1, 1, 4);
        AutoFoundryTile f = foundry(h, door);
        AutoMoldTile[] molds = new AutoMoldTile[4];
        for (int i = 0; i < 4; i++) {
            h.setBlock(new BlockPos(2 + i, 1, 4), PiBronze.AUTO_MOLD.getBlock().defaultBlockState());
            molds[i] = (AutoMoldTile) h.getBlockEntity(new BlockPos(2 + i, 1, 4));
        }
        for (int i = 0; i < 4; i++) {
            BlockPos at = new BlockPos(2 + i, 2, 4);
            h.setBlock(at, PiBronze.CASTING_CHANNEL.getBlock().defaultBlockState());
            var st = h.getBlockState(at);
            h.setBlock(at, ((CastingChannelBlock) st.getBlock()).connected(st, h.getLevel(), h.absolutePos(at)));
        }
        Gt.check(f.house().checkNow(), "fonderie formee");
        FoundryUnit u = f.foundry();
        //2 lingots de bronze et 2 de laiton fondus ensemble (cuivre 432 + etain 72 + zinc 72) : avant, « melange sans alliage », rien ne coulait
        Metals.addTo(u.mix(), "copper", 432);
        Metals.addTo(u.mix(), "tin", 72);
        Metals.addTo(u.mix(), "zinc", 72);
        Gt.equal("", Metals.resolve(u.mix()), "aucun alliage exact");
        //Ce que chaque moule a recu (le moule fige puis se vide : on garde le plus haut niveau vu et son metal)
        String[] metal = new String[4];
        int[] most = new int[4];
        h.onEachTick(() -> {
            u.setTemperature(1400);
            for (int i = 0; i < 4; i++) {
                if (molds[i].mold().amount() > most[i]) {
                    most[i] = molds[i].mold().amount();
                    metal[i] = molds[i].mold().metal();
                }
            }
            int poured = 0;
            for (int m : most) {
                poured += m;
            }
            Gt.check(poured + u.total() + u.pourLeft() <= 576, "jamais plus de metal que le bain n'en avait");
        });
        h.succeedWhen(() -> {
            int bronze = 0, brass = 0, poured = 0;
            for (int i = 0; i < 4; i++) {
                poured += most[i];
                bronze += "bronze".equals(metal[i]) ? 1 : 0;
                brass += "brass".equals(metal[i]) ? 1 : 0;
            }
            Gt.check(!u.pouring() && u.total() == 0, "cuve videe " + u.mix());
            Gt.equal(576, poured, "conservation : 576 mB dans les moules");
            Gt.check(bronze == 2 && brass == 2, "2 moules de bronze et 2 de laiton : " + bronze + " / " + brass);
        });
    }


    // ================================================================== creuset en argile : pince et « Vider »
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7b_argile", timeoutTicks = 20)
    public static void p103_7b_creuset_argile(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        BlockPos support = new BlockPos(4, 1, 4);
        h.setBlock(support, Blocks.STONE);
        Gt.check(PlacedMoldBlock.place(level, h.absolutePos(support), Direction.SOUTH, new ItemStack(PiBronze.MOLDS.get("ingot").get())), "moule pose");
        PlacedMoldTile mold = (PlacedMoldTile) h.getBlockEntity(support.above());
        //Petite dose ratee : 6 fragments de cuivre + 3 d'etain (une de trop) = 216 + 108 mB, plus que les 288 mB du creuset d'avant
        MoltenLoad load = new MoltenLoad();
        load.add("copper", 216);
        load.add("tin", 108);
        load.setTemperature(1300, level.getGameTime());
        Gt.equal("", load.metal(), "pas exactement du bronze");
        ItemStack tongs = new ItemStack(PiForge.CRUCIBLE_TONGS.get());
        CrucibleTongsItem.setCrucible(tongs, new ItemStack(PiForge.CLAY_CRUCIBLE.getBlock()), load);
        Player player = h.makeMockPlayer();
        CrucibleTongsItem.pour(level, player, InteractionHand.MAIN_HAND, tongs, mold);
        MoltenLoad after = CrucibleTongsItem.load(tongs);
        Gt.equal("bronze", mold.metal(), "la pince verse le bronze d'abord (avant : refuse, « melange »)");
        Gt.equal(324, (long) mold.amount() + after.total(), "conservation pince + moule");
        //« Vider » : Maj + clic main vide sur le creuset pose (ici : la sortie de secours du contenu)
        List<ItemStack> spilled = after.spill();
        Gt.check(after.isEmpty() && !spilled.isEmpty(), "contenu rendu en lingots / pepites");
        //Creuset pose avec un bain melange casse : rien de perdu
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, PiForge.CLAY_CRUCIBLE.getBlock());
        ClayCrucibleTile c = (ClayCrucibleTile) h.getBlockEntity(at);
        MoltenLoad mixed = new MoltenLoad();
        mixed.add("copper", 144);
        mixed.add("zinc", 144);
        c.setCrucible(new ItemStack(PiForge.CLAY_CRUCIBLE.getBlock()), mixed);
        List<ItemStack> drops = new ArrayList<>();
        c.dropContents(drops::add);
        int back = 0;
        for (ItemStack st : drops) {
            var melt = ForgeMetals.meltOf(st);
            back += melt == null || st.is(PiForge.CLAY_CRUCIBLE.getBlock().asItem()) ? 0 : melt.mb() * st.getCount();
        }
        Gt.equal(288, back, "le bain melange est rendu a la casse, sans perte (avant : perdu) " + drops);
        s.done();
    }
}
