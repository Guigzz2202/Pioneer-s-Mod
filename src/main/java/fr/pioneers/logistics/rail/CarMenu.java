package fr.pioneers.logistics.rail;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.logistics.rail.train.Car;
import fr.pioneers.logistics.rail.train.CarType;
import fr.pioneers.logistics.rail.train.RailCarEntity;
import fr.pioneers.logistics.rail.train.RailItems;
import fr.pioneers.logistics.rail.train.Train;
import fr.pioneers.core.registries.PiRail;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : inventaire d'un vehicule (wagon couvert, tremie, wagonnet, tender et locomotive : combustible). Neuf colonnes ; l'inventaire du
 * joueur dessous ; Maj + clic transfere. 0.20.1 : l'eau (tender, locomotive) et le foyer sont suivis ({@link #water}, {@link #burning}) ;
 * le menu connait son train pour ouvrir sa fiche.
 */
public class CarMenu extends AbstractContainerMenu {

    /** Haut des cases du vehicule ; hauteur de la ligne d'eau sous les cases (vehicules a vapeur). */
    public static final int SLOT_Y = 20, WATER_LINE = 16;

    public final int carSlots, rows;
    public final CarType type;
    public final int tier;
    private final int entityId;
    public final UUID train;
    public final int index;
    /** 0.20.11 : vue du vehicule, ou du navire dont c'est une machine ou une cale. */
    @Nullable
    private final Entity view;
    /**
     * Eau (2 x 16 bits), contenance (2 x 16 bits), foyer allume ; 0.20.2 : reserve principale (2 x 16), sa contenance (2 x 16), id brut du
     * contenu + 1 (2 x 16 ; fluide, gaz ou type de donnees selon la sorte).
     */
    private final ContainerData data;
    public static final int DATA_COUNT = 11;

