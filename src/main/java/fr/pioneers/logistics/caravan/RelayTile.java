package fr.pioneers.logistics.caravan;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : tuile du RELAIS DE CARAVANE : porte son unite ({@link RelayUnit} : ordre, filtre, condition, attente,
 * proprietaire) et les 4 cases de la MANGEOIRE (rations des betes, entonnoirs acceptes) ; donne le CONTENEUR voisin (dessous, puis les
 * cotes ; caisse d'entrepot par ses ports, coffre, buffer... ; jamais un autre relais ; frontiere de claim : {@link ClanApi#canTransfer}) et
 * le POINT D'ATTACHE ou s'arrete la bete de tete (devant la facade). Interface standard v2 (vue {@code caravan_relay},
 * {@code client/gui/core/CaravanViews}) ; seuls le proprietaire et son clan la reglent ({@link Caravans#mayEdit}).
 */
public class RelayTile extends BronzeTile {

    /** Geometrie de l'interface (vue {@code caravan_relay}). */
    public static final int WIDTH = StandardLayout.WIDTH, LABEL_W = 30, CELL = 16, MANGER_X = 117, MANGER_Y = 72;
    public static final StandardLayout.Rect ORDER_ROW = new StandardLayout.Rect(8, 20, 180, 12),
          FILTER_ROW = new StandardLayout.Rect(8, 35, 180, 18),
          LEAVE_ROW = new StandardLayout.Rect(8, 56, 180, 12),
          WAIT_ROW = new StandardLayout.Rect(8, 73, 100, 14);
    /** Actions : attente - / + (pas de {@code WAIT_STEP}). */
    public static final int WAIT_DOWN = CUSTOM + 1, WAIT_UP = CUSTOM + 2;

    private final List<ItemSlot> manger = new ArrayList<>();
    private final RelayUnit unit;
    /** Unites de ration deja payees (une botte de foin = 9 rations : le reste attend ici). */
    private int credit;

    public RelayTile(BlockPos pos, BlockState state) {
        super(PiCaravan.RELAY_TILE.get(), pos, state);
        for (int i = 0; i < 4; i++) {
            //Case vide : botte de foin fantome (ce qui s'y met)
            manger.add(slot(ItemSlot.of(s -> food(s) != null, this::changed, MANGER_X + i * 18, MANGER_Y).ghost(net.minecraft.world.item.Items.HAY_BLOCK)));
        }
        unit = carry(new RelayUnit(host(), this));
    }

    public RelayUnit relay() {
        return unit;
    }

    public List<ItemSlot> manger() {
        return manger;
    }

    /** Unites de ration d'un objet (0 : pas une ration). */
    public static int units(String id) {
        for (GeneratedCaravan.Food f : GeneratedCaravan.FOOD) {
            if (f.id().equals(id)) {
                return f.units();
            }
        }
        return 0;
    }

    static GeneratedCaravan.Food food(ItemStack s) {
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        for (GeneratedCaravan.Food f : GeneratedCaravan.FOOD) {
            if (f.id().equals(id)) {
                return f;
            }
        }
        return null;
    }

    /** Rations disponibles (pour les chameaux : cactus compris). */
    public int rations(boolean camel) {
        int n = credit;
        for (ItemSlot s : manger) {
            GeneratedCaravan.Food f = s.isEmpty() ? null : food(s.getStack());
            if (f != null && (camel || !f.camelOnly())) {
                n += f.units() * s.getStack().getCount();
            }
        }
        return n;
    }

    /** Une ration pour une bete (chameau : cactus permis) ; faux s'il n'y en a plus. */
    public boolean takeRation(boolean camel) {
        if (credit > 0) {
            credit--;
            setChanged();
            return true;
        }
        for (ItemSlot s : manger) {
            GeneratedCaravan.Food f = s.isEmpty() ? null : food(s.getStack());
            if (f != null && (camel || !f.camelOnly())) {
                s.extract(1, Action.EXECUTE, Automation.INTERNAL);
                credit = f.units() - 1;
                setChanged();
                return true;
            }
        }
        return false;
    }

    public Direction facing() {
        return getBlockState().getValue(RelayBlock.FACING);
    }

    /** Point d'attache : la case devant la facade, au sol (centre). */
    public Vec3 standPoint() {
        BlockPos p = worldPosition.relative(facing(), 2);
        return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    /** Nom de l'etape (nom donne a l'enclume, sinon ses coordonnees). */
    public String stopName() {
        return displayName(this);
    }

    public static String displayName(@Nullable BlockEntity be) {
        if (be instanceof RelayTile t && t.customName != null) {
            return t.customName;
        }
        BlockPos p = be == null ? BlockPos.ZERO : be.getBlockPos();
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    @Nullable
    public String customName;

    /**
     * Conteneur voisin (capacite d'objets) : dessous, puis nord, sud, ouest, est, dessus ; jamais un relais ; refuse a travers une frontiere
     * de claim ({@link ClanApi#canTransfer}).
     */
    @Nullable
    public IItemHandler container() {
        if (level == null) {
            return null;
        }
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            BlockPos np = worldPosition.relative(d);
            if (!level.isLoaded(np) || !ClanApi.canTransfer(level, worldPosition, np)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(np);
            if (be == null || be instanceof RelayTile) {
                continue;
            }
            IItemHandler h = be.getCapability(ForgeCapabilities.ITEM_HANDLER, d.getOpposite()).resolve().orElse(null);
            if (h != null && h.getSlots() > 0) {
                return h;
            }
        }
        return null;
    }

    @Override
    public void serverTick(ServerLevel level) {
        unit.tick();
        flushClient(level, 10);
    }

    // ------------------------------------------------------------------ interface standard v2

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public String guiTier() {
        return "bench";
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return List.of(new StandardLayout.Part("setting", ORDER_ROW), new StandardLayout.Part("setting", FILTER_ROW),
              new StandardLayout.Part("setting", LEAVE_ROW), new StandardLayout.Part("setting", WAIT_ROW),
              new StandardLayout.Part("screen", StandardLayout.screen(WIDTH)));
    }

    /** Le joueur peut-il regler ce relais ? (proprietaire, son clan ; sinon un message) */
    private boolean allowed(@Nullable ServerPlayer player) {
        if (player == null || Caravans.mayEdit(player, unit.owner)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("gui.pioneers.relay.locked").withStyle(ChatFormatting.RED), true);
        return false;
    }

    @Override
    public void handleSetting(ServerPlayer player, int setting, int value) {
        if (allowed(player)) {
            unit.setting(setting, value);
        }
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (!allowed(player)) {
            return;
        }
        if (action == WAIT_DOWN || action == WAIT_UP) {
            unit.setting(RelayUnit.SETTING_WAIT, unit.wait + (action == WAIT_UP ? GeneratedCaravan.WAIT_STEP : -GeneratedCaravan.WAIT_STEP));
        }
    }

    /** Pose : le poseur devient proprietaire. */
    public void placedBy(@Nullable Player player) {
        if (player != null) {
            unit.owner = player.getUUID();
            unit.ownerName = player.getGameProfile().getName();
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("ration_credit", credit);
        if (customName != null) {
            tag.putString("relay_name", customName);
        }
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        credit = tag.getInt("ration_credit");
        customName = tag.contains("relay_name") ? tag.getString("relay_name") : null;
    }
}
