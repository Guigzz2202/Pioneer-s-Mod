package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.MachineRegistry;
import fr.pioneers.api.machine.PowerRules;
import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.api.machine.tier.TierRecipes;
import fr.pioneers.api.machine.tier.TierRecipes.TierRecipe;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 38 (retours de jeu 5e serie, 3 : familles de tiers et machines a vapeur ; specification
 * docs/specification-age-bronze-1.0.5.md §2, §3 ; GameTests) :
 * <ul>
 *     <li>FAMILLES : la machine T1 (broyeur a vapeur) fait les recettes de tier minimal 0 et 1 de sa famille, jamais une recette du MKI
 *     (refusee a la case d'entree) ; minerai brut -> 3 concasses (recette du concasseur x 1,5), deux fois plus vite ;</li>
 *     <li>RENDEMENT : vapeur > mecanique sur les matieres premieres (concasses, planches), MKI > vapeur hors plafond (planches x 1,75) ;
 *     recettes du kit dominees retirees du broyeur MKI ;</li>
 *     <li>MARTEAU-PILON : bloc et objet remappes vers la scie a vapeur, type de tile vers {@code steam_process} ; un marteau-pilon d'un
 *     monde existant relu : ses materiaux et sa piece sortent au sol (gardes dans la sauvegarde avant), verrou efface, rien de perdu ;</li>
 *     <li>ANCIENS FORMATS : compacteur a 1 entree (sorties decalees), presse d'etabli ;</li>
 *     <li>PRESSE : elle aplatit (lingot + matrice a plaque -> plaque) ; TOUR : lingot d'acier -> 2 tiges ;</li>
 *     <li>TIERS : tier global des blocs (T0 meule, T1 machines a vapeur, T2..T5 MKI..MKIV), famille.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot38Tests {

    private PatchP105Lot38Tests() {
    }

    static ResourceLocation rl(String path) {
        return Pioneers.rl(path);
    }

    static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
        Gt.check(it != null && it != Items.AIR, "objet inconnu " + id);
        return it;
    }

    static SteamProcessTile place(GameTestHelper h, BlockPos at, SteamMachine m) {
        h.setBlock(at, PiBronze.STEAM_MACHINES.get(m).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(at) instanceof SteamProcessTile, "machine a vapeur posee : " + m.id());
        return (SteamProcessTile) h.getBlockEntity(at);
    }

    static int count(List<fr.pioneers.core.inventory.ItemSlot> slots, Item item) {
        int n = 0;
        for (var s : slots) {
            if (s.getStack().is(item)) {
                n += s.getStack().getCount();
            }
        }
        return n;
    }

    static int ground(GameTestHelper h, BlockPos at, Item item) {
        int n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(at)).inflate(3), e -> e.getItem().is(item))) {
            n += e.getItem().getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ 1. T1 : recettes de m <= 1, refus du MKI

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_38_family", timeoutTicks = 20)
    public static void p105_38_t1_famille(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        List<TierRecipe> family = TierRecipes.of(s.level, "broyage");
        Gt.check(!family.isEmpty(), "famille broyage vide");
        Gt.check(family.stream().allMatch(r -> r.minTier() <= 1), "machine T1 : seulement des recettes de tier minimal 0 ou 1");
        Gt.check(family.stream().anyMatch(r -> r.minTier() == 0) && family.stream().anyMatch(r -> r.minTier() == 1), "T1 : recettes m 0 ET m 1");
        //une entree que seul le broyeur MKI sait faire : refusee par le broyeur a vapeur
        SteamProcessTile mac = place(h, new BlockPos(2, 1, 2), SteamMachine.MACERATOR);
        ItemStack mki = ItemStack.EMPTY;
        for (IndustrialRecipe r : IndustryRecipes.of(s.level, IndustryMachine.CRUSHER)) {
            if (r.converted() || r.items().size() != 1) {
                continue;
            }
            for (ItemStack st : r.items().get(0).ingredient().getItems()) {
                if (!st.isEmpty() && !TierRecipes.accepts(s.level, "broyage", st)) {
                    mki = st.copyWithCount(1);
                    break;
                }
            }
            if (!mki.isEmpty()) {
                break;
            }
        }
        Gt.check(!mki.isEmpty(), "une recette propre au broyeur MKI (tier 2)");
        ItemStack rest = mac.inputs().get(0).insert(mki.copy(), Action.SIMULATE, Automation.EXTERNAL);
        Gt.check(rest.getCount() == mki.getCount(), "broyeur a vapeur : recette du MKI refusee (" + mki + ")");
        //minerai brut de fer : recette du concasseur (m 0) x 1,5 -> 3 concasses, en la moitie du temps du concasseur
        Item raw = item("minecraft:raw_iron"), crushed = item("crushed_iron");
        mac.inputs().get(0).setStack(new ItemStack(raw, 2));
        Gt.check(mac.process().craftNow(), "broyeur a vapeur : minerai brut de fer broye");
        Gt.equal(1, mac.inputs().get(0).getStack().getCount(), "un minerai pris");
        Gt.equal(3, count(mac.guiSlots(), crushed), "minerai brut -> 3 concasses au tier 1");
        TierRecipe jaw = TierRecipes.byId(s.level, "broyage", rl("materials/jaw_crushing_crushed_iron"));
        Gt.check(jaw != null && jaw.minTier() == 0, "recette du concasseur dans la famille");
        Gt.equal(jaw.duration(0) / 2, jaw.duration(1), "deux fois plus vite au tier 1");
        Gt.equal(PowerRules.globalDuration(jaw.time(), 2, 0), jaw.duration(2), "regle des durees (÷ 2 par tier)");
        s.done();
    }

    // ------------------------------------------------------------------ 2. rendement : vapeur > mecanique ; MKI

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_38_yield", timeoutTicks = 20)
    public static void p105_38_rendement(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        TierRecipe jaw = TierRecipes.byId(s.level, "broyage", rl("materials/jaw_crushing_crushed_iron"));
        Gt.check(jaw != null && jaw.yield() && jaw.capped(), "concasses : recette a rendement plafonnee a T1 (regle 1)");
        Gt.check(jaw.expectedMain() * jaw.yieldFactor(1) > jaw.expectedMain() * jaw.yieldFactor(0), "vapeur > mecanique (concasses)");
        Gt.check(Math.abs(jaw.expectedMain() * jaw.yieldFactor(5) - 3) < 1e-9, "plafond a T1 : 3 concasses meme au MKIV");
        TierRecipe log = TierRecipes.byId(s.level, "sciage", rl("mechanical/sawing/oak_log"));
        Gt.check(log != null && log.yield() && !log.capped(), "bucheron : recette a rendement");
        Gt.check(Math.abs(log.expectedMain() * log.yieldFactor(1) - 9) < 1e-9, "scie a vapeur : buche -> 9 planches");
        Gt.check(Math.abs(log.yieldFactor(2) - 1.75) < 1e-9 && Math.abs(log.yieldFactor(5) - 3.5) < 1e-9, "table R (1,75 au MKI, 3,5 au MKIV)");
        //a la vapeur pour de vrai : une buche de chene -> 9 planches (+ sciure)
        SteamProcessTile saw = place(h, new BlockPos(2, 1, 2), SteamMachine.SAW);
        saw.inputs().get(0).setStack(new ItemStack(Items.OAK_LOG));
        Gt.check(saw.process().craftNow(), "scie a vapeur : buche sciee");
        Gt.equal(9, count(saw.guiSlots(), Items.OAK_PLANKS), "scie a vapeur : 9 planches");
        //MKI : recettes basses converties, recette du kit dominee retiree
        IndustrialRecipe conv = null;
        for (IndustrialRecipe r : IndustryRecipes.of(s.level, IndustryMachine.SAWMILL)) {
            if (r.converted() && r.getId().equals(rl("mechanical/sawing/oak_log"))) {
                conv = r;
            }
        }
        Gt.check(conv != null && conv.tier() == 0 && Math.abs(conv.tierYield(0) - 1.75) < 1e-9 && conv.yieldAll(), "scierie MKI : buche x 1,75");
        Gt.equal(Math.max(1, log.time() >> 2), conv.time(), "scierie MKI : duree / 4 (m 0)");
        //1.0.6 : la recette du kit dominee est desactivee (doublon du concasseur) : le broyeur MKI ne la voit plus du tout
        Gt.check(IndustryRecipes.of(s.level, IndustryMachine.CRUSHER).stream().noneMatch(r -> r.getId().equals(rl("crushing/raw_iron"))),
              "broyeur MKI : la recette dominee n'est plus essayee");
        Gt.check(IndustryRecipes.withoutLow(IndustryRecipes.of(s.level, IndustryMachine.SAWMILL)).stream().noneMatch(IndustrialRecipe::converted),
              "pages JEI industrielles sans les recettes converties");
        //monde existant : lot en cours d'un multibloc (entrees deja prises) sur une recette retiree ou dominee, repris par sa remplacante
        Gt.equal(rl("mechanical/sawing/oak_log"), PiRemaps.recipeId(rl("industry/sawmill/steam_planks_oak")), "copie retiree suivie");
        IndustrialRecipe next = IndustryRecipes.successor(s.level, IndustryMachine.SAWMILL, rl("industry/sawmill/steam_planks_oak"));
        Gt.check(next != null && next.results().get(0).stack().is(Items.OAK_PLANKS), "scierie : lot d'une copie retiree repris (planches de chene)");
        IndustrialRecipe dye = IndustryRecipes.successor(s.level, IndustryMachine.EXTRACTOR, rl("industry/extractor/steam_blue_dye"));
        Gt.check(dye != null && dye.results().get(0).stack().is(Items.BLUE_DYE), "extracteur : lot d'une copie retiree repris (colorant bleu)");
        Gt.check(IndustryRecipes.successor(s.level, IndustryMachine.CRUSHER, rl("crushing/raw_iron")) != null, "broyeur : recette dominee remplacee");
        for (var m : PiRemaps.P105_LOT38_RECIPES.entrySet()) {
            Gt.check(s.level.getRecipeManager().byKey(rl(m.getValue())).isPresent(), "remplacante chargee : " + m.getValue());
            Gt.check(s.level.getRecipeManager().byKey(rl(m.getKey())).isEmpty(), "ancienne recette absente : " + m.getKey());
        }
        s.done();
    }

    // ------------------------------------------------------------------ 3. monde avec un marteau-pilon a vapeur

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_38_hammer", timeoutTicks = 20)
    public static void p105_38_marteau_pilon_migre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl("steam_hammer")) && !ForgeRegistries.ITEMS.containsKey(rl("steam_hammer")),
              "marteau-pilon a vapeur retire");
        Gt.equal("steam_saw", PiRemaps.simplifiedBlock("steam_hammer"), "bloc remappe vers la scie a vapeur");
        Gt.equal("steam_saw", PiRemaps.simplifiedItem("steam_hammer"), "objet remappe vers la scie a vapeur");
        Gt.equal("steam_process", PiRemaps.P105_LOT38_TILES.get("steam_hammer"), "type de tile remappe");
        Gt.check(ForgeRegistries.BLOCK_ENTITY_TYPES.containsKey(rl("steam_process")), "type steam_process enregistre");
        //chunk d'avant : la tile remappee lit l'ancien NBT de l'atelier (6 materiaux + sortie, verrou de recette)
        BlockPos at = new BlockPos(2, 1, 2);
        SteamProcessTile saw = place(h, at, SteamMachine.SAW);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "pioneers:steam_hammer");
        ListTag slots = new ListTag();
        CompoundTag a = new ItemStack(Items.COBBLESTONE, 12).save(new CompoundTag());
        a.putShort("Slot", (short) 0);
        slots.add(a);
        CompoundTag b = new ItemStack(Items.STONE_BRICKS, 3).save(new CompoundTag());
        b.putShort("Slot", (short) 6);
        slots.add(b);
        tag.put("slots", slots);
        tag.putString("recipe", "pioneers:bronze/bench/stone_bricks");
        tag.putInt("progress", 17);
        saw.load(tag);
        Gt.check(saw.lockedRecipe() == null, "verrou de l'ancien marteau-pilon efface");
        //sauvegarde AVANT le premier tick : les objets sont gardes (cle pending) puis relus par une tile neuve qui les pose au sol
        CompoundTag saved = saw.saveWithFullMetadata();
        Gt.check(saved.getList("pending", 10).size() == 2, "sauvegarde avant le premier tick : objets gardes");
        //assez loin de la tile d'origine : ground() compte les objets a 3 blocs autour (en (5, 1, 5), les 12 pierres des deux tiles
        //etaient comptees ensemble : 24 au lieu de 12)
        BlockPos at2 = new BlockPos(8, 1, 8);
        SteamProcessTile again = place(h, at2, SteamMachine.SAW);
        again.load(saved);
        again.serverTick(s.level);
        Gt.equal(12, ground(h, at2, Items.COBBLESTONE), "materiaux au sol, rien de perdu");
        Gt.equal(3, ground(h, at2, Items.STONE_BRICKS), "piece de sortie au sol, rien de perdu");
        Gt.equal(0, count(again.guiSlots(), Items.COBBLESTONE), "les materiaux ne restent pas dans la scie");
        //la tile d'origine aussi
        saw.serverTick(s.level);
        Gt.equal(12, ground(h, at, Items.COBBLESTONE), "tile d'origine : materiaux au sol");
        s.done();
    }

    // ------------------------------------------------------------------ 4. anciens formats : compacteur a une entree

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_38_formats", timeoutTicks = 20)
    public static void p105_38_compacteur_ancien(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        SteamProcessTile comp = place(h, new BlockPos(2, 1, 2), SteamMachine.COMPRESSOR);
        Gt.equal(2, SteamMachine.COMPRESSOR.inputs, "compacteur : 2 entrees");
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "pioneers:steam_process");
        ListTag slots = new ListTag();
        CompoundTag in = new ItemStack(item("iron_nugget"), 7).save(new CompoundTag());
        in.putShort("Slot", (short) 0);
        slots.add(in);
        CompoundTag out = new ItemStack(Items.IRON_INGOT, 4).save(new CompoundTag());
        out.putShort("Slot", (short) 1);
        slots.add(out);
        tag.put("slots", slots);
        comp.load(tag);
        Gt.equal(7, comp.guiSlots().get(0).getStack().getCount(), "entree gardee");
        Gt.check(comp.guiSlots().get(1).getStack().isEmpty(), "2e entree vide");
        Gt.check(comp.guiSlots().get(2).getStack().is(Items.IRON_INGOT) && comp.guiSlots().get(2).getStack().getCount() == 4,
              "sortie d'avant glissee apres la 2e entree");
        //format neuf : relu tel quel
        CompoundTag saved = comp.saveWithFullMetadata();
        Gt.equal(38, saved.getInt(SteamProcessTile.FORMAT), "format 38 ecrit");
        SteamProcessTile copy = place(h, new BlockPos(5, 1, 5), SteamMachine.COMPRESSOR);
        copy.load(saved);
        Gt.check(copy.guiSlots().get(2).getStack().is(Items.IRON_INGOT) && copy.guiSlots().get(0).getStack().getCount() == 7, "aller-retour du format 38");
        s.done();
    }

    // ------------------------------------------------------------------ 5. presse (aplatir), tour (acier)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_38_press", timeoutTicks = 20)
    public static void p105_38_presse_et_tour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        SteamProcessTile press = place(h, new BlockPos(2, 1, 2), SteamMachine.PRESS);
        Gt.equal("presse", press.process().family, "presse : famille presse");
        press.inputs().get(0).setStack(new ItemStack(Items.IRON_INGOT, 2));
        Gt.tool(press, new ItemStack(item("plate_die")));                 // 1.0.5-beta (lot 44) : la matrice dans la case Outil
        Gt.check(press.process().craftNow(), "presse a vapeur : lingot aplati");
        Gt.equal(1, count(press.guiSlots(), item("iron_plate")), "lingot de fer + matrice -> plaque de fer");
        Gt.equal(1, press.inputs().get(0).getStack().getCount(), "un lingot pris");
        SteamProcessTile lathe = place(h, new BlockPos(5, 1, 5), SteamMachine.LATHE);
        Gt.equal("tournage", lathe.process().family, "tour : famille tournage");
        lathe.inputs().get(0).setStack(new ItemStack(item("steel_ingot")));
        Gt.check(lathe.process().craftNow(), "tour a vapeur : lingot d'acier tourne");
        Gt.equal(2, count(lathe.guiSlots(), item("steel_rod")), "lingot d'acier -> 2 tiges");
        //le tour a bois ne fait plus l'acier (T0)
        Gt.check(s.level.getRecipeManager().byKey(rl("materials/turning_steel_rod")).isEmpty(), "tour a bois : plus de tige d'acier");
        Gt.check(TierRecipes.of(s.level, "tournage").stream().anyMatch(r -> r.minTier() == 0), "tour a vapeur : fait aussi le tour a bois");
        s.done();
    }

    // ------------------------------------------------------------------ 6. tiers globaux et familles des blocs

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_38_tiers", timeoutTicks = 20)
    public static void p105_38_tiers_globaux(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        String[][] rows = {{"millstone", "0", "broyage"}, {"jaw_crusher", "0", "broyage"}, {"steam_macerator", "1", "broyage"},
              {"basic_crusher", "2", "broyage"}, {"ultimate_crusher", "5", "broyage"}, {"steam_lathe", "1", "tournage"},
              {"steam_sifter", "1", "tamisage"}, {"steam_press", "1", "presse"}, {"wood_lathe", "0", "tournage"}};
        for (String[] r : rows) {
            Gt.equal((long) Integer.parseInt(r[1]), (long) GeneratedTierFamilies.TIER_OF.getOrDefault(r[0], -1), "tier global de " + r[0]);
            Gt.equal(r[2], GeneratedTierFamilies.FAMILY_OF.get(r[0]), "famille de " + r[0]);
            var block = ForgeRegistries.BLOCKS.getValue(rl(r[0]));
            MachineRegistry.Entry e = block == null ? null : MachineRegistry.of(block);
            Gt.check(e != null, "fiche de l'API : " + r[0]);
            Gt.equal(Integer.parseInt(r[1]), e.spec().globalTier(e.tier()), "MachineSpec.globalTier de " + r[0]);
        }
        //1.0.7 : T6 = MKV, R(T6) = 4,375
        Gt.check(GeneratedTierFamilies.YIELD.length == 7 && GeneratedTierFamilies.YIELD[5] == 3.5 && GeneratedTierFamilies.YIELD[6] == 4.375, "table R");
        //JEI : une recette basse convertie n'a jamais d'ingredient vide
        for (TierRecipe r : TierRecipes.of(s.level, "sciage")) {
            for (Counted c : r.inputs()) {
                Gt.check(!c.ingredient().isEmpty(), "entree vide : " + r.id());
            }
        }
        s.done();
    }
}
