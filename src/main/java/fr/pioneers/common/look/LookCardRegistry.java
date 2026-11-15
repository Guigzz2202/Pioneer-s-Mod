package fr.pioneers.common.look;

import fr.pioneers.bronze.foundry.Metals;
import fr.pioneers.farm.FarmCard.Line;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.crustacean.CrustaceanLookCards;
import fr.pioneers.forge.MoltenLoad;
import fr.pioneers.forge.automation.QuenchBasinTile;
import fr.pioneers.forge.casting.ClayCrucibleBlock;
import fr.pioneers.forge.casting.ClayCrucibleTile;
import fr.pioneers.forge.casting.HandBellowsBlock;
import fr.pioneers.forge.casting.PlacedMoldBlock;
import fr.pioneers.forge.casting.PlacedMoldTile;
import fr.pioneers.forge.smithing.AnvilBlock;
import fr.pioneers.forge.smithing.AnvilTile;
import fr.pioneers.forge.smithing.ForgeHearthBlock;
import fr.pioneers.forge.smithing.ForgeHearthTile;
import fr.pioneers.forge.smithing.QuenchBasinBlock;
import fr.pioneers.kinetics.KineticBlock;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.mechanics.HandSieveBlock;
import fr.pioneers.mechanics.WoodenHopperBlock;
import fr.pioneers.mechanics.charcoal.CharcoalPits;
import fr.pioneers.mechanics.charcoal.ClayDrainBlock;
import fr.pioneers.nature.ResinTapBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.4 : declaration des fiches au regard calculees par le serveur ({@link LookCards}) : pieces et sources de force rotative, tamis a main,
 * entonnoir en bois, charbonniere, saignee, creuset d'argile, soufflet a main, moule pose, foyer de forge, enclume, bac de trempe. Appele a la
 * construction du mod (client et serveur). 1.6 : fiches synthetiques (libelles courts {@code card.pioneers.*}, valeurs alignees, au plus une
 * indication).
 */
public final class LookCardRegistry {

    private LookCardRegistry() {
    }

    private static boolean done;

    public static void init() {
        if (done) {
            return;
        }
        done = true;
        CrustaceanLookCards.init();      //1.4 : filet de peche, casier a crabe
        fr.pioneers.warehouse.WarehouseLook.init();      //1.0.4-beta (lot 1) : caisse et comptoir d'entrepot
        fr.pioneers.lighting.LightLook.init();           //1.0.4-beta (lot 5) : interrupteurs et lumieres
        fr.pioneers.farm.greenhouse.GreenhouseLook.init();   //1.0.5-beta (lot 3) : regulateur de serre
        //Force rotative : toutes les pieces (arbres, engrenages, sources, machines...)
        LookCards.register(s -> s.getBlock() instanceof KineticBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof KineticTile tile ? tile.lookCard(sneaking) : null);
        //Charbonniere : buches d'une fosse allumee ou d'une vraie meule (1.6 : plus les troncs d'arbre, voir CharcoalPits.card)
        LookCards.register(s -> s.is(BlockTags.LOGS_THAT_BURN), (level, pos, state, player, sneaking) -> CharcoalPits.card(level, pos));
        LookCards.register(s -> s.getBlock() instanceof ClayDrainBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof ClayDrainBlock.Tile t ? card(state, 0x8A5A30,
                    Line.gauge(FarmCard.label("creosote"), t.creosote() / (float) t.capacity(), FarmCard.millibuckets(t.creosote()), 0x6A4A2A),
                    Line.hint(Component.translatable("look.pioneers.drain.hint"))) : null);
        LookCards.register(s -> s.getBlock() instanceof HandSieveBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof HandSieveBlock.Tile t ? sieve(state, t) : null);
        LookCards.register(s -> s.getBlock() instanceof WoodenHopperBlock, (level, pos, state, player, sneaking) -> hopper(state));
        LookCards.register(s -> s.getBlock() instanceof ResinTapBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof ResinTapBlock.Tile t ? card(state, 0xC89A5A, t.lookLines()) : null);
        LookCards.register(s -> s.getBlock() instanceof ClayCrucibleBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof ClayCrucibleTile t ? crucible(state, t, sneaking) : null);
        LookCards.register(s -> s.getBlock() instanceof HandBellowsBlock, (level, pos, state, player, sneaking) ->
              card(state, 0xC89A5A, Line.hint(Component.translatable("look.pioneers.bellows.hint"))));
        LookCards.register(s -> s.getBlock() instanceof PlacedMoldBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof PlacedMoldTile t ? mold(state, t) : null);
        LookCards.register(s -> s.getBlock() instanceof ForgeHearthBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof ForgeHearthTile t ? hearth(state, t) : null);
        LookCards.register(s -> s.getBlock() instanceof AnvilBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof AnvilTile t ? anvil(state, t) : null);
        LookCards.register(s -> s.getBlock() instanceof QuenchBasinBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof QuenchBasinTile t ? quench(state, t) : null);
    }

