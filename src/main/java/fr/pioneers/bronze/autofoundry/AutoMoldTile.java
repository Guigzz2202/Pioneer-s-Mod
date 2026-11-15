package fr.pioneers.bronze.autofoundry;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.bronze.BronzeTile;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.registries.PiBronze;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4) : tile du moule automatique : elle PORTE son unite ({@link AutoMoldUnit} : empreinte, metal recu, figeage, sortie) et
 * garde le bloc, le rendu et les GameTests. Interface v2 (figeage a la place de l'energie motrice, 6 boutons d'empreinte, fleche, piece,
 * ecran). La sortie se vide par le dessous ou les cotes (le dessus est au bec du conduit) ; rien n'entre par l'exterieur.
 */
public class AutoMoldTile extends BronzeTile {

    /** Actions de l'interface : choisir l'empreinte n ({@code FORM_ACTION + n}). */
    public static final int FORM_ACTION = CUSTOM;
    private final AutoMoldUnit unit;

    public AutoMoldTile(BlockPos pos, BlockState state) {
        super(PiBronze.AUTO_MOLD_TILE.get(), pos, state);
        unit = carry(new AutoMoldUnit(host()));
    }

    public AutoMoldUnit mold() {
        return unit;
    }

    public ItemSlot outputSlot() {
        return unit.output;
    }

    @Override
    public void serverTick(ServerLevel level) {
        unit.tick();
        flushClient(level, 5);
    }

    @Override
    public void clientTick() {
        unit.clientTick();
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        int n = action - FORM_ACTION;
        if (n >= 0 && n < AutoMoldBlock.Form.values().length) {
            unit.setForm(AutoMoldBlock.Form.of(n));
        }
    }

    @Override
    protected boolean exposesItems(@Nullable Direction side) {
        return side != Direction.UP;
    }

    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        super.dropContents(drop);
        unit.spill().forEach(drop);
    }

    // ------------------------------------------------------------------
    // Interface standard v2
    // ------------------------------------------------------------------

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    /** Bouton de l'empreinte n (cadre). */
    public static StandardLayout.Rect formButton(int n) {
        return new StandardLayout.Rect(AutoMoldUnit.FORMS_X + (n % 3) * AutoMoldUnit.STEP, AutoMoldUnit.FORMS_Y + (n / 3) * AutoMoldUnit.STEP,
              AutoMoldUnit.BUTTON, AutoMoldUnit.BUTTON);
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        List<StandardLayout.Part> parts = new ArrayList<>();
        parts.add(new StandardLayout.Part("card", AutoMoldUnit.FORMS));
        parts.add(new StandardLayout.Part("arrow", AutoMoldUnit.ARROW));
        parts.add(new StandardLayout.Part("slot_out", StandardLayout.slotFrame(AutoMoldUnit.OUT_X, AutoMoldUnit.OUT_Y)));
        return StandardLayout.ageService(false, parts.toArray(new StandardLayout.Part[0]));
    }
}
