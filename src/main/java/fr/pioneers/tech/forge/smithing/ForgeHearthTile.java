package fr.pioneers.tech.forge.smithing;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.StandardLayout.Part;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.tech.bronze.AirBlastReceiver;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.tech.forge.ForgeHeat;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.ForgeTile;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.casting.Ignitable;
import fr.pioneers.core.registries.PiForge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Foyer de forge (spec forge primaire par. 1.9 et par. 3) : brule du charbon (de bois ou de terre, tag {@code minecraft:coals}), monte a
 * {@link #BASE_C} (feu de camp + charbon) plus l'air des soufflets (jusqu'a {@link ForgeHeat#BELLOWS_MAX_C}), et chauffe UNE piece a la fois
 * dans la braise jusqu'a la temperature du foyer, sans jamais atteindre la fusion du metal ({@link #MELT_MARGIN_C} en dessous).
 * <ul>
 * <li>cases : {@code input} (lingots en attente, entonnoir par le HAUT), {@code fuel} (charbon, par les COTES), {@code hot} (la piece dans la
 * braise : sort par le DESSOUS, seulement quand elle se forge) ;</li>
 * <li>allumage : briquet, boule de feu ou {@link #ignite()} (allume-feu de l'equipe "casting"), seulement s'il y a du charbon ;</li>
 * <li>a la main (1.0.5-beta lot 23) : clic droit = l'interface, quel que soit l'objet en main ; Maj + clic droit avec de quoi allumer =
 * allumage ({@link #isIgniter}) ; plus rien ne se pose ni ne se prend au clic droit.</li>
 * </ul>
 * Source de chaleur pour ce qui est pose dessus ({@link ForgeHeat.HeatSource} : la vraie temperature) et recepteur d'air des soufflets.
 * <p>1.0.2-beta (lot 2, notes de Guillaume : « ajouter une interface au bloc Foyer de forge afin d'ajuster le nombre de lingots et de charbon mis
 * dedans ») : API Machine : la tile ne fait plus que PORTER son unite ({@link HearthUnit} : braise, chauffe, curseurs, lopin d'acier) ; type
 * {@code forge_hearth}, cles NBT et ordre des cases inchanges ; interface v2 (feu a la place de l'energie motrice, lingots et charbon, carte de
 * la braise, piece, ecran d'etat, deux curseurs de chargement).
 */
public class ForgeHearthTile extends ForgeTile implements ForgeHeat.HeatSource, AirBlastReceiver, Ignitable {

    /** Braise de charbon sans air (degres C). */
    public static final int BASE_C = ForgeHeat.CAMPFIRE_C + ForgeHeat.CHARCOAL_BONUS_C;
    /** Marge sous la fusion du metal chauffe. */
    public static final int MELT_MARGIN_C = 50;
    /** Interface v2 : lingots et charbon a droite du feu, carte de la braise, piece a droite. */
    public static final int INPUT_X = 30, INPUT_Y = StandardLayout.TOP + 4, FUEL_X = 30, FUEL_Y = StandardLayout.TOP + 40, HOT_X = 161,
          HOT_Y = StandardLayout.TOP + 22;
    public static final Rect CARD = StandardLayout.ageCard(52, 94);

    private final HearthUnit unit;
    private final LazyOptional<ItemSlots> top, sides, bottom;

    public ForgeHearthTile(BlockPos pos, BlockState state) {
        super(PiForge.FORGE_HEARTH_TILE.get(), pos, state);
        unit = carry(new HearthUnit(host(), this));
        top = LazyOptional.of(() -> new ItemSlots(List.of(unit.input), Automation.EXTERNAL));
        sides = LazyOptional.of(() -> new ItemSlots(List.of(unit.fuel), Automation.EXTERNAL));
        bottom = LazyOptional.of(() -> new ItemSlots(List.of(unit.hot), Automation.EXTERNAL));
    }

    public HearthUnit hearth() {
        return unit;
    }

    /** Lingot (ou metal chauffe brut) a poser dans le foyer. */
    public static boolean isRawMetal(ItemStack stack) {
        //Toute piece de metal chauffe se rechauffe, piece finie comprise (refroidie sans trempe, sinon elle resterait bloquee)
        return ForgeMetals.isIngot(stack) || HeatedMetal.is(stack) && !HeatedMetal.metal(stack).isEmpty();
    }

    public static boolean isFuel(ItemStack stack) {
        return HearthUnit.coal(stack);
    }

    // ------------------------------------------------------------------
    // Etat (rendu, fiche au regard, GameTests)
    // ------------------------------------------------------------------

    @Override
    public boolean isLit() {
        return getBlockState().hasProperty(ForgeHearthBlock.LIT) && getBlockState().getValue(ForgeHearthBlock.LIT);
    }

    @Override
    public int heatC() {
        return isLit() ? Math.round(unit.temperature()) : 0;
    }

    /** Temperature de la braise (degres C, client compris). */
    public float temperature() {
        return unit.temperature();
    }

    public float airC() {
        return unit.air();
    }

    public ItemStack hotPiece() {
        return unit.hot.getStack();
    }

    public ItemStack waiting() {
        return unit.input.getStack();
    }

    public ItemStack fuelStack() {
        return unit.fuel.getStack();
    }

    /** Fraction de combustible restant dans la braise en cours (0..1). */
    public float burnRatio() {
        return unit.burnRatio();
    }

    /** Temperature visee par une piece dans la braise. */
    public static float pieceTarget(float hearthC, String metal) {
        return Math.min(hearthC, ForgeMetals.meltingC(metal) - MELT_MARGIN_C);
    }

    @Override
    public void blast(double strength) {
        unit.blast(strength);
    }

    /** Allume le foyer s'il y a du charbon (appele par l'allume-feu, le briquet, la boule de feu). Vrai si le foyer brule. */
    public boolean ignite() {
        if (level == null || level.isClientSide) {
            return false;
        }
        if (isLit()) {
            return true;
        }
        if (!unit.burning() && unit.fuel.isEmpty()) {
            return false;
        }
        setLit(true);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        BronzeEvents.award((ServerLevel) level, worldPosition, "forge_hearth_lit");
        return true;
    }

    /** Allume-feu ({@link Ignitable}) : vrai seulement si le foyer vient de s'allumer ; sans charbon, le joueur est prevenu. */
    @Override
    public boolean ignite(@Nullable Player player) {
        if (level == null || level.isClientSide || isLit()) {
            return false;
        }
        boolean lit = ignite();
        if (!lit && player != null) {
            player.displayClientMessage(Component.translatable("message.pioneers.forge_hearth.no_fuel"), true);
        }
        return lit;
    }

    void setLit(boolean lit) {
        if (level != null && isLit() != lit && getBlockState().hasProperty(ForgeHearthBlock.LIT)) {
            level.setBlock(worldPosition, getBlockState().setValue(ForgeHearthBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void serverTick(ServerLevel level) {
        unit.tick();
        flushClient(level, 10);
    }

    // ------------------------------------------------------------------
    // Interaction a la main
    // ------------------------------------------------------------------

    /**
     * 1.0.5-beta (lot 23, Guillaume : « le foyer de forge ne doit plus jamais accepter d'item par le clic droit, seule l'interface est utilisee.
     * Seul l'allumage accepte le shift clic droit avec de quoi l'allumer ») : le clic droit ne pose ni ne prend plus RIEN, quel que soit l'objet
     * en main : il ouvre l'interface ({@link ForgeTile#hasMenu}). L'allumage (Maj + clic droit avec un {@link #isIgniter allume-feu}) passe par
     * {@link SmithingEvents#hearthIgnite} (le jeu n'appelle pas le bloc quand le joueur accroupi tient un objet) ; l'allume-feu a archet garde
     * son geste ({@link Ignitable}). Les entonnoirs (lingots par le haut, charbon par les cotes, piece par le dessous) restent.
     */
    @Override
    public InteractionResult use(Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /**
     * De quoi allumer le foyer a Maj + clic droit (liste d'avant : briquet, boule de feu ; plus la boite d'allumettes des fumeurs et la torche).
     * L'allume-feu a archet n'y est pas : il allume par son geste ({@link fr.pioneers.tech.forge.casting.FireStarterItem}).
     */
    public static boolean isIgniter(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH)
               || stack.is(fr.pioneers.farm.leisure.PiLeisure.MATCHBOX.get());
    }

    /**
     * Allumage a la main (serveur) : allume s'il y a du charbon et use l'objet (briquet et allumettes : 1 usage ; boule de feu : consommee ;
     * torche : rien), sinon previent le joueur. Vrai si le foyer brule.
     */
    public boolean igniteBy(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level == null || level.isClientSide || !isIgniter(held)) {
            return false;
        }
        if (isLit()) {
            return true;
        }
        if (!ignite()) {
            player.displayClientMessage(Component.translatable("message.pioneers.forge_hearth.no_fuel"), true);
            return false;
        }
        if (held.is(Items.FIRE_CHARGE)) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        } else if (held.isDamageableItem()) {
            held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
        return true;
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    // ------------------------------------------------------------------
    // Entonnoirs : lingots par le haut, charbon par les cotes, piece chaude par le dessous
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side != null) {
            return (side == Direction.UP ? top : side == Direction.DOWN ? bottom : sides).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        top.invalidate();
        sides.invalidate();
        bottom.invalidate();
    }

    /** Metal des lingots en attente (client, relu a chaque mise a jour). */
    public String waitingMetal() {
        return unit.waitingMetal();
    }

    /** Charbon en reserve (client : valeur synchronisee). */
    public int fuelCount() {
        return unit.fuelCount();
    }

    // ------------------------------------------------------------------
    // Interface standard v2
    // ------------------------------------------------------------------

    @Override
    public void handleSetting(ServerPlayer player, int setting, int value) {
        unit.setting(setting, value);
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    @Override
    public List<Part> guiParts() {
        List<Part> parts = new ArrayList<>();
        parts.add(StandardLayout.framed("power", StandardLayout.POWER));
        parts.add(new Part("card", CARD));
        parts.add(new Part("screen", StandardLayout.sliderScreen(StandardLayout.WIDTH)));
        Rect row = StandardLayout.sliderRow(StandardLayout.WIDTH);
        int half = (row.w() - 4) / 2;
        parts.add(new Part("slider", new Rect(row.x(), row.y(), half, row.h())));
        parts.add(new Part("slider", new Rect(row.x() + half + 4, row.y(), row.w() - half - 4, row.h())));
        return parts;
    }
}
