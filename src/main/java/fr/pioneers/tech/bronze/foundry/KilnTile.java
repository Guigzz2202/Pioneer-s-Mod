package fr.pioneers.tech.bronze.foundry;

import fr.pioneers.api.machine.StandardLayout.Part;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Four de potier : un foyer, quatre places a cuire. Monte vers 900 degres quand il brule (1 000 au charbon de terre) ; chaque piece cuit
 * sur place des que sa temperature est atteinte (moules, briques, creusets, briques refractaires, chaux vive, verre...). API Machine
 * (lot 8b) : la tile ne fait plus que PORTER son unite ({@link KilnUnit}) ; type {@code pottery_kiln}, cles et ordre des cases inchanges ;
 * interface v2 (foyer a la place de l'energie motrice, combustible et cendre, carte, 4 places en 2 x 2 avec leur barre de cuisson).
 * 1.0.4-beta (lot 24, Guillaume : « le four a poterie prend une nouvelle structure et le controleur devient un block full ») : MULTIBLOC
 * MAISON ({@link HouseNbt}, {@code pottery_kiln}) : la bouche du four (bloc plein) dans une maconnerie de briques (socle de 3 x 3, escaliers,
 * dalle, cheminee), la chambre vide derriere elle ; il ne cuit que forme (non forme : feu eteint, rien ne cuit, rien n'est perdu).
 */
public class KilnTile extends BronzeTile implements MultiblockCore.House.Holder, MultiblockPort {

    public static final int MAX_C = 900;
    /** Interface v2 : combustible et cendre a droite du foyer, carte, 4 places a cuire en 2 x 2 contre le bord droit. */
    public static final int FUEL_X = 30, FUEL_Y = StandardLayout.TOP + 40, ASH_X = 52, ASH_Y = StandardLayout.TOP + 40, IN_X = 140,
          IN_Y = StandardLayout.TOP + 6;
    public static final Rect CARD = StandardLayout.ageCard(30, 102);
    /** Hauteur de la carte (la flamme du foyer est dessous). */
    public static final int CARD_H = 22;
    private final KilnUnit unit;
    private final PowerSource.Fuel fire;
    /** 1.0.4-beta (lot 24) : structure de Guillaume ({@link HouseNbt}). */
    public static final String SHAPE = "pottery_kiln";
    private final MultiblockCore.House house = new MultiblockCore.House(HouseNbt.host(this, this::facing, SHAPE, "gui.pioneers.multiblock.house.kiln", this::markClient), 40);

    public KilnTile(BlockPos pos, BlockState state) {
        super(PiBronze.KILN_TILE.get(), pos, state);
        unit = carry(new KilnUnit(host()));
        fire = unit.power();
    }

    /** Place n (0..3) : grille 2 x 2 espacee, une barre de cuisson sous chaque place. */
    public static int[] warePos(int i) {
        return new int[]{IN_X + (i % 2) * 24, IN_Y + (i / 2) * 28};
    }

    @Override
    protected PowerSource power() {
        return fire;
    }

    public KilnUnit kiln() {
        return unit;
    }

    public double temperature() {
        return unit.temperature();
    }

    /** 1.0.5-beta (lot 23) : habillage peint (forme, palier) ; -1 : a refaire (chargement : idempotent). */
    private int dressedHeat = -1;
    private boolean dressed;

    /**
     * 1.0.5-beta (lot 23, Guillaume : « donner un modele fini au four de potier et une cheminee » ; « toute la structure doit prendre la meme
     * couleur en chauffant ») : formee, la maconnerie vanilla prend sa forme finie ({@link KilnMasonry#dress}) au palier de chauffe du four
     * ({@link KilnMasonry#stepOf}), le meme pour la bouche ({@link fr.pioneers.tech.bronze.HouseControllerBlock#HEAT}) ; defaite, elle redevient
     * vanilla ({@link KilnMasonry#undress}).
     */
    private void dress(ServerLevel level) {
        boolean formed = house.formed();
        int h = formed ? KilnMasonry.stepOf(unit.temperature()) : 0;
        if (formed == dressed && h == dressedHeat) {
            return;
        }
        if (formed) {
            KilnMasonry.dress(level, worldPosition, facing(), h);
        } else if (dressed || dressedHeat < 0) {
            KilnMasonry.undress(level, worldPosition, facing());
        }
        fr.pioneers.tech.bronze.HouseControllerBlock.sync(level, worldPosition, formed);
        fr.pioneers.tech.bronze.HeatPalette.paint(level, worldPosition, h);
        dressed = formed;
        dressedHeat = h;
    }

    @Override
    public void dropContents(java.util.function.Consumer<net.minecraft.world.item.ItemStack> drop) {
        super.dropContents(drop);
        //1.0.5-beta (lot 23) : bouche cassee : la maconnerie redevient vanilla
        if (level != null && !level.isClientSide) {
            KilnMasonry.undress(level, worldPosition, facing());
            dressed = false;
        }
    }

    @Override
    public void serverTick(ServerLevel level) {
        house.tick();
        dress(level);
        if (house.formed()) {
            unit.tick();
        } else {
            //1.0.4-beta (lot 24) : structure non formee : le feu ne prend pas (rien n'est perdu)
            fr.pioneers.tech.bronze.LitBronzeBlock.setLit(level, worldPosition, getBlockState(), false);
        }
        flushClient(level, 20);
    }

    /** 1.0.4-beta (lot 24) : facade de la bouche du four (la structure est derriere elle). */
    public Direction facing() {
        return HouseNbt.facing(getBlockState());
    }

    public boolean formed() {
        return house.formed();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    /** Lot 8c : contrat de port commun : un port commun pose dans la structure formee prete la capacite interne du four. */
    @org.jetbrains.annotations.Nullable
    @Override
    public Object portHandler(fr.pioneers.tech.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }

    /** 1.0.4-beta (lot 24) : premiere case fautive de la structure (null : formee) ; tile, banc et GameTests. */
    @Nullable
    public static BlockPos fault(Structure.View view, BlockPos controller, Direction front) {
        return HouseNbt.fault(view, controller, front, SHAPE);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        house.load();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        house.removed(true);
    }

    @Override
    protected void writeClient(net.minecraft.nbt.CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
    }

    @Override
    protected void readClient(net.minecraft.nbt.CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
    }

    @Override
    protected boolean exposesItems(@Nullable Direction side) {
        return true;
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
        parts.add(new Part("card", new Rect(CARD.x(), CARD.y(), CARD.w(), CARD_H)));
        for (int i = 0; i < 4; i++) {
            int[] p = warePos(i);
            parts.add(new Part("bar", new Rect(p[0], p[1] + 17, 16, 2)));
        }
        return StandardLayout.ageService(false, parts.toArray(new Part[0]));
    }

    /** 1.0.4-beta (lot 25) : particules de la structure en marche (T:industry/nbt_models.py HOUSE_FX). */
    @Override
    public void clientTick() {
        houseFx("pottery_kiln", lit());
    }
}
