package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.cargo.CargoFlight;
import fr.pioneers.space.cargo.CargoNetwork;
import fr.pioneers.space.cargo.CargoRoutes;
import fr.pioneers.space.cargo.CargoType;
import fr.pioneers.space.module.RocketCargo;
import fr.pioneers.space.mega.MegaWorld;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.mega.PacketMegaState;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.common.projector.ProjectedStructure;
import fr.pioneers.tech.industry.multi.IndustrialBlueprint;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import fr.pioneers.api.gas.GasStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.7 « Plus loin et au-dela », lot 4 (socle des megastructures) :
 * <ul>
 *     <li>donnees : chaque megastructure a son astre, son port (multibloc du catalogue), ses ingredients et ses composants gates enregistres ;
 *     les composants gates n'ont pour source QUE les recettes de leur port (aucune autre recette du jeu ne les donne) ;</li>
 *     <li>chantier : livraison par capacite (seulement ce qui manque, rien d'autre), deblocage d'un coup quand la liste est complete ;</li>
 *     <li>synchronisation : l'etat aller-retour par le paquet (le ciel, calcul client, a son banc : tools/au_dela/sky/run.sh) ;</li>
 *     <li>port : refuse hors de l'astre et avant le deblocage, refuse en carcasses MKI, fait sa recette unique en carcasses MKV (lot en cours
 *     garde a la relecture).</li>
 * </ul>
 * Les tests remettent l'etat commun et l'astre du monde d'essai comme ils les ont trouves.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot4Tests {

    private static final int MARS = Megastructures.byBody(Body.MARS);

    private PatchP107Lot4Tests() {
    }

    private static ResourceLocation rl(String id) {
        return ResourceLocation.tryParse(id);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_4_data", timeoutTicks = 20)
    public static void p107_4_donnees_et_source_unique(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(Megastructures.COUNT >= 1 && MARS >= 0, "megastructure de Mars");
        Gt.equal("mars_elevator", Megastructures.id(MARS), "Ascenseur de Mars");
        Set<String> gated = new HashSet<>();
        for (int i = 0; i < Megastructures.COUNT; i++) {
            IndustryMachine port = IndustryMachine.byKey(Megastructures.port(i));
            Gt.check(port != null && PiIndustry.controller(port) != null, "port de " + Megastructures.id(i));
            Gt.check(Megastructures.isPort(port.key), "port reconnu");
            for (int k = 0; k < Megastructures.ingredientCount(i); k++) {
                Gt.check(Megastructures.ingredient(i, k) != Items.BARRIER, Megastructures.id(i) + " : ingredient " + k + " introuvable");
            }
            Gt.check(Megastructures.gated(i).length >= 3, Megastructures.id(i) + " : au moins 3 composants gates");
            Set<String> made = new HashSet<>();
            for (IndustrialRecipe r : IndustryRecipes.of(h.getLevel(), port)) {
                for (IndustrialRecipe.Result res : r.results()) {
                    made.add(ForgeRegistries.ITEMS.getKey(res.stack().getItem()).toString());
                }
            }
            for (String g : Megastructures.gated(i)) {
                Gt.check(ForgeRegistries.ITEMS.containsKey(rl(g)), "composant gate absent : " + g);
                Gt.check(made.contains(g), g + " : pas fabrique par le port de " + Megastructures.id(i));
                gated.add(g);
            }
        }
        //source unique : aucune recette hors des ports ne donne un composant gate
        Level level = h.getLevel();
        for (Recipe<?> r : level.getRecipeManager().getRecipes()) {
            if (r instanceof IndustrialRecipe ir) {
                if (Megastructures.isPort(ir.machine().key)) {
                    continue;
                }
                for (IndustrialRecipe.Result res : ir.results()) {
                    Gt.check(!gated.contains(ForgeRegistries.ITEMS.getKey(res.stack().getItem()).toString()), r.getId() + " donne un composant gate");
                }
                continue;
            }
            ItemStack out = r.getResultItem(level.registryAccess());
            Gt.check(out.isEmpty() || !gated.contains(ForgeRegistries.ITEMS.getKey(out.getItem()).toString()), r.getId() + " donne un composant gate");
        }
        s.done();
    }

    /**
     * 1.0.7 : le chantier s'ouvre depuis la carte et se ravitaille par fusees cargo : une fusee arrivee au chantier livre ce qui manque, garde
     * le reste (terre, surplus), et le chantier termine quitte le reseau cargo.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_4_site", timeoutTicks = 40)
    public static void p107_4_chantier_livre_puis_debloque(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        net.minecraft.server.MinecraftServer server = h.getLevel().getServer();
        MegaWorld w = MegaWorld.get(server);
        CompoundTag saved = w.save(new CompoundTag());
        CargoNetwork net = CargoNetwork.get(server);
        boolean wasOpen = net.getStation(CargoNetwork.megaSiteId(MARS)) != null;
        s.onCleanup(() -> w.restore(saved));
        try {
            w.reset(MARS);
            net.openMegaSite(MARS);
            CargoNetwork.Station site = net.getStation(CargoNetwork.megaSiteId(MARS));
            Gt.check(site != null && site.receiver && site.mega == MARS, "chantier ouvert : destination cargo");
            Gt.check(net.receiversFor(java.util.UUID.randomUUID()).contains(site), "chantier parmi les destinations des complexes");
            Gt.equal(Body.MARS, CargoRoutes.bodyOf(site), "astre du chantier");
            //premiere fusee : un ingredient (trop), de la terre
            int need = Megastructures.needed(MARS, 0);
            RocketCargo cargo = new RocketCargo();
            cargo.getItems().set(0, new ItemStack(Megastructures.ingredient(MARS, 0), Math.min(64, need + 3)));
            cargo.getItems().set(1, new ItemStack(Items.DIRT, 64));
            CargoFlight flight = new CargoFlight(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), site.id,
                  CargoType.ITEMS, cargo.save(), 0, 0, false);
            int taken = CargoNetwork.deliverToMegaSite(server, flight, site);
            int first = Math.min(64, need + 3);
            Gt.equal(Math.min(first, need), taken, "le chantier prend ce qui manque");
            RocketCargo rest = RocketCargo.read(flight.cargo);
            Gt.equal(64, rest.getItems().get(1).getCount(), "la terre reste a bord");
            Gt.equal(first - Math.min(first, need), rest.getItems().get(0).getCount(), "le surplus reste a bord");
            Gt.check(!w.unlocked(MARS), "debloquee trop tot");
            //le reste de la liste, fusee apres fusee
            for (int k = 0; k < Megastructures.ingredientCount(MARS); k++) {
                while (w.missing(MARS, k) > 0) {
                    RocketCargo c = new RocketCargo();
                    c.getItems().set(0, new ItemStack(Megastructures.ingredient(MARS, k), Math.min(64, w.missing(MARS, k))));
                    CargoFlight f = new CargoFlight(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), site.id,
                          CargoType.ITEMS, c.save(), 0, 0, false);
                    Gt.check(CargoNetwork.deliverToMegaSite(server, f, site) > 0, "livraison refusee : " + k);
                }
            }
            Gt.check(w.unlocked(MARS), "liste complete : Ascenseur debloque");
            Gt.check(net.getStation(CargoNetwork.megaSiteId(MARS)) == null, "chantier termine : plus une destination");
            Gt.check((w.unlockedMask() & (1L << MARS)) != 0, "masque des debloquees");
            //etat synchronise : aller-retour du paquet
            PacketMegaState p = w.packet();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            p.encode(buf);
            PacketMegaState q = PacketMegaState.decode(buf);
            Gt.equal(p.unlocked(), q.unlocked(), "paquet : debloquees");
            Gt.equal(Megastructures.needed(MARS, 1), q.delivered()[MARS][1], "paquet : livraisons");
            //sauvegarde : aller-retour
            CompoundTag tag = w.save(new CompoundTag());
            Gt.check(tag.getList("megas", 10).getCompound(MARS).getBoolean("unlocked"), "sauvegarde : debloquee");
            //le chantier se sauve avec le reseau cargo
            net.openMegaSite(MARS);
            CompoundTag nt = net.save(new CompoundTag());
            Gt.check(nt.getList("stations", 10).stream().anyMatch(t -> ((CompoundTag) t).getString("mega").equals(Megastructures.id(MARS))),
                  "sauvegarde du reseau : chantier");
        } finally {
            if (!wasOpen) {
                net.closeMegaSite(MARS);
            }
            restore(w, saved);
        }
        s.done();
    }

    // ------------------------------------------------------------------ port

    /** Bati le port (plan du catalogue) ; carcasses MKV si demande. */
    private static ControllerTile port(GameTestHelper h, boolean mkv) {
        return build(h, IndustryMachine.byKey(Megastructures.port(MARS)), mkv);
    }

    /** Bati le plan d'une machine du catalogue ; toute la structure au tier MKV si demande (lot 5 : multiblocs de famille). */
    static ControllerTile build(GameTestHelper h, IndustryMachine m, boolean mkv) {
        Direction front = Direction.NORTH, forward = front.getOpposite();
        Map<BlockPos, ProjectedStructure.Ghost> plan = IndustrialBlueprint.of(m).layout(0, BlockPos.ZERO, forward);
        int minX = 0, minY = 0, minZ = 0;
        for (BlockPos p : plan.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
        }
        BlockPos c = new BlockPos(1 - minX, 1 - minY, 1 - minZ);
        CableTier mk5 = CableTier.STELLAR;
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : plan.entrySet()) {
            BlockState st = e.getValue().state();
            if (st == null) {
                continue;
            }
            BlockPos rel = c.offset(e.getKey());
            if (rel.equals(c) && st.hasProperty(MachineBlock.FACING)) {
                st = st.setValue(MachineBlock.FACING, front);
            }
            //toute la structure au tier MKV : carcasses, verres, cadres, toles
            if (mkv && st.getBlock() instanceof CasingBlock) {
                st = PiIndustry.CASINGS.get(mk5).getBlock().defaultBlockState();
            } else if (mkv && st.getBlock() instanceof fr.pioneers.tech.industry.multi.IndustrialGlassBlock) {
                st = PiIndustry.GLASSES.get(mk5).getBlock().defaultBlockState();
            } else if (mkv && st.getBlock() instanceof fr.pioneers.tech.industry.multi.StructureFrameBlock) {
                st = PiIndustry.FRAMES.get(mk5).getBlock().defaultBlockState();
            } else if (mkv && st.getBlock() instanceof fr.pioneers.tech.industry.multi.SheetMetalBlock) {
                st = PiIndustry.SHEETS.get(mk5).getBlock().defaultBlockState();
            }
            h.setBlock(rel, st);
        }
        return MultiblockApiTests.controller(h, c);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p107_4_port_mki", timeoutTicks = 200)
    public static void p107_4_port_refuse_hors_astre_et_en_mki(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Function<Level, Body> old = Megastructures.bodyOf;
        s.onCleanup(() -> Megastructures.bodyOf = old);
        MegaWorld w = MegaWorld.get(h.getLevel().getServer());
        CompoundTag saved = w.save(new CompoundTag());
        s.onCleanup(() -> w.restore(saved));
        ControllerTile tile = port(h, false);
        h.runAfterDelay(5, () -> {
            try {
                Gt.check(tile.formed(), "port : la structure ne se forme pas (" + tile.guiValue(5) + ")");
                w.reset(MARS);
                feed(h, tile);
                Megastructures.bodyOf = l -> Body.MARS;
                tile.getEnergy().setStored(tile.getEnergy().getCapacity());
                h.runAfterDelay(30, () -> {
                    try {
                        Gt.equal(ControllerTile.MEGA_LOCKED, fr.pioneers.tech.industry.multi.MkvCoreSlot.status(tile), "avant le deblocage : refuse");
                        w.unlock(h.getLevel().getServer(), MARS);
                        h.runAfterDelay(30, () -> {
                            try {
                                Gt.equal(ControllerTile.MKV_CASINGS, fr.pioneers.tech.industry.multi.MkvCoreSlot.status(tile), "carcasses MKI : refuse");
                                s.done();
                            } finally {
                                Megastructures.bodyOf = old;
                                restore(w, saved);
                            }
                        });
                    } catch (RuntimeException e) {
                        Megastructures.bodyOf = old;
                        restore(w, saved);
                        throw e;
                    }
                });
            } catch (RuntimeException e) {
                Megastructures.bodyOf = old;
                restore(w, saved);
                throw e;
            }
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p107_4_port_mkv", timeoutTicks = 1600)
    public static void p107_4_port_mkv_fait_sa_recette_unique(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Function<Level, Body> old = Megastructures.bodyOf;
        s.onCleanup(() -> Megastructures.bodyOf = old);
        MegaWorld w = MegaWorld.get(h.getLevel().getServer());
        CompoundTag saved = w.save(new CompoundTag());
        s.onCleanup(() -> w.restore(saved));
        ControllerTile tile = port(h, true);
        h.runAfterDelay(5, () -> {
            try {
                Gt.check(tile.formed(), "port MKV : la structure ne se forme pas (" + tile.guiValue(5) + ")");
                Megastructures.bodyOf = l -> null;
                w.unlock(h.getLevel().getServer(), MARS);
                feed(h, tile);
                tile.getEnergy().setStored(tile.getEnergy().getCapacity());
            } catch (RuntimeException e) {
                Megastructures.bodyOf = old;
                restore(w, saved);
                throw e;
            }
            h.runAfterDelay(30, () -> {
                try {
                    Gt.equal(ControllerTile.MEGA_LOCKED, fr.pioneers.tech.industry.multi.MkvCoreSlot.status(tile), "hors de Mars : refuse");
                    Megastructures.bodyOf = l -> Body.MARS;
                } catch (RuntimeException e) {
                    Megastructures.bodyOf = old;
                    restore(w, saved);
                    throw e;
                }
                h.onEachTick(() -> tile.getEnergy().setStored(tile.getEnergy().getCapacity()));
                boolean[] seen = {false};
                h.succeedWhen(() -> {
                    if (!seen[0] && fr.pioneers.tech.industry.multi.MkvCoreSlot.status(tile) == ControllerTile.WORKING) {
                        //lot en cours : garde a la relecture
                        CompoundTag tag = tile.saveWithoutMetadata();
                        Gt.check(tag.toString().contains("batch"), "lot en cours absent de la sauvegarde");
                        seen[0] = true;
                    }
                    boolean made = false;
                    for (var slot : tile.itemOutputs()) {
                        made |= ForgeRegistries.ITEMS.getKey(slot.getStack().getItem()).toString().equals(Megastructures.gated(MARS)[1]);
                    }
                    Gt.check(made, "poutre orbitale continue pas encore faite (etat " + fr.pioneers.tech.industry.multi.MkvCoreSlot.status(tile) + ")");
                    Gt.check(seen[0], "jamais vu en marche");
                    //1.0.7 (« ciel grandiose ») : le port qui travaille met sa megastructure en marche dans le ciel
                    Gt.check((fr.pioneers.space.mega.MegaActivity.mask(h.getLevel().getGameTime()) & (1L << MARS)) != 0,
                          "Ascenseur pas en marche pour le ciel");
                    Megastructures.bodyOf = old;
                    restore(w, saved);
                    s.done();
                });
            });
        });
    }

    /** Entrees de la poutre orbitale continue (recette unique de l'Ascenseur) et argon. */
    private static void feed(GameTestHelper h, ControllerTile tile) {
        IndustrialRecipe beam = null;
        for (IndustrialRecipe r : IndustryRecipes.of(h.getLevel(), tile.machine)) {
            if (r.getId().getPath().endsWith("mars_elevator_orbital_beam")) {
                beam = r;
            }
        }
        Gt.check(beam != null, "recette de la poutre orbitale continue");
        var inputs = tile.itemInputs();
        int n = 0;
        for (var ing : beam.items()) {
            ItemStack[] options = ing.ingredient().getItems();
            Gt.check(options.length > 0, "entree vide");
            inputs.get(n++).setStack(new ItemStack(options[0].getItem(), ing.count()));
        }
        tile.gasInTanks().get(0).setStack(GasStack.of(PioneersAPI.gas(new ResourceLocation("pioneers", "argon")), 4_000));
    }

    private static void restore(MegaWorld w, CompoundTag saved) {
        w.restore(saved);
    }
}
