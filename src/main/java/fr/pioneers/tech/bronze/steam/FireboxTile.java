package fr.pioneers.tech.bronze.steam;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.tech.bronze.Hearth;
import fr.pioneers.tech.bronze.LitBronzeBlock;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.kinetics.KineticsConfig;
import fr.pioneers.tech.mechanics.PiMechanics;
import fr.pioneers.core.registries.PiBronze;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Foyer et cerveau de la chaudiere modulaire. Formation : les corps et domes colles les uns aux autres a partir du bloc au-dessus du foyer
 * (12 pieces au plus, au moins un corps juste au-dessus) ; la plus haute colonne de cheminee posee sur une piece fait le tirage.
 * <p>
 * Physique (une fois par tick, {@link SteamRules#boil}) : le feu apporte {@code 6 kJ x tirage} ; l'eau et le metal chauffent ; a 100 C le
 * surplus vaporise l'eau (2,26 kJ par mB, 1 mB d'eau = 8 mB de vapeur) dans la RESERVE de vapeur des pieces (corps et domes) ; reserve
 * pleine, la chaudiere ne produit plus (la chaleur en trop se perd). 1.0.2-beta (lot 3, Guillaume : « supprimer la notion de pression, la
 * chaudiere explose si elle n'a plus d'eau tout simplement ») : plus de pression ni de surpression ; SEUL danger : chauffer A SEC (eau
 * epuisee, feu allume) : alarme, usure, puis explosion (config {@code bronze.boilerExplosion} : ON / WARN = le feu s'eteint / OFF) ; de l'eau
 * froide sur une chaudiere rougie a sec la fait sauter aussi (choc thermique) ; eau au-dessus de 92 % = primage (vapeur humide).
 */
public class FireboxTile extends BronzeTile implements MultiblockCore.House.Holder, MultiblockPort {

    /** Lot 8b (vraie v2) : foyer a la place de l'energie motrice, niveau d'eau, manometre et bouton du feu, carte, combustible et cendres. */
    public static final int FUEL_X = 171, FUEL_Y = StandardLayout.TOP + 4, ASH_X = 171, ASH_Y = StandardLayout.TOP + 40;
    public static final StandardLayout.Rect WATER = StandardLayout.ageGauge(0), DIAL = new StandardLayout.Rect(44, StandardLayout.TOP, 34, 34),
          FIRE = new StandardLayout.Rect(44, StandardLayout.TOP + 38, 34, 14), CARD = StandardLayout.ageCard(84, 78);
    /** Lot 8b : valeurs nommees aux indices d'avant. */
    public static final UiValues.Key DRAFT = UiValues.Key.of("draft_x100"),
          MADE = UiValues.Key.of("made_x100"), DAMAGE = UiValues.Key.of("damage_x10"), SHELLS = UiValues.Key.of("shells"),
          DOMES = UiValues.Key.of("domes"), CHIMNEY = UiValues.Key.of("chimney"), ENABLED = UiValues.Key.of("enabled"),
          PROBLEM = UiValues.Key.of("problem"), BURN_LEFT = UiValues.Key.of("burn_left_s"), BONUS = UiValues.Key.of("fuel_bonus");
    public static final double HEAT = SteamRules.HEAT, LATENT = SteamRules.LATENT, WATER_C = SteamRules.WATER_C, SHELL_C = SteamRules.SHELL_C;
    public static final int TOGGLE = CUSTOM;
    private final ItemSlot fuel, ash;
    private final Hearth hearth = new Hearth();
    private final SteamVolume steam = new SteamVolume(1);
    private final Set<BlockPos> parts = new HashSet<>();
    private final List<BlockPos> chimneyTops = new ArrayList<>();
    private int shells, domes, chimney;
    private double water, temperature = 20, damage, made;
    private boolean enabled = true;
    /** 1.0.3-beta (correctif) : prochain sifflement de la reserve pleine (non sauve ; 0 = a tirer). */
    private long nextHiss;

    /** 1.0.3-beta (lot 7) : dernier tick ou une surete de chaudiere ({@link AccessoryTile}) a coupe le foyer (non sauve : la surete le redit). */
    private long cutoffAt = Long.MIN_VALUE;
    private boolean cut;
    private String problem = "unformed";

    public FireboxTile(BlockPos pos, BlockState state) {
        super(PiBronze.FIREBOX_TILE.get(), pos, state);
        fuel = slot(new ItemSlot(() -> 64, s -> ForgeHooks.getBurnTime(s, null) > 0, (s, a) -> true, (s, a) -> !a.external(),
              this::changed, FUEL_X, FUEL_Y));
        ash = slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, ASH_X, ASH_Y));
    }

    // ------------------------------------------------------------------
    // Acces
    // ------------------------------------------------------------------

    public SteamVolume steamVolume() {
        return steam;
    }

    /** Contenance de la reserve de vapeur (mB) : somme des pieces. */
    public double steamCapacity() {
        return steam.capacity();
    }

    public double water() {
        return water;
    }

    public int waterCapacity() {
        return shells * BoilerPart.WATER;
    }

    /** 1.0.3-beta (lot 7) : une surete de chaudiere coupe le foyer pour ce tick (eau sous son seuil). */
    public void cutOff(long gameTime) {
        cutoffAt = gameTime;
    }

    /** 1.0.3-beta (lot 7) : foyer coupe par une surete (eau basse) : ni chaleur, ni combustible brule ; il repart seul quand l'eau remonte. */
    public boolean cut() {
        return cut;
    }

    public boolean formed() {
        return shells > 0;
    }

    /** 1.0.4-beta (lot 24) : facade du foyer (la chaudiere est derriere lui, forme de Guillaume). */
    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(fr.pioneers.tech.bronze.BronzeBlock.FACING) ? s.getValue(fr.pioneers.tech.bronze.BronzeBlock.FACING) : Direction.NORTH;
    }

    /** 1.0.4-beta (lot 24) : feu peint sur les foyers en briques refractaires de la forme (0 eteints, 2 en flammes) ; -1 : a peindre. */
    private int paintedFire = -1;

    private void paintFire(ServerLevel level, int fire) {
        if (fire == paintedFire) {
            return;
        }
        paintedFire = fire;
        for (BlockPos p : HouseNbt.positions(worldPosition, facing(), SHAPE, "X")) {
            if (level.isLoaded(p)) {
                fr.pioneers.tech.bronze.autofoundry.FoundryPartBlock.paint(level, p, fire, false);
            }
        }
    }

    public boolean owns(BlockPos pos) {
        return parts.contains(pos);
    }

    public double draft() {
        return SteamRules.draft(chimney);
    }

    public double temperature() {
        return temperature;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    /** Ajoute de l'eau (renvoie ce qui est entre) ; l'eau froide refroidit la chaudiere, et sur un foyer rouge et sec elle la fait sauter. */
    public int fillWater(int amount, boolean simulate) {
        int room = (int) Math.max(0, waterCapacity() - water);
        int added = Math.min(room, amount);
        if (!simulate && added > 0) {
            double c = capacity();
            temperature = (temperature * c + 20 * added * WATER_C) / (c + added * WATER_C);
            if (damage > 50 && temperatureBefore > 350 && KineticsConfig.boilerExplosion() == KineticsConfig.Breakage.ON && level instanceof ServerLevel server) {
                explode(server, "steam.pioneers.boiler.shock");
                return added;
            }
            water += added;
            setChanged();
        }
        return added;
    }

    private double temperatureBefore = 20;

    private double capacity() {
        return water * WATER_C + shells * SHELL_C + 2;
    }

    // ------------------------------------------------------------------
    // Formation
    // ------------------------------------------------------------------

    /**
     * Lot 8a : RELEVE UNIQUE de la chaudiere, le meme en jeu et au banc des multiblocs ; une piece deja prise par un AUTRE foyer en place est
     * refusee (monde reel : tile de la case ; monde factice du banc : aucune tile, rien n'est pris). 1.0.4-beta (lot 24) : FORME DE GUILLAUME
     * ({@link HouseNbt}, {@code bronze_boiler} / {@code steel_boiler}, meme forme) : le foyer (controleur) devant, 4 foyers en briques
     * refractaires derriere et sur les cotes du rang arriere, 11 corps (cuivre ou acier) en croix sur deux niveaux et un au sommet, le dome
     * sur le corps de devant, la cheminee posee sur le corps du sommet : au moins un bloc, plus haute elle tire mieux ({@link BoilerPart#MAX_CHIMNEY},
     * comme avant). Les ameliorations (accessoires colles aux corps) restent valables.
     *
     * @param found   corps et domes de la structure (vide si la forme n'y est pas)
     * @param at      premiere case fautive de la forme (null : complete)
     * @param problem cle courte du defaut ({@code ""}, {@code shape}, {@code no_shell}, {@code no_dome})
     */
    public record Boiler(Set<BlockPos> found, int shells, int domes, int chimney, double reserve, List<BlockPos> chimneyTops, String problem,
                         @org.jetbrains.annotations.Nullable BlockPos at) {

        /** Chaudiere en service : la forme complete (1.0.4-beta, lot 24 : une chaudiere a l'ancienne forme n'est plus formee). */
        public boolean formed() {
            return shells > 0 && problem.isEmpty();
        }

        /** Conforme au plan : forme, corps et dome. */
        public boolean complete() {
            return shells > 0 && domes > 0 && problem.isEmpty();
        }
    }

    /** 1.0.4-beta (lot 24) : cle de la forme verifiee (les deux NBT ont la meme forme ; l'indicateur de niveau est facultatif). */
    public static final String SHAPE = "bronze_boiler";

    private static boolean free(fr.pioneers.tech.industry.multi.Structure.View level, BlockPos p, BlockPos firebox) {
        return !(level.tile(p) instanceof BoilerPartTile t) || t.controllerPos() == null || t.controllerPos().equals(firebox) || t.controller() == null;
    }

    public static Boiler measure(fr.pioneers.tech.industry.multi.Structure.View level, BlockPos firebox, Direction front) {
        Set<BlockPos> found = new HashSet<>();
        BlockPos fault = HouseNbt.fault(level, firebox, front, SHAPE);
        if (fault == null) {
            for (BlockPos p : HouseNbt.positions(firebox, front, SHAPE, "od")) {
                if (!free(level, p, firebox)) {
                    fault = p;
                    break;
                }
                found.add(p);
            }
        }
        if (fault != null) {
            return new Boiler(Set.of(), 0, 0, 0, 0, List.of(), "shape", fault);
        }
        int s = 0, d = 0;
        double reserve = 0;
        for (BlockPos p : found) {
            BoilerPart part = ((BoilerPartBlock) level.getBlockState(p).getBlock()).part;
            if (part.shell()) {
                s++;
            } else {
                d++;
            }
            reserve += part.steam;
        }
        //Cheminee : depuis sa premiere case du plan, vers le haut
        int h = 0;
        HouseNbt.Cell low = HouseNbt.first(SHAPE, 'k');
        BlockPos c = low == null ? firebox.above(4) : HouseNbt.at(firebox, front, low);
        while (h < BoilerPart.MAX_CHIMNEY && level.isLoaded(c) && level.getBlockState(c).getBlock() instanceof BoilerPartBlock cb && cb.part == BoilerPart.CHIMNEY) {
            h++;
            c = c.above();
        }
        List<BlockPos> tops = h > 0 ? List.of(c) : List.of();
        String problem = s == 0 ? "no_shell" : d == 0 ? "no_dome" : "";
        return new Boiler(found, s, d, h, reserve, tops, problem, null);
    }

    /** Banc des multiblocs (lot 6) : {corps, domes, cheminee la plus haute, trop grande ? 1 : 0} du releve unique {@link #measure}. */
    public static int[] survey(fr.pioneers.tech.industry.multi.Structure.View level, BlockPos firebox, Direction front) {
        Boiler b = measure(level, firebox, front);
        return new int[]{b.shells(), b.domes(), b.chimney(), "too_big".equals(b.problem()) ? 1 : 0};
    }

    /**
     * Lot 8a : cycle de vie commun des multiblocs maison (verification toutes les 2 s ou a la demande, revalidee au chargement) ; la
     * chaudiere reste « en service » des qu'elle a un corps (regle d'avant), le cycle porte l'erreur commune et le registre des hotes.
     */
    private final MultiblockCore.House house = new MultiblockCore.House(new MultiblockCore.House.Host() {
        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public Direction front() {
            return facing();
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (level == null) {
                return null;
            }
            Boiler b = measure(fr.pioneers.tech.industry.multi.Structure.View.of(level), worldPosition, facing());
            if (b.formed()) {
                //1.0.4-beta (lot 24) : toute la forme (foyers en briques refractaires compris) et la cheminee
                Set<BlockPos> cells = new HashSet<>(HouseNbt.members(worldPosition, facing(), SHAPE));
                for (BlockPos top : b.chimneyTops()) {
                    for (BlockPos c = top.below(); !b.found().contains(c) && c.getY() > worldPosition.getY(); c = c.below()) {
                        cells.add(c);
                    }
                }
                return MultiblockCore.House.Survey.ok(cells, b);
            }
            return MultiblockCore.House.Survey.fail("steam.pioneers.boiler." + (b.problem().isEmpty() ? "unformed" : b.problem()),
                  b.at() != null ? b.at() : worldPosition.above(), b);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            apply((ServerLevel) level, (Boiler) survey.data());
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            if (!survey.removal()) {
                apply((ServerLevel) level, (Boiler) survey.data());
            }
        }

        @Override
        public void houseChanged() {
            markClient();
        }
    }, 40);

    public MultiblockCore.House house() {
        return house;
    }

    /** Lot 8a : applique un releve (pieces prises et liberees, volume de vapeur, contenance) : crochets du cycle commun. */
    private void apply(ServerLevel level, Boiler b) {
        Set<BlockPos> found = b.found();
        int s = b.shells(), d = b.domes(), best = b.chimney();
        //1.0.5-beta (lot 29) : releve identique au precedent (chaudiere inchangee, faite ou non) : rien a renvoyer au client ni a sauver (avant :
        //envoi et sauvegarde a chaque releve, toutes les 2 s, meme chaudiere froide et inchangee)
        double steamBefore = steam.steam(), capacityBefore = steam.capacity(), waterBefore = water;
        boolean same = parts.equals(found) && chimneyTops.equals(b.chimneyTops()) && shells == s && domes == d && chimney == best
                       && java.util.Objects.equals(problem, b.problem());
        problem = b.problem();
        //Pieces liberees
        for (BlockPos old : parts) {
            if (!found.contains(old) && level.getBlockEntity(old) instanceof BoilerPartTile tile && worldPosition.equals(tile.controllerPos())) {
                tile.setController(null);
            }
        }
        parts.clear();
        parts.addAll(found);
        chimneyTops.clear();
        chimneyTops.addAll(b.chimneyTops());
        for (BlockPos p : found) {
            if (level.getBlockEntity(p) instanceof BoilerPartTile tile) {
                tile.setController(worldPosition);
            }
        }
        shells = s;
        domes = d;
        chimney = best;
        //Reserve reduite (piece cassee, chaudiere defaite) : la vapeur qui n'a plus de place s'echappe
        double after = s == 0 ? 1 : b.reserve() + 1;
        if (steam.steam() > after) {
            double vented = steam.take(steam.steam() - after);
            if (vented > 20) {
                level.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, 12, 0.4, 0.3, 0.4,
                      0.08);
                level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.8F);
            }
        }
        steam.setCapacity(after);
        if (s > 0) {
            water = Math.min(water, waterCapacity());
        }
        if (!same || steam.steam() != steamBefore || steam.capacity() != capacityBefore || water != waterBefore) {
            markClient();
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void serverTick(ServerLevel level) {
        house.tick();
        temperatureBefore = temperature;
        double draft = draft();
        //1.0.3-beta (lot 7) : surete de chaudiere : foyer en veille (le feu en cours ne brule plus, rien ne s'allume) tant que l'eau est basse
        //1.0.3-beta (lot 14) : CORRECTIF « impossible de demarrer une chaudiere » : l'ancien test {@code now - cutoffAt <= 2} debordait avec
        //cutoffAt = Long.MIN_VALUE (jamais coupee) : now - MIN_VALUE passe en negatif, donc TOUTE chaudiere formee etait « coupee »
        cut = formed() && SteamRules.cutActive(level.getGameTime(), cutoffAt);
        boolean wanted = enabled && formed() && !cut;
        boolean ashDrop = !cut && hearth.tickExact(fuel, wanted, draft);       //1.0.3-beta (lot 14) : cadence exacte (tirage)
        if (ashDrop) {
            ash.insert(new ItemStack(PiMechanics.WOOD_ASH.get()), Action.EXECUTE, Automation.INTERNAL);
        }
        double heat = hearth.lit() && formed() && !cut ? HEAT * draft * (1 + hearth.bonus() / 100.0) : 0;
        //1.0.2-beta (lot 3) : physique sans pression (SteamRules.boil) : ebullition a 100 C, production tant que la reserve a de la place
        double[] state = {temperature, formed() ? water : 0, steam.steam()};
        made = SteamRules.boil(state, heat, shells, formed() ? steam.capacity() : 0);
        temperature = state[0];
        if (made > 0) {
            water = state[1];
            double wet = water > waterCapacity() * 0.92 ? 0.5 : 0;
            steam.add(made, wet);
            if (steam.steam() >= 1000 && level.getGameTime() % 200 == 0) {
                BronzeEvents.award(level, worldPosition, "boiler");
            }
        } else if (formed() && temperature < SteamRules.BOIL_C - 1 && steam.steam() > 0) {
            //La chaudiere refroidit : la vapeur se recondense
            double back = steam.take(Math.min(steam.steam(), (SteamRules.BOIL_C - temperature) * 4));
            water = Math.min(waterCapacity(), water + back / Steam.STEAM_PER_WATER);
        }
        if (water <= waterCapacity() * 0.92) {
            steam.dry(0.002);
        }
        //0.20.40 : les domes poussent la vapeur dans les cables et ports a gaz voisins
        if (formed() && steam.steam() >= 1) {
            for (BlockPos dome : parts) {
                if (level.isLoaded(dome) && level.getBlockState(dome).getBlock() instanceof BoilerPartBlock b && b.part == BoilerPart.STEAM_DOME) {
                    for (Direction d : Directions.ALL) {
                        if (d != Direction.DOWN && !parts.contains(dome.relative(d))) {
                            if (SteamGas.eject(level, dome, d, steam) > 0 && level.getGameTime() % 200 == 0) {
                                BronzeEvents.award(level, worldPosition, "steam_cable");
                            }
                        }
                    }
                }
            }
        }
        temperature = Math.max(20, temperature);
        dangers(level, heat);
        if (isRemoved() || level.getBlockEntity(worldPosition) != this) {
            return;                                                  //La chaudiere vient de sauter
        }
        if (hearth.lit() && !cut && level.getGameTime() % 6 == 0) {
            for (BlockPos top : chimneyTops) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, top.getX() + 0.5, top.getY() + 0.1, top.getZ() + 0.5, 0, 0, 0.08, 0, 1);
            }
        }
        LitBronzeBlock.setLit(level, worldPosition, getBlockState(), hearth.lit() && !cut);
        //1.0.4-beta (lot 24) : les foyers en briques refractaires de la chaudiere flambent quand elle brule
        paintFire(level, formed() && hearth.lit() && !cut ? 2 : 0);
        //1.0.5-beta (lot 29) : sauvegarde et envoi au client seulement si l'etat a change (avant : chaque seconde, meme chaudiere froide)
        if (level.getGameTime() % 20 == 0 && stateChanged()) {
            setChanged();
            markClient();
        }
        flushClient(level, 20);
    }

    /**
     * 1.0.2-beta (lot 3) : SEUL danger, chauffer sans eau ({@link SteamRules#dry}) : avertissement, usure ({@link SteamRules#wear}), puis a
     * {@link SteamRules#BURST_DAMAGE} l'explosion (config ON), ou le feu s'eteint (WARN) ; OFF : rien. Eau basse (sous 15 %) : avertissement
     * seulement. Reserve pleine : un filet de vapeur s'echappe des domes (rien ne casse).
     */
    private void dangers(ServerLevel level, double heat) {
        if (!formed()) {
            return;
        }
        //1.0.3-beta (correctif, Guillaume : « que la chaudiere fasse du bruit moins regulierement ») : reserve pleine, la soupape siffle a
        //intervalles irreguliers (6 a 18 s) et pas toujours sur la meme note, au lieu de toutes les 2 s pile
        if (steam.full() && heat > 0 && level.getGameTime() >= nextHiss) {
            if (nextHiss != 0) {
                hiss(level, 0.85F + level.random.nextFloat() * 0.3F);
            }
            nextHiss = level.getGameTime() + 120 + level.random.nextInt(241);
        }
        KineticsConfig.Breakage rule = KineticsConfig.boilerExplosion();
        boolean dry = SteamRules.dry(water, heat > 0);
        if (dry && rule != KineticsConfig.Breakage.OFF) {
            damage += SteamRules.wear(heat);
            if (level.getGameTime() % 20 == 0) {
                warn(level, "steam.pioneers.boiler.dry");
                level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1F, 0.6F);
            }
            if (damage >= SteamRules.BURST_DAMAGE) {
                if (rule == KineticsConfig.Breakage.ON) {
                    explode(level, "steam.pioneers.boiler.burnt");
                } else {
                    enabled = false;
                    damage = SteamRules.BURST_DAMAGE / 2;
                    warn(level, "steam.pioneers.boiler.fire_out");
                    setChanged();
                }
            }
        } else {
            if (heat > 0 && water < waterCapacity() * SteamRules.LOW_WATER && level.getGameTime() % 40 == 0) {
                warn(level, "steam.pioneers.boiler.low");
            }
            damage = Math.max(0, damage - 0.005);
        }
    }

    private void hiss(ServerLevel level, float pitch) {
        for (BlockPos p : parts) {
            if (level.getBlockState(p).getBlock() instanceof BoilerPartBlock b && b.part == BoilerPart.STEAM_DOME) {
                level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.06);
                level.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, pitch);
            }
        }
    }

    private void warn(ServerLevel level, String key) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(16))) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

    /** La chaudiere saute : pieces detruites, explosion a la mesure de sa taille et de la vapeur qu'elle tient. */
    void explode(ServerLevel level, String key) {
        warn(level, key);
        BronzeEvents.award(level, worldPosition, "boiler_explosion");
        double power = Math.min(7, 2.5 + steam.fill() * 2 + shells * 0.25);
        for (BlockPos p : new ArrayList<>(parts)) {
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        parts.clear();
        steam.clear();
        water = 0;
        BlockPos at = worldPosition.above();
        level.setBlockAndUpdate(worldPosition, Blocks.AIR.defaultBlockState());
        level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, (float) power, Level.ExplosionInteraction.BLOCK);
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    public Component summary() {
        if (!formed()) {
            return Component.translatable("steam.pioneers.boiler." + (problem.isEmpty() ? "unformed" : problem)).withStyle(ChatFormatting.RED);
        }
        return Component.translatable("steam.pioneers.boiler.summary", fr.pioneers.core.Texts.format(Math.round(steam.steam())),
              fr.pioneers.core.Texts.format(Math.round(steam.capacity())), Math.round(water * 100 / Math.max(1, waterCapacity())) + " %",
              Math.round(temperature)).withStyle(ChatFormatting.AQUA);
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == TOGGLE) {
            enabled = !enabled;
            setChanged();
        }
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    /** Lot 8a : interface standard v2 (inventaire dans le cadre, onglets a droite, Infos et Structure). */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public java.util.List<StandardLayout.Part> guiParts() {
        return StandardLayout.ageService(false, StandardLayout.framed("gauge", WATER), new StandardLayout.Part("dial", DIAL),
              new StandardLayout.Part("setting", FIRE), new StandardLayout.Part("card", CARD));
    }

    @Override
    public UiValues uiValues() {
        return namedIndices(UiValues.HEAT, UiValues.WATER, UiValues.WATER_MAX, UiValues.STEAM, UiValues.STEAM_MAX, UiValues.BURN, DRAFT, MADE, DAMAGE, SHELLS, DOMES,
              CHIMNEY, UiValues.WET, ENABLED, PROBLEM, BURN_LEFT, BONUS);
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    /**
     * 0 temperature x10, 1 eau, 2 capacite, 3 vapeur (mB ; pression x1000 avant le lot 3 du 1.0.2-beta), 4 reserve (mB ; pression max), 5 feu x1000, 6 tirage x100, 7 vapeur produite x100 (mB/t),
     * 8 degats x10, 9 corps, 10 domes, 11 cheminee, 12 humidite x1000, 13 marche (2 : coupee par la surete, 1.0.3-beta lot 7), 14 probleme (index),
     * 15 feu restant de l'objet en cours (s, au tirage actuel), 16 bonus du combustible en cours (%) (1.0.3-beta lot 14).
     */
    @Override
    public int guiValueCount() {
        return 17;
    }

    public static final List<String> PROBLEMS = List.of("", "unformed", "no_shell", "no_dome", "too_big", "shape");

    @Override
    public long guiValue(int index) {
        return switch (index) {
            case 0 -> Math.round(temperature * 10);
            case 1 -> Math.round(water);
            case 2 -> waterCapacity();
            case 3 -> Math.round(steam.steam());
            case 4 -> Math.round(steam.capacity());
            case 5 -> Math.round(hearth.ratio() * 1000);
            case 6 -> Math.round(draft() * 100);
            case 7 -> Math.round(made * 100);
            case 8 -> Math.round(damage * 10);
            case 9 -> shells;
            case 10 -> domes;
            case 11 -> chimney;
            case 12 -> Math.round(steam.wet() * 1000);
            case 13 -> enabled ? cut ? 2 : 1 : 0;                         //1.0.3-beta (lot 7) : 2 = coupee par la surete
            case 15 -> Math.round(hearth.remaining() / Math.max(0.01, draft()) / 20);
            case 16 -> hearth.lit() ? hearth.bonus() : 0;
            default -> Math.max(0, PROBLEMS.indexOf(problem));
        };
    }

    /** 1.0.5-beta (lot 29) : etat sauve et envoye au dernier envoi periodique (champs de {@link #saveAdditional} et de {@link #writeClient}). */
    private final double[] sent = new double[12], now = new double[12];
    private boolean sentOnce;

    /**
     * Vrai (et retenu) si un champ sauve ou envoye a change depuis le dernier envoi periodique : eau, vapeur (et humidite), temperature,
     * usure, marche, corps, domes, contenance, foyer (reste, part brulee, bonus). La forme (releve) a son propre envoi ({@code houseChanged}).
     */
    private boolean stateChanged() {
        double[] n = now;
        n[0] = water;
        n[1] = steam.steam();
        n[2] = steam.wet();
        n[3] = temperature;
        n[4] = damage;
        n[5] = enabled ? 1 : 0;
        n[6] = shells;
        n[7] = domes;
        n[8] = waterCapacity();
        n[9] = hearth.remaining();
        n[10] = hearth.ratio();
        n[11] = hearth.bonus();
        if (sentOnce && java.util.Arrays.equals(n, sent)) {
            return false;
        }
        System.arraycopy(n, 0, sent, 0, n.length);
        sentOnce = true;
        return true;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        house.write(tag);
        tag.putDouble("water", water);
        tag.putInt("capacity", waterCapacity());
        tag.putDouble("steam", steam.steam());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        house.read(tag);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        hearth.save(tag);
        steam.save(tag);
        tag.putDouble("water", water);
        tag.putDouble("temperature", temperature);
        tag.putDouble("damage", damage);
        tag.putBoolean("enabled", enabled);
        tag.putInt("shells", shells);
        tag.putInt("domes", domes);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        hearth.load(tag);
        shells = tag.getInt("shells");
        domes = tag.getInt("domes");
        //Reserve d'apres les pieces sauvees (nombre seulement, au plus large : corps d'acier) ; le releve au chargement la ramene a la vraie
        steam.setCapacity(shells == 0 ? 1 : shells * SteamRules.STEEL_SHELL_STEAM + domes * SteamRules.DOME_STEAM + 1);
        steam.load(tag);
        water = tag.getDouble("water");
        temperature = tag.contains("temperature") ? tag.getDouble("temperature") : 20;
        damage = tag.getDouble("damage");
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
    }

    /**
     * Lot 8c : CONTRAT DE PORT COMMUN ({@link MultiblockPort#portHandler}) : un port commun pose dans la structure formee prete la capacite
     * interne de la maison ({@link MultiblockPort#capabilityOf}) ; les ports propres de la maison gardent leurs gestionnaires.
     */
    @Nullable
    @Override
    public Object portHandler(fr.pioneers.tech.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }

    /** 1.0.4-beta (lot 25) : particules de la structure en marche (T:industry/nbt_models.py HOUSE_FX). */
    @Override
    public void clientTick() {
        houseFx("bronze_boiler", lit());
    }
}
