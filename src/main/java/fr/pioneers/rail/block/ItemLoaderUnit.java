package fr.pioneers.rail.block;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.rail.RailWorld;
import fr.pioneers.rail.logistics.Cargo;
import fr.pioneers.rail.train.Car;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * API Machine (lot 8c) : LOGIQUE DES POSTES A OBJETS (quai a objets, silo, fosse, charbonnier, grue de quai, poste blinde : code d'avant de
 * {@link ItemLoaderTile}, deplace) : tampon de 9 cases (declarees par l'unite, meme ordre et memes places), credit de debit du tier, vehicule
 * servi (pas a travers une frontiere de claim refusee), plan de la gare logistique ({@code RailWorld.postPlan}), chargement / dechargement
 * case par case ou en vrac. Cles sauvees : {@code unload}, {@code moved} ; client : {@code unload}.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : sens, vehicule devant, objets passes, sorte du poste.
 */
public final class ItemLoaderUnit extends MachineUnit {

    private final ItemLoaderTile tile;
    final List<ItemSlot> buffer = new ArrayList<>();
    boolean unload;
    private boolean serving;
    private long moved;
    private float credit;
    private int ticks;

    public ItemLoaderUnit(ItemLoaderTile tile, MachineTile.Access machine) {
        super(machine);
        this.tile = tile;
        for (int i = 0; i < 9; i++) {
            buffer.add(machine.slot(ItemSlot.of(machine::changed, StandardLayout.POST_SLOTS_X + (i % 3) * 18, StandardLayout.POST_SLOTS_Y + (i / 3) * 18)));
        }
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel sl) || !machine.canWork()) {
            serving = false;
            machine.markWorking(false);
            return;
        }
        PostKind kind = tile.kind();
        int tier = TieredMachineBlock.tierOf(machine.state());
        int rate = kind.rate(tier);
        credit = Math.min(rate, credit + rate / 20F);
        RailWorld w = RailWorld.get(sl);
        if (++ticks % 20 == 0) {
            tile.point.update(sl, tile);
        }
        Car car = RailPostTile.carFor(w, pos(), kind);
        //1.9 (MAJ RP, bloc 4) : le poste ne charge ni ne decharge un vehicule a travers une frontiere de claim refusee
        if (car != null && !ClanApi.canTransfer(sl, pos(), RailPostTile.carPos(w, pos(), car))) {
            car = null;
        }
        serving = car != null;
        boolean worked = false;
        RailWorld.PostPlan plan = car == null || tile.holds().isEmpty() ? null : w.postPlan(pos(), car);
        if (car != null && credit >= 1 && plan != null) {
            //0.20.5 : gare logistique
            boolean out = plan.unload();
            int budget = (int) Math.min((long) credit, plan.limit());
            int done = 0;
            if (!plan.idle() && (out ? kind != PostKind.SILO : kind != PostKind.PIT)) {
                Predicate<ItemStack> filter = st -> plan.allows(Cargo.item(Cargo.itemId(st)));
                done = kind == PostKind.CRANE ? (out ? unloadSlot(car, 0, budget) : loadSlot(car, 0, budget))
                      : out ? unload(car, budget, filter) : load(car, budget, filter);
            }
            credit -= done;
            moved += done;
            worked = done > 0;
        } else if (car != null && credit >= 1) {
            int budget = (int) credit;
            int done;
            if (kind == PostKind.NUCLEAR) {
                //Reprise des assemblages uses, puis combustible neuf
                done = unloadSlot(car, 1, budget);
                done += loadSlot(car, 0, budget - done);
            } else if (kind == PostKind.CRANE) {
                done = unload ? unloadSlot(car, 0, budget) : loadSlot(car, 0, budget);
            } else {
                boolean out = kind.unloadOnly() || kind.reversible() && unload;
                done = out ? unload(car, budget, st -> true) : load(car, budget, st -> true);
            }
            credit -= done;
            moved += done;
            worked = done > 0;
        }
        machine.markWorking(worked);
    }

    /** Charge une seule case du vehicule. */
    private int loadSlot(Car car, int slot, int budget) {
        int done = 0;
        for (ItemSlot s : buffer) {
            if (s.isEmpty() || done >= budget) {
                continue;
            }
            ItemStack offer = s.getStack().copy();
            offer.setCount(Math.min(offer.getCount(), budget - done));
            ItemStack rest = car.items.insertItem(slot, offer, false);
            int n = offer.getCount() - rest.getCount();
            if (n > 0) {
                s.extract(n, Action.EXECUTE, Automation.INTERNAL);
                done += n;
            }
        }
        return done;
    }
    /** Vide une seule case du vehicule dans le tampon. */
    private int unloadSlot(Car car, int slot, int budget) {
        ItemStack in = car.items.getStackInSlot(slot);
        if (in.isEmpty() || budget <= 0) {
            return 0;
        }
        ItemStack rest = in.copy();
        rest.setCount(Math.min(in.getCount(), budget));
        int want = rest.getCount();
        for (ItemSlot s : buffer) {
            rest = s.insert(rest, Action.EXECUTE, Automation.INTERNAL);
            if (rest.isEmpty()) {
                break;
            }
        }
        int n = want - rest.getCount();
        if (n > 0) {
            ItemStack left = in.copy();
            left.shrink(n);
            car.items.setStackInSlot(slot, left.isEmpty() ? ItemStack.EMPTY : left);
        }
        return n;
    }
    private int load(Car car, int budget, Predicate<ItemStack> filter) {
        int done = 0;
        for (ItemSlot s : buffer) {
            if (s.isEmpty() || done >= budget || !filter.test(s.getStack())) {
                continue;
            }
            ItemStack offer = s.getStack().copy();
            offer.setCount(Math.min(offer.getCount(), budget - done));
            ItemStack rest = ItemHandlerHelper.insertItemStacked(car.items, offer, false);
            int n = offer.getCount() - rest.getCount();
            if (n > 0) {
                s.extract(n, Action.EXECUTE, Automation.INTERNAL);
                done += n;
            }
        }
        return done;
    }
    private int unload(Car car, int budget, Predicate<ItemStack> filter) {
        int done = 0;
        for (int i = 0; i < car.items.getSlots() && done < budget; i++) {
            ItemStack in = car.items.getStackInSlot(i);
            if (in.isEmpty() || !filter.test(in)) {
                continue;
            }
            ItemStack take = car.items.extractItem(i, Math.min(in.getCount(), budget - done), true);
            ItemStack rest = take.copy();
            for (ItemSlot s : buffer) {
                rest = s.insert(rest, Action.EXECUTE, Automation.INTERNAL);
                if (rest.isEmpty()) {
                    break;
                }
            }
            int n = take.getCount() - rest.getCount();
            if (n > 0) {
                car.items.extractItem(i, n, false);
                done += n;
            }
        }
        return done;
    }

    /** Sens du poste : vider le vehicule (action du joueur, gare logistique). */
    void setUnload(boolean value) {
        if (unload != value) {
            unload = value;
            machine.changed();
            machine.markSync();
        }
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(RailPostTile.UNLOAD, () -> unload ? 1 : 0).add(RailPostTile.SERVING, () -> serving ? 1 : 0)
              .add(RailPostTile.MOVED, () -> moved).add(RailPostTile.KIND, () -> tile.kind().ordinal());
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putBoolean("unload", unload);
        tag.putLong("moved", moved);
    }

    @Override
    public void load(CompoundTag tag) {
        unload = tag.getBoolean("unload");
        moved = tag.getLong("moved");
    }

    /** 0.20.7 : le sens du poste part aussi au client (copie des plans des robots). */
    @Override
    public void writeClient(CompoundTag tag) {
        tag.putBoolean("unload", unload);
    }

    @Override
    public void readClient(CompoundTag tag) {
        unload = tag.getBoolean("unload");
    }
}