    public CarMenu(int id, Inventory inventory, IItemHandler items, @Nullable Entity view, int entityId, CarType type, int tier, UUID train,
          int index, ContainerData data) {
        super(PiRail.CAR_MENU.get(), id);
        this.carSlots = items.getSlots();
        this.rows = (carSlots + 8) / 9;
        this.type = type;
        this.tier = tier;
        this.view = view;
        this.entityId = entityId;
        this.train = train;
        this.index = index;
        this.data = data;
        for (int i = 0; i < carSlots; i++) {
            addSlot(new SlotItemHandler(items, i, 8 + (i % 9) * 18, SLOT_Y + (i / 9) * 18));
        }
        addDataSlots(data);
        int top = inventoryTop(type, tier, rows);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, top + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inventory, c, 8 + c * 18, top + 58));
        }
    }

    /** Vehicule a vapeur : combustible et eau. */
    public static boolean steam(CarType type) {
        return type == CarType.STEAM || type == CarType.TENDER;
    }

    /** Ligne de jauge sous les cases : eau de la vapeur, ou reserve (citerne, batterie, carburant, donnees, air). */
    public static boolean hasLine(CarType type, int tier) {
        return steam(type) || type.fluidCapacity(tier) > 0 || type.gasCapacity(tier) > 0 || type.energyCapacity(tier) > 0
              || type.dataCapacity(tier) > 0 || type.airCapacity(tier) > 0;
    }

    /** Haut de l'inventaire du joueur (le libelle est 10 px au-dessus). */
    public static int inventoryTop(CarType type, int tier, int rows) {
        return SLOT_Y + rows * 18 + (hasLine(type, tier) ? WATER_LINE : 0) + 14;
    }

    /** Cote client : inventaire fictif de la bonne taille. */
    public static CarMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        int entity = buf.readVarInt();
        int size = buf.readVarInt();
        CarType type = CarType.byId(buf.readByte());
        int tier = buf.readByte();
        UUID train = buf.readUUID();
        int index = buf.readVarInt();
        //Memes regles que le vehicule (combustible seul, une seule sorte) : pas de faux deplacements prevus
        IItemHandler items = new Car(type, tier).items;
        return new CarMenu(id, inventory, items.getSlots() == size ? items : new ItemStackHandler(size), null, entity, type, tier, train, index,
              new SimpleContainerData(DATA_COUNT));
    }

    public int water() {
        return data.get(0) & 0xFFFF | (data.get(1) & 0xFFFF) << 16;
    }

    public int waterCapacity() {
        return data.get(2) & 0xFFFF | (data.get(3) & 0xFFFF) << 16;
    }

    public boolean burning() {
        return data.get(4) != 0;
    }

    public long storeAmount() {
        return (data.get(5) & 0xFFFFL) | (data.get(6) & 0xFFFFL) << 16;
    }

    public long storeCapacity() {
        return (data.get(7) & 0xFFFFL) | (data.get(8) & 0xFFFFL) << 16;
    }

    /** Id brut (registre) du contenu de la reserve, -1 si vide. */
    public int storeContent() {
        return ((data.get(9) & 0xFFFF) | (data.get(10) & 0xFFFF) << 16) - 1;
    }

    /** Suivi des valeurs du vehicule cote serveur. */
    static ContainerData track(Car car) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> car.water & 0xFFFF;
                    case 1 -> car.water >>> 16;
                    case 2 -> car.waterCapacity() & 0xFFFF;
                    case 3 -> car.waterCapacity() >>> 16;
                    case 4 -> car.heat > 0 ? 1 : 0;
                    case 5 -> (int) (car.storeAmount() & 0xFFFF);
                    case 6 -> (int) (car.storeAmount() >>> 16 & 0xFFFF);
                    case 7 -> (int) (car.storeCapacity() & 0xFFFF);
                    case 8 -> (int) (car.storeCapacity() >>> 16 & 0xFFFF);
                    case 9 -> (contentId(car) + 1) & 0xFFFF;
                    case 10 -> (contentId(car) + 1) >>> 16 & 0xFFFF;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    /** Id brut du contenu de la reserve principale dans son registre (-1 : vide ou inconnu). */
    static int contentId(Car car) {
        String id = car.storeContent();
        if (id.isEmpty()) {
            return -1;
        }
        ResourceLocation rl = ResourceLocation.parse(id);
        return switch (car.type.store) {
            case FLUID -> ((ForgeRegistry<Fluid>) ForgeRegistries.FLUIDS).getID(rl);
            case GAS -> ((ForgeRegistry<Gas>) PioneersAPI.gasRegistry()).getID(rl);
            case DATA -> ((ForgeRegistry<DataType>) PioneersAPI.dataTypeRegistry()).getID(rl);
            default -> -1;
        };
    }

    public int entityId() {
        return entityId;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return view == null || !view.isRemoved() && view.distanceToSqr(player) < (view instanceof RailCarEntity ? 64 : 576);
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slotAt(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < carSlots) {
            if (!moveItemStackTo(stack, carSlots, carSlots + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, carSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Nullable
    private Slot slotAt(int index) {
        return index >= 0 && index < slots.size() ? slots.get(index) : null;
    }

    /** Ouverture cote serveur. */
    /** 0.20.11 : {@code owner} = train ou navire, {@code index} = place du vehicule (train) ou de la machine / cale (navire). */
    public record Provider(UUID owner, Car car, Entity view, int index) implements MenuProvider {

        public Provider(Train train, Car car, RailCarEntity view) {
            this(train.id, car, view, Math.max(0, train.cars.indexOf(car)));
        }

        @NotNull
        @Override
        public Component getDisplayName() {
            return RailItemsName.of(car);
        }

        @Override
        public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
            return new CarMenu(id, inventory, car.items, view, view.getId(), car.type, car.tier, owner, index, track(car));
        }
    }

    /** Nom d'un vehicule (celui de son objet). */
    public static final class RailItemsName {

        private RailItemsName() {
        }

        public static Component of(Car car) {
            return RailItems.stack(car.type, car.tier).getHoverName();
        }
    }

    @Nullable
    public Entity entity(Level level) {
        return level.getEntity(entityId);
    }
}
