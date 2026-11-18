package fr.pioneers.gametest;

import fr.pioneers.api.multiblock.StructureMatcher;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.PartBlock;
import fr.pioneers.tech.industry.multi.PartMigration;
import fr.pioneers.tech.industry.multi.ShapeTemplate;
import fr.pioneers.tech.industry.multi.SheetMetalBlock;
import fr.pioneers.tech.industry.multi.Signature;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 15, decision de Guillaume du 07/11/2026) : GameTests du RETRAIT DES NEUF BLOCS DE MULTIBLOC (brûleur, tuyère, cheminée,
 * torchère, évent, ventilateur, groupe froid, bobine de chauffe, bloc d'ingénierie). Les multiblocs qui les exigeaient se forment en carcasses
 * (haut fourneau, cowper, convertisseur, four tubulaire, colonne atmospherique, usine d'ergols, turbine a gaz ; centre de donnees : famille de
 * MultiblockApiTests) et gardent leur taille (carcasse au sommet a la place de la cheminee) ; les anciens ids se chargent en carcasse MKI et
 * ne sont plus enregistres ; un controleur sauve avant le lot remet la carcasse d'une ancienne piece au tier de sa structure (enveloppe) ou en
 * tole (paroi d'un contenant).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot15Tests {

    private static final Direction FRONT = Direction.NORTH, FORWARD = FRONT.getOpposite(), RIGHT = FORWARD.getClockWise();

    private PatchP104Lot15Tests() {
    }

    /** Case du monde (relative au test) d'une case du gabarit ; controleur en {@code c} (plan de MultiblockApiTests.build). */
    private static BlockPos at(GameTestHelper h, BlockPos c, int cell) {
        BlockPos abs = StructureMatcher.set(new BlockPos.MutableBlockPos(), h.absolutePos(c), FORWARD, RIGHT, ShapeTemplate.x(cell), ShapeTemplate.y(cell),
              ShapeTemplate.z(cell)).immutable();
        return abs.subtract(h.absolutePos(BlockPos.ZERO));
    }

    /** Case au-dessus du sommet (ancienne cheminee), ou null. */
    @Nullable
    private static Integer summit(IndustryMachine m) {
        for (int cell : ShapeTemplate.of(m).get(0).cells()) {
            if ((ShapeTemplate.zones(cell) & Signature.Zone.TOP.bit) != 0 && !ShapeTemplate.isAttached(cell) && !ShapeTemplate.interior(cell)
                && !ShapeTemplate.special(cell)) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Plan bati en carcasses MKI : forme, tier 0, aucune piece retiree demandee, carcasse au sommet si la machine avait une cheminee.
     * 1.0.4-beta (lot 24) : la forme est celle du NBT de Guillaume (plus de case rapportee au sommet : sa structure dit ou va chaque bloc) ;
     * tier fixe d'une structure de salle blanche (centre de donnees).
     */
    private static void forms(GameTestHelper h, IndustryMachine m, boolean summitCasing) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(summitCasing, m.summitCasing, m.multiId() + " : carcasse au sommet");
        Gt.check(m.signatures.isEmpty(), m.multiId() + " : plus aucune piece signature exigee (" + m.signatures + ")");
        BlockPos c = MultiblockApiTests.build(h, m);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        ShapeTemplate t = ShapeTemplate.of(m).get(0);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), m.multiId() + " : le plan en carcasses ne se forme pas (" + tile.guiValue(5) + ")");
            Gt.equal(t.nbt && t.fixedTier >= 0 ? t.fixedTier : 0, tile.tier(), m.multiId() + " : tier du plan en carcasses MKI");
            Integer top = summit(m);
            Gt.equal(summitCasing && !t.nbt, top != null, m.multiId() + " : case au-dessus du sommet dans le gabarit");
            if (top != null) {
                Gt.check(h.getBlockState(at(h, c, top)).getBlock() instanceof CasingBlock, m.multiId() + " : carcasse au sommet dans le plan");
                Gt.check(tile.structure() != null && tile.structure().isPart(h.absolutePos(at(h, c, top))), m.multiId() + " : carcasse du sommet comptee");
            }
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_blast", timeoutTicks = 100)
    public static void p104_15_haut_fourneau_sans_tuyere_ni_cheminee(GameTestHelper h) {
        forms(h, IndustryMachine.BLAST_FURNACE, true);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_stove", timeoutTicks = 100)
    public static void p104_15_cowper_sans_bruleur(GameTestHelper h) {
        forms(h, IndustryMachine.HOT_BLAST_STOVE, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_converter", timeoutTicks = 100)
    public static void p104_15_convertisseur_sans_tuyere(GameTestHelper h) {
        forms(h, IndustryMachine.CONVERTER, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_fired", timeoutTicks = 100)
    public static void p104_15_four_tubulaire_sans_bruleur_ni_cheminee(GameTestHelper h) {
        forms(h, IndustryMachine.CRACKER, true);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_column", timeoutTicks = 100)
    public static void p104_15_colonne_sans_torchere(GameTestHelper h) {
        forms(h, IndustryMachine.DISTILLATION_COLUMN, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_propellant", timeoutTicks = 100)
    public static void p104_15_usine_d_ergols_sans_groupe_froid(GameTestHelper h) {
        forms(h, IndustryMachine.PROPELLANT_PLANT, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_turbine", timeoutTicks = 100)
    public static void p104_15_turbine_a_gaz_sans_cheminee(GameTestHelper h) {
        forms(h, IndustryMachine.GAS_TURBINE, true);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_datacenter", timeoutTicks = 100)
    public static void p104_15_centre_de_donnees_sans_groupe_froid(GameTestHelper h) {
        forms(h, IndustryMachine.DATA_CENTER, false);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_15_remaps", timeoutTicks = 20)
    public static void p104_15_anciens_ids_en_carcasse(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(9, PiRemaps.P104_LOT15_PARTS.size(), "neuf blocs retires");
        for (String id : PiRemaps.P104_LOT15_PARTS) {
            Gt.equal("industrial_casing_basic", PiRemaps.p104Lot15Block(id), id + " -> carcasse MKI");
            Gt.equal("industrial_casing_basic", PiRemaps.simplifiedBlock(id), id + " : bloc pose -> carcasse MKI");
            Gt.equal("industrial_casing_basic", PiRemaps.simplifiedItem(id), id + " : objet -> carcasse MKI");
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("pioneers", id);
            Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl), id + " : bloc encore enregistre");
            Gt.check(!ForgeRegistries.ITEMS.containsKey(rl), id + " : objet encore enregistre");
        }
        Gt.equal(1, PartBlock.Kind.values().length, "seule piece restante : le refrigerateur a dilution");
        Gt.check(ForgeRegistries.BLOCKS.containsKey(ResourceLocation.fromNamespaceAndPath("pioneers", "industrial_dilution_refrigerator")),
              "refrigerateur a dilution toujours la");
        Gt.equal(null, PiRemaps.p104Lot15Block("industrial_dilution_refrigerator"), "refrigerateur a dilution non remappe");
        s.done();
    }

    /** Sauvegarde du controleur sans le drapeau du lot 15 (controleur sauve avant le lot) relue dans la tile. */
    private static void oldSave(ControllerTile tile) {
        CompoundTag tag = tile.saveWithoutMetadata();
        tag.remove(PartMigration.TAG);
        tile.load(tag);
    }

    /**
     * 1.0.4-beta (lot 24) : la turbine a gaz (et tous les multiblocs a NBT) a la forme de Guillaume : plus de carcasse au sommet a remettre au
     * tier ; une structure d'avant le lot 15 a l'ancienne forme ne s'y forme plus. Le test garde la regle : un controleur sauve avant le lot
     * 15 ne change AUCUN bloc d'une structure NBT (la migration ne s'applique qu'aux gabarits d'avant), sa structure reste formee et au tier.
     */
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_migrate", timeoutTicks = 200)
    public static void p104_15_monde_existant_carcasse_remise_au_tier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        IndustryMachine m = IndustryMachine.GAS_TURBINE;
        if (ShapeTemplate.of(m).get(0).nbt) {
            BlockPos c0 = MultiblockApiTests.build(h, m);
            ControllerTile t0 = MultiblockApiTests.controller(h, c0);
            Block basic0 = PiIndustry.CASINGS.get(CableTier.BASIC).getBlock();
            h.runAfterDelay(5, () -> {
                Gt.check(t0.formed(), "turbine (NBT) non formee");
                int before = 0;
                for (int cell : ShapeTemplate.of(m).get(0).cells()) {
                    if (h.getBlockState(at(h, c0, cell)).getBlock() == basic0) {
                        before++;
                    }
                }
                int casings = before;
                oldSave(t0);
                t0.requestCheck();
                h.runAfterDelay(5, () -> {
                    int after = 0;
                    for (int cell : ShapeTemplate.of(m).get(0).cells()) {
                        if (h.getBlockState(at(h, c0, cell)).getBlock() == basic0) {
                            after++;
                        }
                    }
                    Gt.equal(casings, after, "structure NBT : aucune carcasse changee par la migration du lot 15");
                    Gt.check(t0.formed(), "turbine toujours formee");
                    Gt.equal(0, t0.tier(), "turbine toujours MKI");
                    s.done();
                });
            });
            return;
        }
        BlockPos c = MultiblockApiTests.build(h, m);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        Block basic = PiIndustry.CASINGS.get(CableTier.BASIC).getBlock(), advanced = PiIndustry.CASINGS.get(CableTier.ADVANCED).getBlock();
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "turbine MKI non formee");
            //Structure montee en MKII : toutes les carcasses remplacees
            for (int cell : ShapeTemplate.of(m).get(0).cells()) {
                BlockPos p = at(h, c, cell);
                if (h.getBlockState(p).getBlock() == basic) {
                    h.setBlock(p, advanced);
                }
            }
            tile.requestCheck();
            h.runAfterDelay(5, () -> {
                Gt.check(tile.formed(), "turbine MKII non formee");
                Gt.equal(1, tile.tier(), "turbine en carcasses MKII");
                //Monde d'avant le lot : la cheminee du sommet s'est chargee en carcasse MKI, et le controleur n'a pas le drapeau
                BlockPos top = at(h, c, summit(m));
                h.setBlock(top, basic);
                oldSave(tile);
                tile.requestCheck();
                h.runAfterDelay(5, () -> {
                    Gt.equal(advanced, h.getBlockState(top).getBlock(), "ancienne cheminee remise en carcasse MKII");
                    Gt.check(tile.formed(), "turbine toujours formee apres migration");
                    Gt.equal(1, tile.tier(), "turbine toujours MKII apres migration");
                    //Une seule fois : une carcasse MKI posee ensuite fait bien baisser le tier
                    h.setBlock(top, basic);
                    tile.requestCheck();
                    h.runAfterDelay(5, () -> {
                        Gt.equal(basic, h.getBlockState(top).getBlock(), "migration une seule fois");
                        Gt.equal(0, tile.tier(), "carcasse MKI posee apres la migration : tier MKI");
                        s.done();
                    });
                });
            });
        });
    }

    /**
     * 1.0.4-beta (lot 24) : la cuve de stockage a la forme de Guillaume (NBT) : la migration du lot 15 ne touche plus ses blocs ; une carcasse
     * posee dans une case de tole y est refusee (la case attend une tole, un port ou du verre) et rien ne la change.
     */
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_15_sheet", timeoutTicks = 100)
    public static void p104_15_monde_existant_paroi_en_tole(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        IndustryMachine m = IndustryMachine.STORAGE_TANK;
        if (ShapeTemplate.of(m).get(0).nbt) {
            BlockPos c0 = MultiblockApiTests.build(h, m);
            ControllerTile t0 = MultiblockApiTests.controller(h, c0);
            Block basic0 = PiIndustry.CASINGS.get(CableTier.BASIC).getBlock();
            h.runAfterDelay(5, () -> {
                Gt.check(t0.formed(), "cuve (NBT) non formee");
                Integer sheet = null;
                for (int cell : ShapeTemplate.of(m).get(0).cells()) {
                    if (ShapeTemplate.sheet(cell)) {
                        sheet = cell;
                        break;
                    }
                }
                Gt.check(sheet != null, "la cuve n'a pas de paroi en tole");
                BlockPos p = at(h, c0, sheet);
                h.setBlock(p, basic0);
                oldSave(t0);
                t0.requestCheck();
                h.runAfterDelay(5, () -> {
                    Gt.equal(basic0, h.getBlockState(p).getBlock(), "structure NBT : la carcasse posee n'est pas changee");
                    Gt.check(!t0.formed(), "carcasse dans une case de tole : refusee");
                    s.done();
                });
            });
            return;
        }
        BlockPos c = MultiblockApiTests.build(h, m);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        Block basic = PiIndustry.CASINGS.get(CableTier.BASIC).getBlock();
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "cuve non formee");
            Integer sheet = null;
            for (int cell : ShapeTemplate.of(m).get(0).cells()) {
                if (ShapeTemplate.sheet(cell)) {
                    sheet = cell;
                    break;
                }
            }
            Gt.check(sheet != null, "la cuve n'a pas de paroi en tole");
            //Ancien bloc de paroi fonctionnel (event...) dans la tole : charge en carcasse MKI
            BlockPos p = at(h, c, sheet);
            h.setBlock(p, basic);
            oldSave(tile);
            tile.requestCheck();
            h.runAfterDelay(5, () -> {
                Gt.check(h.getBlockState(p).getBlock() instanceof SheetMetalBlock, "carcasse d'une ancienne piece de paroi remise en tole");
                Gt.check(tile.formed(), "cuve toujours formee apres migration");
                s.done();
            });
        });
    }
}
