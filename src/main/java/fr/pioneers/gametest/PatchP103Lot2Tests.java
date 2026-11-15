package fr.pioneers.gametest;

import fr.pioneers.bronze.bench.BenchRecipe;
import fr.pioneers.geology.DepositTypes;
import fr.pioneers.geology.fluid.FluidDepositTypes;
import fr.pioneers.guide.JourneyProgress;
import fr.pioneers.guide.JourneySteps;
import fr.pioneers.guide.JourneyTracker;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.atelier.AtelierTile;
import fr.pioneers.network.PacketDeposits;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.transport.CableTier;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.3-beta, lot 2 (GameTests) : la voie du fer au MKI (broyeur, creuset electrique et machine de coulee MKI, 2 lingots par minerai,
 * sans circuit MKI ni moteur ni matrice de presse dans les recettes des trois machines), les gisements envoyes aux clients (paquet relu
 * a l'identique), la prochaine etape du Periple (regle commune), la table des etapes (progres, buts) et leur suivi par le serveur.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot2Tests {

    private PatchP103Lot2Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
        Gt.check(it != null && it != Items.AIR, "objet inconnu " + id);
        return it;
    }

    /** Machine d'atelier MKI posee en rel, energie pleine. */
    private static AtelierTile mk1(GameTestHelper h, BlockPos rel, IndustryMachine machine) {
        Block block = PiIndustry.ATELIERS.get(machine).get(CableTier.BASIC).getBlock();
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier pour " + machine.key);
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        return t;
    }

    private static int count(AtelierTile m, Item it) {
        int n = 0;
        for (var s : m.itemOutputs()) {
            if (s.getStack().is(it)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ================================================================== la voie du fer au MKI
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_iron", timeoutTicks = 2400)
    public static void p103_fer_mk1_deux_lingots(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        MinecraftServer server = h.getLevel().getServer();
        //broyeur du kit : 1 minerai brut -> 2 concasses
        Recipe<?> crush = server.getRecipeManager().byKey(rl("crushing/raw_iron")).orElse(null);
        Gt.check(crush != null, "recette crushing/raw_iron absente");
        ItemStack out = crush.getResultItem(server.registryAccess());
        Gt.check(out.is(item("crushed_iron")) && out.getCount() == 2, "broyeur : 1 minerai -> 2 concasses (" + out + ")");
        //les trois machines MKI : ni circuit MKI, ni moteur electrique, ni train epicycloidal, ni matrice de presse
        Item[] late = {item("circuit_mk1"), item("electric_motor"), item("planetary_gear"), item("press_die"), item("cast_iron_housing")};
        for (String r : List.of("machines/basic_crusher", "industry/crafting/basic_electric_crucible", "industry/crafting/basic_casting_machine")) {
            Recipe<?> rec = server.getRecipeManager().byKey(rl(r)).orElse(null);
            Gt.check(rec instanceof BenchRecipe, "recette " + r + " absente");
            for (BenchRecipe.Counted c : ((BenchRecipe) rec).ingredients()) {
                for (Item it : late) {
                    Gt.check(!c.ingredient().test(new ItemStack(it)), r + " demande " + it + " (arrive apres le premier courant)");
                }
            }
        }
        //creuset electrique MKI : 2 concasses -> 288 mB x 1,5 (1.0.3-beta lot 3 : multiplicateur du tier) ; machine de coulee MKI : lingots
        AtelierTile crucible = mk1(h, new BlockPos(2, 2, 2), IndustryMachine.ELECTRIC_CRUCIBLE);
        AtelierTile caster = mk1(h, new BlockPos(5, 2, 2), IndustryMachine.CASTING_MACHINE);
        Gt.check(crucible.setLockedRecipe(rl("industry/electric_crucible/melt_crushed_iron")), "fusion du concasse absente du creuset MKI");
        crucible.itemInputs().get(0).setStack(new ItemStack(item("crushed_iron"), 4));
        h.startSequence()
              .thenWaitUntil(() -> {
                  crucible.getEnergy().setStored(crucible.getEnergy().getCapacity());
                  int mb = crucible.fluidOutputs().get(0).getFluidAmount();
                  Gt.check(mb >= 864, "creuset MKI : " + mb + " / 864 mB (4 concasses = 2 minerais, x1,5 au MKI)");
              })
              .thenExecute(() -> {
                  Gt.check(caster.setLockedRecipe(rl("industry/casting_machine/cast_ingot_iron")), "coulee du fer absente de la machine MKI");
                  caster.fluidInputs().get(0).setFluid(crucible.fluidOutputs().get(0).getFluid().copy());
                  crucible.fluidOutputs().get(0).setFluid(FluidStack.EMPTY);
                  Gt.tool(caster, new ItemStack(item("ingot_die")));   // 1.0.5-beta (lot 44) : case Outil
              })
              .thenWaitUntil(() -> {
                  caster.getEnergy().setStored(caster.getEnergy().getCapacity());
                  int ingots = count(caster, item("iron_ingot")), nuggets = count(caster, item("iron_nugget"));
                  Gt.check(ingots >= 4, "coulee MKI : " + ingots + " lingots (attendu 4 + 9 pepites : 4 coulees de 180 mB sur 864 mB)");
                  Gt.check(ingots * 144 + nuggets * 16 <= 864, "aucune creation de metal");
              })
              .thenExecute(s::done);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_iron_melts", timeoutTicks = 20)
    public static void p103_creuset_fond_le_minerai(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        MinecraftServer server = h.getLevel().getServer();
        String[][] melts = {{"melt_raw_iron", "144"}, {"melt_crushed_iron", "144"}, {"melt_ore_dust_iron", "144"}, {"melt_fragment_magnetite", "36"},
              {"melt_fragment_hematite", "36"}, {"melt_crushed_copper", "144"}, {"melt_crushed_tin", "144"}, {"melt_crushed_lead", "144"},
              {"melt_crushed_zinc", "144"}, {"melt_crushed_gold", "144"}};
        for (String[] m : melts) {
            Gt.check(server.getRecipeManager().byKey(rl("industry/electric_crucible/" + m[0])).isPresent(), "recette " + m[0] + " absente");
        }
        Gt.check(server.getRecipeManager().byKey(rl("processes/iron/direct_smelting_2")).isEmpty(), "ancienne fonte de la voie du fer encore la");
        Gt.check(server.getRecipeManager().byKey(rl("processes/steel/induction_2")).isPresent(), "fonte de la voie de l'acier absente");
        s.done();
    }

    // ================================================================== gisements envoyes aux clients
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_deposits", timeoutTicks = 20)
    public static void p103_gisements_au_client(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int before = DepositTypes.all().size();
        Gt.check(before > 0, "aucun gisement charge");
        Gt.equal(before, DepositTypes.sources().size(), "un texte par gisement lu");
        PacketDeposits p = new PacketDeposits(DepositTypes.sources(), FluidDepositTypes.sources());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(buf);
        PacketDeposits back = PacketDeposits.decode(buf);
        Gt.equal(p.deposits(), back.deposits(), "gisements relus a l'identique");
        Gt.equal(p.fluids(), back.fluids(), "gisements fluides relus a l'identique");
        Set<ResourceLocation> ids = new HashSet<>();
        DepositTypes.all().forEach(t -> ids.add(t.id()));
        DepositTypes.acceptSynced(back.deposits());                    //ce que fait un client de serveur dedie
        Set<ResourceLocation> after = new HashSet<>();
        DepositTypes.all().forEach(t -> after.add(t.id()));
        Gt.equal(ids, after, "le client retrouve les memes gisements");
        s.done();
    }

    // ================================================================== Periple : regle de la prochaine etape
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_next", timeoutTicks = 20)
    public static void p103_periple_prochaine_etape(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int n = 20;
        Gt.equal(0, JourneyProgress.next(n, i -> false), "rien de fait : etape 1");
        Gt.equal(5, JourneyProgress.next(n, i -> i < 5), "etapes 1 a 5 faites : etape 6");
        //dans le desordre, a moins de PASS places : la prochaine reste l'etape oubliee (jamais de saut)
        Gt.equal(5, JourneyProgress.next(n, i -> i < 5 || i == 5 + JourneyProgress.PASS - 1), "etape un peu plus loin faite : on reste");
        //une etape PASS places plus loin faite : l'oubliee est depassee (elle reste a faire dans le guide)
        Gt.equal(6, JourneyProgress.next(n, i -> i < 5 || i == 5 + JourneyProgress.PASS), "etape nettement depassee");
        Gt.check(JourneyProgress.passed(n, i -> i < 5 || i == 5 + JourneyProgress.PASS, 5), "depassee");
        Gt.equal(-1, JourneyProgress.next(n, i -> true), "tout fait");
        Gt.equal(List.of(5, 7, 8), JourneyProgress.upcoming(n, i -> i < 5 || i == 6, 3), "trois prochaines etapes (la 7e est faite)");
        //progression reguliere : jamais en arriere
        int prev = -1;
        for (int k = 0; k <= n; k++) {
            final int done = k;
            int next = JourneyProgress.next(n, i -> i < done);
            int idx = next < 0 ? n : next;
            Gt.check(idx >= prev, "prochaine etape en arriere");
            prev = idx;
        }
        s.done();
    }

    // ================================================================== Periple : table des etapes et suivi par le serveur
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_steps", timeoutTicks = 20)
    public static void p103_periple_table(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        MinecraftServer server = h.getLevel().getServer();
        Gt.check(JourneySteps.ALL.size() >= 60, "Periple : " + JourneySteps.ALL.size() + " etapes");
        Set<String> seen = new HashSet<>();
        for (JourneySteps.Step st : JourneySteps.ALL) {
            Gt.check(seen.add(st.advancement()), "etape en double " + st.advancement());
            Gt.check(server.getAdvancements().getAdvancement(ResourceLocation.parse(st.advancement())) != null, "progres absent " + st.advancement());
            if (st.byEvent()) {
                Gt.check(server.getAdvancements().getAdvancement(ResourceLocation.parse(st.legacy())) != null, "progres d'evenement absent " + st.legacy());
            }
            Gt.check(!st.goal().isEmpty(), st.advancement() + " : pas de but");
            Gt.check(!JourneyTracker.goal(st).isEmpty(), st.advancement() + " : objets du but inconnus");
        }
        s.done();
    }

    private static boolean done(ServerPlayer p, String path) {
        Advancement a = p.getServer().getAdvancements().getAdvancement(rl(path));
        Gt.check(a != null, "progres absent " + path);
        return p.getAdvancements().getOrStartProgress(a).isDone();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_tracker", timeoutTicks = 40)
    public static void p103_periple_suivi(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p103", "Ada", new BlockPos(4, 2, 4));
        p.getInventory().clearContent();
        JourneyTracker.check(p);
        Gt.check(!done(p, "journey/j_primitive_01"), "fibres : pas encore (inventaire vide)");
        p.getInventory().add(new ItemStack(item("plant_fiber")));
        Gt.check(JourneyTracker.check(p) >= 1 && done(p, "journey/j_primitive_01"), "fibre dans l'inventaire : etape 1 faite");
        Gt.check(!done(p, "journey/j_primitive_02"), "etape 2 (tamis) pas faite pour autant");
        p.getInventory().clearContent();
        JourneyTracker.check(p);
        Gt.check(done(p, "journey/j_primitive_01"), "une etape faite le reste (objet consomme)");
        //etape par evenement : son progres d'evenement fait la coche, pas l'objet
        JourneySteps.Step cast = JourneySteps.ALL.stream().filter(st -> st.byEvent() && st.chapter().equals("j_primitive")).findFirst().orElse(null);
        Gt.check(cast != null, "pas d'etape par evenement au primitif");
        p.getInventory().add(new ItemStack(item(cast.goal().get(0))));
        JourneyTracker.check(p);
        Gt.check(!JourneyTracker.done(p, cast), cast.advancement() + " : l'objet seul ne suffit pas (evenement exact)");
        Advancement legacy = p.getServer().getAdvancements().getAdvancement(ResourceLocation.parse(cast.legacy()));
        AdvancementProgress pr = p.getAdvancements().getOrStartProgress(legacy);
        for (String c : pr.getRemainingCriteria()) {
            p.getAdvancements().award(legacy, c);
        }
        JourneyTracker.check(p);
        Gt.check(JourneyTracker.done(p, cast), cast.advancement() + " : faite par son progres d'evenement");
        s.done();
    }
}
