package fr.pioneers.integration.jei;

import fr.pioneers.api.multiblock.HouseShapes;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.common.projector.Blueprint;
import fr.pioneers.core.common.projector.BlueprintBill;
import fr.pioneers.core.common.projector.Projections;
import fr.pioneers.core.Texts;
import fr.pioneers.tech.industry.multi.Cleanroom;
import fr.pioneers.tech.industry.multi.IndustrialBlueprint;
import fr.pioneers.logistics.naval.HarborBlueprint;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.space.assembly.AssemblyHallBlueprint;
import fr.pioneers.space.base.BaseBlueprint;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11.3 (lot E3, demande de Guillaume : « une page de multibloc dans JEI : prevoir le nombre de blocs necessaires et une preview du
 * multibloc ») : categorie « Multiblocs » ({@code pioneers:multiblock}), une recette par plan projetable (Maj + clic droit sur le controleur :
 * industriels a leur taille unique, reacteurs, calcul, chantier naval, ateliers ferroviaires, haut fourneau, chaudiere, foreuse a vapeur,
 * stabilisateur, simulateur, absorbeur, port, bases, halls d'assemblage, complexe de lancement...).
 * <ul>
 *   <li>a gauche : apercu 3D du plan (vrais modeles, maillage en cache {@link MultiblockMesh}), rotation lente automatique, glisser pour tourner,
 *   molette ou boutons pour la vue par couche (« couche n / N ») ;</li>
 *   <li>a droite : les blocs necessaires et leur quantite (entrees : R et U sur chacun), variantes de tier qui defilent, autres blocs acceptes en
 *   infobulle ; le controleur en sortie (et catalyseur) ;</li>
 *   <li>pied de page : dimensions longueur x largeur x hauteur et nombre total de blocs.</li>
 * </ul>
 * Quantites : {@link BlueprintBill} (meme source que les plans, rien n'est recopie). Banc hors jeu : {@code tools/multiblock/run.sh}.
 */
@OnlyIn(Dist.CLIENT)
public class MultiblockCategory extends PiCategory<MultiblockCategory.Entry> {

    static final RecipeType<Entry> TYPE = JeiOrder.create("multiblock", Entry.class);
    static final int W = 176, H = 104, PV_W = 100, PV_H = 90, BAR_Y = 92, BAR_H = 12, GRID_X = 104, COLS = 4, ROWS = 4, SLOT = 18;
    static final int CTRL_X = 158, CTRL_Y = 78;

    /** Une page : plan, nomenclature, cle du maillage, icone du controleur (creee a la demande). */
    static final class Entry {

        final Projections.Plan plan;
        final BlueprintBill bill;
        final String key;
        @Nullable
        IDrawable icon;

        Entry(Projections.Plan plan, BlueprintBill bill) {
            this.plan = plan;
            this.bill = bill;
            this.key = plan.structure().getTranslationKey() + "#" + plan.variant() + "#" + id(bill.controller());
        }
    }

    MultiblockCategory(IGuiHelper helper) {
        super(helper, TYPE, "jei.pioneers.multiblock", PiIndustry.CASINGS.get(CableTier.BASIC).getBlock(), W, H);
        family(JeiDraw.Family.BLUEPRINT);
    }

    // ------------------------------------------------------------------ donnees

    static String id(@Nullable Block block) {
        ResourceLocation key = block == null ? null : ForgeRegistries.BLOCKS.getKey(block);
        return key == null ? "?" : key.getPath();
    }

    /** Rang d'un plan dans la page : bronze et vapeur, industriels (ordre du catalogue), port, bases, halls, fin de jeu. */
    private static int rank(Blueprint s) {
        if (s instanceof HouseShapes.HousePlan p) {
            //Lot 6 : plans maison tires de leur forme (memes rangs qu'avant ; le hub robotique avec les halls)
            return p.rank();
        }
        if (s instanceof IndustrialBlueprint i) {
            return 1_000 + i.machine().ordinal();
        }
        if (s instanceof HarborBlueprint) {
            return 20_000;
        }
        if (s instanceof BaseBlueprint) {
            return 30_000;
        }
        if (s instanceof AssemblyHallBlueprint) {
            return 40_000;
        }
        return 60_000;
    }

    /** Toutes les pages : les plans que savent projeter les blocs du mod (meme source que Maj + clic droit sur un controleur). */
    static List<Entry> entries() {
        //Rechargement (monde, ressources) : les maillages seront refaits avec les modeles du moment
        MultiblockMesh.clear();
        List<Block> blocks = new ArrayList<>();
        for (var e : ForgeRegistries.BLOCKS.getEntries()) {
            if (Pioneers.MODID.equals(e.getKey().location().getNamespace())) {
                blocks.add(e.getValue());
            }
        }
        List<Entry> out = new ArrayList<>();
        for (Projections.Plan plan : Projections.catalog(blocks)) {
            BlueprintBill bill;
            try {
                bill = BlueprintBill.of(plan.structure(), plan.variant(), MultiblockCategory::id);
            } catch (RuntimeException ex) {
                Pioneers.LOGGER.warn("JEI multiblocs : plan illisible {} ({})", plan.structure().getTranslationKey(), ex.toString());
                continue;
            }
            if (bill.total() > 0) {
                out.add(new Entry(plan, bill));
            }
        }
        out.sort(Comparator.comparingInt((Entry e) -> rank(e.plan.structure())).thenComparing(e -> e.key));
        return out;
    }

    // ------------------------------------------------------------------ charte

    @Override
    protected Component headerTitle(Entry entry) {
        Blueprint s = entry.plan.structure();
        MutableComponent text = Component.translatable(s.getTranslationKey());
        String variant = s.getVariantKey(entry.plan.variant());
        if (variant != null) {
            text.append(Component.literal(" · ")).append(Component.translatable(variant));
        }
        return text;
    }

    @Override
    protected IDrawable headerIcon(Entry entry) {
        if (entry.icon == null && entry.bill.controller() != null && entry.bill.controller().asItem() != Items.AIR) {
            entry.icon = helper.createDrawableItemStack(new ItemStack(entry.bill.controller()));
        }
        return entry.icon != null ? entry.icon : super.headerIcon(entry);
    }

    @Override
    protected List<JeiDraw.Chip> chips(Entry entry) {
        BlueprintBill b = entry.bill;
        int length = Math.max(b.sizeX(), b.sizeZ()), width = Math.min(b.sizeX(), b.sizeZ());
        //API Multibloc (lot 5) : fiche du multibloc : parallele par tier, version compacte (infobulle de la taille)
        List<Component> sizeTip = new ArrayList<>();
        sizeTip.add(Component.translatable("jei.pioneers.multiblock.size_tip").withStyle(ChatFormatting.GRAY));
        fr.pioneers.api.multiblock.MultiblockSpec spec = b.controller() == null ? null : fr.pioneers.api.multiblock.MultiblockRegistry.of(b.controller());
        if (spec != null && spec.shape() == fr.pioneers.api.multiblock.MultiblockSpec.Shape.TEMPLATE) {
            if (b.controller() instanceof fr.pioneers.tech.industry.multi.ControllerBlock c && Cleanroom.is(c.machine)) {
                //1.0.4-beta (lot 32) : salle blanche : ses vraies valeurs (operations a la fois, FE/t ; GOPS, QOPS), jamais l'echelle MKI -> MKIV
                Cleanroom.lines(c.machine).forEach(line -> sizeTip.add(line.copy().withStyle(ChatFormatting.DARK_AQUA)));
            } else {
                sizeTip.add(Component.translatable("jei.pioneers.multiblock.parallel", fr.pioneers.tech.industry.Tiers.parallelCap(0),
                      fr.pioneers.tech.industry.Tiers.parallelCap(1), fr.pioneers.tech.industry.Tiers.parallelCap(2), fr.pioneers.tech.industry.Tiers.parallelCap(3))
                      .withStyle(ChatFormatting.DARK_AQUA));
            }
            var compact = fr.pioneers.api.multiblock.MultiblockRegistry.compact(spec);
            if (compact != null && !compact.blocks().isEmpty()) {
                sizeTip.add(Component.translatable("jei.pioneers.multiblock.compact", Component.translatable("block.pioneers." + compact.blocks().get(0)))
                      .withStyle(ChatFormatting.GOLD));
            }
        }
        //1.0.5-beta (lot 27, retour 33) : le nombre de blocs est celui des blocs EXIGES ; les facultatifs (tube de niveau...) en infobulle
        List<Component> blocksTip = new ArrayList<>();
        blocksTip.add(Component.translatable("jei.pioneers.multiblock.blocks_tip", Texts.grouped(b.free())).withStyle(ChatFormatting.GRAY));
        if (b.optionalCount() > 0) {
            blocksTip.add(Component.translatable("jei.pioneers.multiblock.blocks_optional", Texts.grouped(b.optionalCount())).withStyle(ChatFormatting.GREEN));
        }
        return List.of(JeiDraw.Chip.of(JeiDraw.Pic.SIZE, Component.translatable("jei.pioneers.multiblock.size", length, width, b.sizeY()), JeiDraw.LIGHT,
                    sizeTip.toArray(new Component[0])),
              JeiDraw.Chip.of(JeiDraw.Pic.BLOCKS, Component.translatable("jei.pioneers.multiblock.blocks", Texts.grouped(b.required())), JeiDraw.LIGHT,
                    blocksTip.toArray(new Component[0])));
    }

    // ------------------------------------------------------------------ cases

    /**
     * Quantite sur une case (en bas a droite, ombree, reduite au-dela de deux chiffres). 1.0.5-beta (lot 27) : case FACULTATIVE ({@code optional}) :
     * quantite en vert pale et « ? » en haut a gauche (infobulle : « Facultatif : la structure se forme sans »).
     */
    record Count(int n, boolean optional) implements IDrawable {

        static final int OPTIONAL_COLOR = 0xFFA8E6A0;

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public void draw(@NotNull GuiGraphics g, int x, int y) {
            String t = n < 10_000 ? String.valueOf(n) : n / 1000 + "k";
            float s = t.length() <= 2 ? 1F : t.length() == 3 ? 0.75F : 0.6F;
            Component c = Component.literal(t);
            float w = font().width(c) * s;
            g.pose().pushPose();
            g.pose().translate(x + 17 - w, y + 17 - 8 * s, 200);
            g.pose().scale(s, s, 1);
            g.drawString(font(), c, 0, 0, optional ? OPTIONAL_COLOR : 0xFFFFFFFF, true);
            g.pose().popPose();
            if (optional) {
                g.pose().pushPose();
                g.pose().translate(x, y, 200);
                g.pose().scale(0.75F, 0.75F, 1);
                g.drawString(font(), "?", 1, 1, OPTIONAL_COLOR, true);
                g.pose().popPose();
            }
        }
    }

    private static List<ItemStack> stacks(List<Block> blocks) {
        List<ItemStack> out = new ArrayList<>();
        for (Block b : blocks) {
            Item item = b.asItem();
            if (item != Items.AIR) {
                out.add(new ItemStack(item));
            }
        }
        return out;
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, @NotNull Entry entry, @NotNull IFocusGroup focuses) {
        int i = 0;
        boolean scroll = entry.bill.lines().size() > COLS * ROWS;
        for (BlueprintBill.Line line : entry.bill.lines()) {
            List<ItemStack> stacks = stacks(line.family());
            if (stacks.isEmpty()) {
                continue;
            }
            int col = i % COLS, row = i / COLS;
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, GRID_X + 1 + col * SLOT, 1 + (scroll ? 0 : row * SLOT))
                  .setStandardSlotBackground().addItemStacks(stacks).setOverlay(new Count(line.count(), line.optional()), 0, 0);
            slot.addRichTooltipCallback((view, tooltip) -> {
                tooltip.add(Component.translatable("jei.pioneers.multiblock.count", Texts.grouped(line.count())).withStyle(ChatFormatting.YELLOW));
                if (line.optional()) {
                    //1.0.5-beta (lot 27, retour 33) : case que la validation n'exige pas (tube de niveau de la chaudiere...)
                    tooltip.add(Component.translatable("jei.pioneers.multiblock.optional").withStyle(ChatFormatting.GREEN));
                }
                if (stacks.size() > 1) {
                    tooltip.add(Component.translatable("jei.pioneers.multiblock.any_tier").withStyle(ChatFormatting.AQUA));
                }
                if (!line.others().isEmpty()) {
                    MutableComponent names = Component.empty();
                    int shown = 0;
                    for (Block b : line.others()) {
                        if (shown == 3) {
                            names.append(Component.literal(", …"));
                            break;
                        }
                        names.append(shown == 0 ? Component.empty() : Component.literal(", ")).append(b.getName());
                        shown++;
                    }
                    tooltip.add(Component.translatable("jei.pioneers.multiblock.also", names).withStyle(ChatFormatting.GRAY));
                }
            });
            i++;
        }
        Block c = entry.bill.controller();
        if (c != null && c.asItem() != Items.AIR) {
            // 1.0.3-beta (lot 6b) : seul acces du controleur a son plan (touche R), la page n'a plus de catalyseurs
            builder.addSlot(RecipeIngredientRole.OUTPUT, CTRL_X + 1, CTRL_Y + 1).setStandardSlotBackground().addItemStack(new ItemStack(c));
        }
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Entry entry, @NotNull IFocusGroup focuses) {
        View view = new View(entry);
        builder.addWidget(view);
        builder.addGuiEventListener(view);
        if (entry.bill.lines().size() > COLS * ROWS) {
            //Plus de 16 sortes de blocs : grille qui defile (3 colonnes + barre)
            builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(RecipeIngredientRole.INPUT), COLS - 1, ROWS).setPosition(GRID_X, 0);
        }
    }

    @Override
    protected void drawContent(@NotNull Entry entry, @NotNull mezz.jei.api.gui.ingredient.IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX,
          double mouseY) {
        GuiDraw.right(graphics, font(), Component.translatable("jei.pioneers.multiblock.controller"), CTRL_X - 3, CTRL_Y + 6.5F, SMALL, TEXT_LIGHT);
    }

    // ------------------------------------------------------------------ apercu 3D

    /** Apercu : widget (dessin) et ecouteur de souris (rotation, couches). Un par page affichee : son etat ne survit pas a la page. */
    static final class View implements IRecipeWidget, IJeiGuiEventListener {

        private final Entry entry;
        private float yaw = 225, pitch = 30;
        private int layer = -1;
        private long lastFrame = System.currentTimeMillis(), lastTouch;

        View(Entry entry) {
            this.entry = entry;
        }

        @NotNull
        @Override
        public ScreenPosition getPosition() {
            return new ScreenPosition(0, 0);
        }

        @NotNull
        @Override
        public ScreenRectangle getArea() {
            return new ScreenRectangle(0, 0, PV_W, BAR_Y + BAR_H);
        }

        private int layers() {
            return Math.max(1, entry.bill.layers());
        }

        @Override
        public void draw(@NotNull GuiGraphics g, double mouseX, double mouseY) {
            GuiDraw.screen(g, 0, 0, PV_W, PV_H);
            long now = System.currentTimeMillis();
            if (now - lastTouch > 2500) {
                yaw += (now - lastFrame) * 0.018F;                                                     //un tour en 20 s
            }
            lastFrame = now;
            MultiblockMesh mesh = MultiblockMesh.of(entry.key, entry.bill);
            float diag = (float) Math.sqrt(mesh.sx * mesh.sx + mesh.sy * mesh.sy + mesh.sz * mesh.sz);
            float s = Math.min(PV_W, PV_H) * 0.92F / Math.max(1F, diag);
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(PV_W / 2F, PV_H / 2F, 150);
            pose.scale(s, -s, s);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-mesh.sx / 2F, -mesh.sy / 2F, -mesh.sz / 2F);
            Lighting.setupFor3DItems();
            mesh.render(pose, g.bufferSource(), layer);
            pose.popPose();
            bar(g, mouseX, mouseY);
        }

        /** Barre des couches : ▼ (descendre), libelle (toutes / couche n sur N), ▲ (monter). */
        private void bar(GuiGraphics g, double mx, double my) {
            int y = BAR_Y;
            button(g, 0, y, false, hit(mx, my, 0));
            button(g, PV_W - BAR_H, y, true, hit(mx, my, PV_W - BAR_H));
            Component label = layer < 0 ? Component.translatable("jei.pioneers.multiblock.all_layers")
                                        : Component.translatable("jei.pioneers.multiblock.layer", layer + 1, layers());
            GuiDraw.center(g, font(), label, PV_W / 2F, y + 3, SMALL, layer < 0 ? JeiDraw.DIM : JeiDraw.ACCENT, PV_W - 2 * BAR_H - 4);
        }

        private static boolean hit(double mx, double my, int x) {
            return mx >= x && mx < x + BAR_H && my >= BAR_Y && my < BAR_Y + BAR_H;
        }

        private static void button(GuiGraphics g, int x, int y, boolean up, boolean hover) {
            g.fill(x, y, x + BAR_H, y + BAR_H, hover ? 0xFF2E3A44 : JeiDraw.SLOT_IN);
            g.fill(x + 1, y + 1, x + BAR_H - 1, y + BAR_H - 1, hover ? 0xFF1E2A33 : JeiDraw.SLOT_FILL);
            for (int r = 0; r < 4; r++) {
                int half = up ? r : 3 - r;
                g.fill(x + 6 - half, y + 4 + r, x + 6 + half, y + 5 + r, JeiDraw.LIGHT);
            }
        }

        @Override
        public void getTooltip(@NotNull ITooltipBuilder tooltip, double mouseX, double mouseY) {
            if (mouseX >= 0 && mouseX < PV_W && mouseY >= 0 && mouseY < PV_H) {
                tooltip.add(Component.translatable("jei.pioneers.multiblock.drag").withStyle(ChatFormatting.GRAY));
                MultiblockMesh mesh = MultiblockMesh.of(entry.key, entry.bill);
                if (mesh.truncated) {
                    tooltip.add(Component.translatable("jei.pioneers.multiblock.truncated").withStyle(ChatFormatting.GOLD));
                }
            }
        }

        private void step(int delta) {
            int n = layers();
            if (delta > 0) {
                layer = layer < 0 ? 0 : layer + 1 >= n ? -1 : layer + 1;
            } else {
                layer = layer < 0 ? n - 1 : layer - 1;
            }
            lastTouch = System.currentTimeMillis();
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (hit(mx, my, 0)) {
                step(-1);
                return true;
            }
            if (hit(mx, my, PV_W - BAR_H)) {
                step(1);
                return true;
            }
            if (my >= BAR_Y && my < BAR_Y + BAR_H) {
                layer = -1;
                return true;
            }
            lastTouch = System.currentTimeMillis();
            return mx >= 0 && mx < PV_W && my >= 0 && my < PV_H;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
            yaw += (float) dragX * 2F;
            pitch = Math.max(-10F, Math.min(89F, pitch + (float) dragY * 2F));
            lastTouch = System.currentTimeMillis();
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            step(delta > 0 ? 1 : -1);
            return true;
        }
    }
}