    // ------------------------------------------------------------------ outils

    static FarmCard card(BlockState state, int accent, Line... lines) {
        return card(state, accent, new ArrayList<>(List.of(lines)));
    }

    static FarmCard card(BlockState state, int accent, List<Line> lines) {
        return new FarmCard(new ItemStack(state.getBlock()), state.getBlock().getName(), accent, lines);
    }

    /** Jauge de temperature (0 .. {@code max} degres C) : bleu froid, orange, rouge vif. */
    static Line heat(float c, float max) {
        float r = c / max;
        return Line.gauge(FarmCard.label("heat"), r, Component.translatable("look.pioneers.temperature", Math.round(c)),
              r > 0.75F ? 0xFF6A30 : r > 0.4F ? 0xF0A040 : 0x8AA0B8);
    }

    static Component stack(ItemStack s) {
        return FarmCard.stack(s);
    }

    // ------------------------------------------------------------------ fiches (1.6 : titre + 2 a 4 lignes courtes)

    private static FarmCard sieve(BlockState state, HandSieveBlock.Tile t) {
        List<Line> lines = new ArrayList<>();
        lines.add(t.mesh.isEmpty() ? Line.pair(FarmCard.label("mesh"), FarmCard.word("none_f"), FarmCard.WARN)
                                   : Line.pair(FarmCard.label("mesh"), t.mesh.getHoverName(), FarmCard.INFO));
        if (t.sediment.isEmpty()) {
            lines.add(Line.pair(FarmCard.label("contents"), FarmCard.word("empty"), FarmCard.DIM));
        } else {
            lines.add(Line.pair(FarmCard.label("contents"), stack(t.sediment), FarmCard.INFO));
            lines.add(Line.gauge(FarmCard.label("shakes"), t.shakes / (float) HandSieveBlock.SHAKES, FarmCard.ratio(t.shakes, HandSieveBlock.SHAKES), 0x6EDC5A));
        }
        lines.add(Line.hint(Component.translatable("look.pioneers.sieve.hint")));
        return card(state, 0xC89A5A, lines);
    }

    private static FarmCard hopper(BlockState state) {
        boolean on = state.getValue(WoodenHopperBlock.ENABLED);
        return card(state, 0xC89A5A, on
              ? Line.pair(FarmCard.label("output"), Component.translatable("look.pioneers.dir." + state.getValue(WoodenHopperBlock.FACING).getName()), FarmCard.OK)
              : Line.pair(FarmCard.label("state"), Component.translatable("look.pioneers.hopper.off"), FarmCard.BAD));
    }

    private static FarmCard crucible(BlockState state, ClayCrucibleTile t, boolean sneaking) {
        List<Line> lines = new ArrayList<>();
        MoltenLoad load = t.load();
        lines.add(heat(t.temperature(), 1600));
        if (!t.onFire()) {
            lines.add(Line.pair(FarmCard.label("fire"), FarmCard.word("none"), FarmCard.WARN));
        }
        if (!load.isEmpty()) {
            String metal = load.metal();
            Component name = Metals.nameOrMixed(metal);
            lines.add(Line.gauge(name, load.total() / (float) MoltenLoad.CLAY_CRUCIBLE_MB, FarmCard.millibuckets(load.total()),
                  0xFF000000 | Metals.colour(metal.isEmpty() ? "iron" : metal)));
            if (metal.isEmpty()) {
                //1.0.3-beta (lot 7b) : bain qui n'est pas exactement un alliage : ce qui coulera et ce qui restera (regle commune)
                Metals.Plan plan = load.plan(m -> 0);
                lines.add(Line.text(Metals.pourLine(plan), FarmCard.WARN));
                lines.add(Line.text(Metals.restLine(plan), FarmCard.DIM));
            }
            if (sneaking && load.mix().size() > 1) {
                //Accroupi : composition de l'alliage sur une ligne
                int total = load.total();
                MutableComponent mix = Component.empty();
                for (Map.Entry<String, Integer> e : load.mix().entrySet()) {
                    if (!mix.getSiblings().isEmpty()) {
                        mix.append(" · ");
                    }
                    mix.append(Metals.name(e.getKey())).append(String.format(Locale.ROOT, " %d %%", Math.round(e.getValue() * 100F / total)));
                }
                lines.add(Line.text(mix, FarmCard.DIM));
            }
            lines.add(t.molten() ? Line.pair(FarmCard.label("pour"), FarmCard.word("ready_f"), FarmCard.OK)
                                 : Line.pair(FarmCard.label("pour"), Component.translatable("look.pioneers.temperature", load.pourC()), FarmCard.WARN));
        }
        if (!t.solids().isEmpty()) {
            lines.add(Line.items(t.solids()));
        }
        if (load.isEmpty() && t.solids().isEmpty()) {
            lines.add(Line.hint(Component.translatable("look.pioneers.crucible.hint")));
        }
        return card(state, 0xE07040, lines);
    }

