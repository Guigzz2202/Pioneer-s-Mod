package fr.pioneers.logistics.rail.block;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.Action;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.logistics.rail.logistics.Cargo;
import fr.pioneers.logistics.rail.logistics.PostPoint;
import fr.pioneers.logistics.rail.logistics.StationPost;
import fr.pioneers.logistics.rail.PiRail;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.0 : quai a objets (MKI a MKIV), pose le long du quai : il charge (ou decharge) le vehicule arrete devant lui, sur n'importe quelle
 * face qui touche la voie. Tampon de 9 cases relie aux cables et coffres par ses faces (interface standard). Debit 16 / 64 / 256 /
 * 1 024 objets par seconde. Le combustible charge dans une locomotive ou un tender va dans ses cases de combustible.
 * <p>
 * 0.20.2 : la meme tile sert tous les postes a objets ({@link PostKind#items()}) : silo (charge les tremies), fosse sous la voie (les
 * decharge), charbonnier (combustible de la vapeur), grue de quai (bloc du wagon plat), poste blinde (assemblages neufs de la locomotive
 * nucleaire, reprise des uses).
 * <p>
 * Interface : 4 mode (0 charger, 1 decharger), 5 vehicule devant (0 non, 1 oui), 6 objets passes depuis la pose, 7 sorte du poste.
 * Action {@link #MODE}.
 * <p>
 * 0.20.5 : dans une gare logistique, le role de la gare (ou la livraison en cours) decide : charger ou decharger, quelle ressource et
 * combien ; le tampon compte dans le stock de la gare et les robots du reseau le remplissent ou le vident ({@link PostPoint}).
 * <p>
 * API Machine (lot 8c) : logique dans {@link ItemLoaderUnit} (fiches {@code migrated}) ; cette tile la porte ({@link #carry}) et garde le
 * point de releve de la gare, le stock et les capacites.
 */
public class ItemLoaderTile extends MachineTile implements StationPost {

    public static final int MODE = CUSTOM;

    /** Point de releve du poste (gare logistique) ; lu par l'unite. */
    final PostPoint point;
    /** API Machine (lot 8c) : la logique (tampon, debit, vehicule servi, plan de la gare) est dans l'unite portee. */
    private final ItemLoaderUnit unit;

    public ItemLoaderTile(BlockPos pos, BlockState state) {
        super(PiRail.ITEM_LOADER_TILE.get(), pos, state, 0, 0);
        point = new PostPoint(this, RailPostBlock.kindOf(state) == PostKind.PIT ? Direction.DOWN : Direction.UP);
        unit = carry(new ItemLoaderUnit(this, access()));
    }

    /** Toutes les faces ouvertes aux cables et coffres des la pose. */
    @Override
    protected SideMode initialMode() {
        return SideMode.IO;
    }

    @Override
    protected void tick() {
    }

    public PostKind kind() {
        return RailPostBlock.kindOf(getBlockState());
    }

    @Override
    public void addStock(Map<Cargo, Long> out) {
        for (ItemSlot s : unit.buffer) {
            if (!s.isEmpty()) {
                out.merge(Cargo.item(Cargo.itemId(s.getStack())), (long) s.getStack().getCount(), Long::sum);
            }
        }
    }

    @Override
    public Set<Cargo.Kind> holds() {
        PostKind k = kind();
        return k == PostKind.ITEM || k == PostKind.SILO || k == PostKind.PIT || k == PostKind.CRANE ? EnumSet.of(Cargo.Kind.ITEM)
              : EnumSet.noneOf(Cargo.Kind.class);
    }

    /** 0.20.46 (audit) : poste connu du releve des quais des son chargement. */
    @Override
    public void onLoad() {
        super.onLoad();
        point.loaded(level);
    }

    @Override
    public void setRemoved() {
        point.remove(level);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        point.remove(level);
        super.onChunkUnloaded();
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == MODE && kind().reversible()) {
            unit.setUnload(!unit.unload);
        }
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.postParts(0, true, kind().reversible());
    }

    @Override
    public String guiTier() {
        return kind().tiered ? TieredMachineBlock.TIERS[TieredMachineBlock.tierOf(getBlockState())] : "steel";
    }

    /** Sens du poste (vider le vehicule). */
    public boolean unloads() {
        return unit.unload;
    }

    public void setUnloads(boolean value) {
        unit.setUnload(value);
    }
}
