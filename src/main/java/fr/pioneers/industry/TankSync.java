package fr.pioneers.industry;

import fr.pioneers.api.gas.Gas;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.menu.PiTileMenu;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * 0.20.46 (audit) : envoi des reservoirs d'une machine au client economise. Avant, chaque variation d'un reservoir demandait un renvoi
 * (un paquet toutes les 10 ticks par machine en marche, a tous les joueurs proches) alors que seule l'interface lit ces valeurs.
 * Maintenant : renvoi toutes les 10 ticks seulement si un joueur a l'interface de la machine ouverte ; sinon, comme les buffers
 * ({@code BufferTile}), quand un remplissage bouge d'au moins 1/64, qu'un reservoir se vide, se remplit ou change de contenu, et au plus
 * tard {@link #REFRESH} ticks apres la derniere variation.
 */
public final class TankSync {

    /** Rafraichissement lent sans spectateur (ticks). */
    public static final int REFRESH = 100;
    private static final double STEP = 1 / 64.0;

    /** Un reservoir a varie depuis le dernier renvoi demande. */
    private boolean dirty = true;
    private long sentAt = Long.MIN_VALUE / 2;
    private FluidStack[] sentFluid = new FluidStack[0];
    private Gas[] sentGas = new Gas[0];
    private double[] sentFill = new double[0];

    /** A appeler a chaque variation d'un reservoir. */
    public void changed() {
        dirty = true;
    }

    /** Serveur, a chaque tick : vrai s'il faut renvoyer les reservoirs au client ({@code markSync}). */
    public boolean due(BlockEntity tile, List<FluidTank> fluids, List<BasicGasTank> gases) {
        Level level = tile.getLevel();
        if (!dirty || level == null || level.isClientSide) {
            return false;
        }
        long now = level.getGameTime();
        int n = fluids.size() + gases.size();
        boolean send = now - sentAt >= REFRESH || sentFill.length != n || moved(fluids, gases) || watched(level, tile);
        if (send) {
            dirty = false;
            sentAt = now;
            snapshot(fluids, gases);
        }
        return send;
    }

    private boolean moved(List<FluidTank> fluids, List<BasicGasTank> gases) {
        for (int i = 0; i < fluids.size(); i++) {
            FluidTank t = fluids.get(i);
            FluidStack was = sentFluid[i];
            if (t.isEmpty() != was.isEmpty() || !t.isEmpty() && !t.getFluid().isFluidEqual(was)
                || differs(fill(t.getFluidAmount(), t.getCapacity()), sentFill[i])) {
                return true;
            }
        }
        for (int i = 0; i < gases.size(); i++) {
            BasicGasTank t = gases.get(i);
            int k = fluids.size() + i;
            Gas gas = t.isEmpty() ? null : t.getStack().getGas();
            if (!Objects.equals(gas, sentGas[i]) || differs(fill(t.getStack().getAmount(), t.getCapacity()), sentFill[k])) {
                return true;
            }
        }
        return false;
    }

    private static double fill(long amount, long capacity) {
        return capacity <= 0 ? 0 : amount / (double) capacity;
    }

    private static boolean differs(double fill, double sent) {
        return Math.abs(fill - sent) >= STEP || (fill <= 0) != (sent <= 0) || (fill >= 1) != (sent >= 1);
    }

    private void snapshot(List<FluidTank> fluids, List<BasicGasTank> gases) {
        int n = fluids.size() + gases.size();
        if (sentFill.length != n) {
            sentFill = new double[n];
            sentFluid = new FluidStack[fluids.size()];
            sentGas = new Gas[gases.size()];
        }
        for (int i = 0; i < fluids.size(); i++) {
            FluidTank t = fluids.get(i);
            sentFluid[i] = t.getFluid().copy();
            sentFill[i] = fill(t.getFluidAmount(), t.getCapacity());
        }
        for (int i = 0; i < gases.size(); i++) {
            BasicGasTank t = gases.get(i);
            sentGas[i] = t.isEmpty() ? null : t.getStack().getGas();
            sentFill[fluids.size() + i] = fill(t.getStack().getAmount(), t.getCapacity());
        }
    }

    /** Un joueur a-t-il l'interface de cette machine ouverte ? */
    private static boolean watched(Level level, BlockEntity tile) {
        for (Player player : level.players()) {
            if (player.containerMenu instanceof PiTileMenu<?> menu && menu.getTile() == tile) {
                return true;
            }
        }
        return false;
    }
}