    private static FarmCard mold(BlockState state, PlacedMoldTile t) {
        List<Line> lines = new ArrayList<>();
        if (t.amount() <= 0) {
            lines.add(Line.hint(Component.translatable("look.pioneers.mold.empty")));
        } else {
            Component name = Metals.nameOrMixed(t.metal());
            int cap = Math.max(t.amount(), t.capacity());
            lines.add(Line.gauge(name, t.amount() / (float) Math.max(1, cap), FarmCard.ratio(t.amount(), cap), 0xF0A040));
            lines.add(t.done() ? Line.pair(FarmCard.label("state"), Component.translatable("look.pioneers.mold.done"), FarmCard.OK)
                               : Line.gauge(FarmCard.label("cooling"), t.solid(0), FarmCard.percent(t.solid(0)), 0x8AA0B8));
        }
        //Titre : le moule pose (son objet), sinon le bloc
        ItemStack mold = t.mold();
        return mold.isEmpty() ? card(state, 0xE07040, lines) : new FarmCard(mold.copyWithCount(1), mold.getHoverName(), 0xE07040, lines);
    }

    private static FarmCard hearth(BlockState state, ForgeHearthTile t) {
        List<Line> lines = new ArrayList<>();
        lines.add(heat(t.temperature(), 1600));
        lines.add(t.isLit() ? Line.gauge(FarmCard.label("embers"), t.burnRatio(), FarmCard.percent(t.burnRatio()), 0xF0A040)
                            : Line.pair(FarmCard.label("fire"), Component.translatable("look.pioneers.hearth.out"), FarmCard.WARN));
        if (!t.hotPiece().isEmpty()) {
            lines.add(Line.pair(FarmCard.label("in_fire"), t.hotPiece().getHoverName(), 0xFF8A50));
        } else if (!t.waiting().isEmpty()) {
            lines.add(Line.pair(FarmCard.label("waiting"), stack(t.waiting()), 0xC0C0C0));
        }
        if (!t.fuelStack().isEmpty()) {
            lines.add(Line.pair(FarmCard.label("fuel"), stack(t.fuelStack()), FarmCard.INFO));
        } else if (!t.isLit()) {
            lines.add(Line.hint(Component.translatable("look.pioneers.hearth.hint")));
        }
        return card(state, 0xE07040, lines);
    }

    private static FarmCard anvil(BlockState state, AnvilTile t) {
        if (t.piece().isEmpty()) {
            return card(state, 0xA0A8B0, Line.hint(Component.translatable("look.pioneers.anvil.empty")));
        }
        return card(state, 0xA0A8B0, Line.pair(FarmCard.label("piece"), t.piece().getHoverName(), 0xFF8A50),
              Line.hint(Component.translatable("look.pioneers.anvil.hint")));
    }

    private static FarmCard quench(BlockState state, QuenchBasinTile t) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.gauge(FarmCard.label("water"), t.water() / (float) QuenchBasinTile.CAPACITY, FarmCard.ratio(t.water(), QuenchBasinTile.CAPACITY),
              FarmCard.WATER));
        if (!t.inputPiece().isEmpty()) {
            lines.add(Line.gauge(FarmCard.label("quench"), t.progress() / (float) QuenchBasinTile.QUENCH_TICKS, t.inputPiece().getHoverName(), 0x8AA0B8));
        }
        if (!t.outputPiece().isEmpty()) {
            lines.add(Line.pair(FarmCard.label("ready"), stack(t.outputPiece()), FarmCard.OK));
        }
        return card(state, 0x4A8AF0, lines);
    }
}
