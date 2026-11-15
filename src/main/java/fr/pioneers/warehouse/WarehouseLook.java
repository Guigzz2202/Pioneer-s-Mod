package fr.pioneers.warehouse;

import fr.pioneers.common.look.LookCards;
import fr.pioneers.core.Texts;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.FarmCard.Line;
import fr.pioneers.kinetics.rope.PulleyTile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.0.4-beta (lot 1) : FICHES AU REGARD de l'entrepot (calculees par le serveur, {@link LookCards}) : la caisse (n'importe quelle piece) :
 * taille, remplissage, types, les objets les plus nombreux, station du reseau ; le comptoir : corde, colis, demandes. C'est la seule
 * « fiche » du contenu pour le joueur : aucune interface d'inventaire sur la caisse.
 */
public final class WarehouseLook {

    private WarehouseLook() {
    }

    public static void init() {
        LookCards.register(s -> s.getBlock() instanceof CrateBlock, (level, pos, state, player, sneaking) -> crate(level, pos, state));
        LookCards.register(s -> s.getBlock() instanceof CounterBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof CounterTile t ? counter(state, t) : null);
    }

    private static FarmCard crate(Level level, BlockPos pos, BlockState state) {
        CrateHatchTile host = CrateBlock.host(level, pos);
        List<Line> lines = new ArrayList<>();
        if (host == null || !host.formed()) {
            lines.add(Line.text(Component.translatable("card.pioneers.warehouse.unformed"), FarmCard.WARN));
            lines.add(Line.hint(Component.translatable("card.pioneers.warehouse.unformed_hint")));
            return new FarmCard(new ItemStack(state.getBlock()), state.getBlock().getName(), 0xB08A50, lines);
        }
        CrateStorage s = host.storage();
        float fill = (float) s.fill();
        lines.add(Line.pair(FarmCard.label("warehouse.size"), Component.literal(host.length().size()), FarmCard.INFO));
        lines.add(Line.gauge(FarmCard.label("warehouse.fill"), fill, FarmCard.percent(fill), fill > 0.9F ? FarmCard.BAD : fill > 0.7F ? FarmCard.WARN : FarmCard.OK));
        lines.add(Line.pair(FarmCard.label("warehouse.items"), Component.literal(Texts.format(s.total())), FarmCard.INFO));
        //1.0.5-beta (lot 22) : cases de type occupees (un objet plein deborde dans les cases libres), et le nombre d'objets differents
        lines.add(Line.pair(FarmCard.label("warehouse.slots"), FarmCard.ratio(s.used(), s.maxTypes()), s.used() >= s.maxTypes() ? FarmCard.WARN : FarmCard.INFO));
        lines.add(Line.pair(FarmCard.label("warehouse.types"), Component.literal(Texts.format(s.types())), FarmCard.INFO));
        List<CrateStorage.Entry> top = new ArrayList<>(s.entries());
        top.sort(Comparator.comparingLong(CrateStorage.Entry::count).reversed());
        List<ItemStack> icons = new ArrayList<>();
        for (int i = 0; i < Math.min(6, top.size()); i++) {
            icons.add(top.get(i).proto().copyWithCount((int) Math.min(999, top.get(i).count())));
        }
        if (!icons.isEmpty()) {
            lines.add(Line.items(icons));
        }
        PulleyTile st = host.station();
        lines.add(st == null ? Line.text(Component.translatable("card.pioneers.warehouse.no_station"), FarmCard.DIM)
                             : Line.pair(FarmCard.label("warehouse.station"), Component.translatable("card.pioneers.warehouse.ropes", st.ropeCount()),
                                   st.ropeCount() == 0 ? FarmCard.WARN : FarmCard.OK));
        lines.add(Line.hint(Component.translatable("card.pioneers.warehouse.hint")));
        return new FarmCard(new ItemStack(PiWarehouse.CRATE_HATCH.get()), Component.translatable("card.pioneers.warehouse.crate"), 0xB08A50, lines);
    }

    private static FarmCard counter(BlockState state, CounterTile t) {
        List<Line> lines = new ArrayList<>();
        CounterUnit u = t.counter();
        PulleyTile st = t.station();
        if (st == null) {
            lines.add(Line.text(Component.translatable("card.pioneers.warehouse.no_station"), FarmCard.WARN));
        } else {
            double v = u.ropeSpeed() * 20;
            lines.add(Line.pair(FarmCard.label("warehouse.rope"), v <= 0 ? Component.translatable("card.pioneers.warehouse.still")
                                                                         : Component.translatable("card.pioneers.warehouse.speed", String.format(java.util.Locale.ROOT, "%.2f", v)),
                  v <= 0 ? FarmCard.BAD : FarmCard.OK));
            lines.add(Line.pair(FarmCard.label("warehouse.crates"), Component.literal(String.valueOf(u.graph().hatches(t.getLevel()).size())), FarmCard.INFO));
        }
        lines.add(Line.pair(FarmCard.label("warehouse.parcels"), FarmCard.ratio(u.parcels().size(), CounterUnit.MAX_PARCELS), FarmCard.INFO));
        if (!u.queue().isEmpty()) {
            lines.add(Line.pair(FarmCard.label("warehouse.queue"), Component.literal(String.valueOf(u.queue().size())), FarmCard.INFO));
        }
        //1.0.5-beta (lot 17) : interfaces d'entree et de sortie : ce qui attend, les regles ; pour tous : pourquoi ca attend
        if (t.role() != CounterTile.Role.COUNTER) {
            int held = 0;
            for (var slot : t.outputs()) {
                held += slot.getStack().getCount();
            }
            lines.add(Line.pair(FarmCard.label(t.role() == CounterTile.Role.INLET ? "warehouse.waiting" : "warehouse.held"), Component.literal(Texts.format(held)),
                  FarmCard.INFO));
            if (t.role() == CounterTile.Role.OUTLET) {
                lines.add(Line.pair(FarmCard.label("warehouse.keeps"), FarmCard.ratio(u.keeps().size(), CounterUnit.MAX_KEEPS), FarmCard.INFO));
            }
        }
        int wait = u.waitCode();
        if (wait != CounterUnit.W_NONE) {
            lines.add(Line.text(Component.translatable("gui.pioneers.warehouse.wait." + CounterUnit.WAIT_KEYS[wait]), FarmCard.WARN));
        }
        return new FarmCard(new ItemStack(state.getBlock()), state.getBlock().getName(), 0xB08A50, lines);
    }
}
