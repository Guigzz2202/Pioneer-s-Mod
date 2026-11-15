package fr.pioneers.client.farm;

import fr.pioneers.Pioneers;
import fr.pioneers.client.look.CardPlacement;
import fr.pioneers.client.look.HudAnchor;
import fr.pioneers.client.look.HudZones;
import fr.pioneers.client.look.LookCardClient;
import fr.pioneers.client.look.LookHudConfig;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.farm.crop.CropDef;
import fr.pioneers.farm.crop.CropTopBlock;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.farm.crop.MushroomCropBlock;
import fr.pioneers.farm.device.ScarecrowBlock;
import fr.pioneers.farm.food.CheeseWheelBlock;
import fr.pioneers.farm.tree.FruitLeavesBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Fiche au regard de l'Agriculture 2.0 (a droite du reticule) : culture visee (stade, climat, genes en se baissant), feuilles d'arbre
 * fruitier (cycle du fruit, climat) ; les animaux et les poissons ajoutent leurs fiches par {@link #ENTITY_PROVIDERS}. Panneau sombre du mod,
 * liseré de la couleur du sujet.
 * <p>
 * 1.6 : dessin synthetique : libelles en colonne et valeurs alignees, largeur calculee sur le texte reel.
 * <p>
 * 1.0.5-beta (lot 14, Guillaume : « Infobulles dans le monde : en bas a droite (configurable), plus petites, textes jamais tronques ») : mise
 * en page pure {@link CardLayout} (lignes compactes, retour a la ligne, plus aucun « … » ni nombre maximal de rangs), place pure
 * {@link CardPlacement} (coin regle, defaut en bas a droite, evite les autres affichages), echelle {@link LookHudConfig} (75 % par defaut,
 * jamais sous un pixel d'ecran par pixel de la police). Banc : {@code tools/look/card_bench.sh} (vraie police, GUI scale 1 a 4).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FarmHud {

    private FarmHud() {
    }

    /** Fiches des entites visees (animaux, poissons...) : renvoie null si l'entite ne les concerne pas. */
    public interface EntityProvider {

        FarmCard card(Entity entity, boolean sneaking);
    }

    public static final List<EntityProvider> ENTITY_PROVIDERS = new ArrayList<>();

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        //1.0.5-beta (lot 14) : apres les barres (hauteurs gauche / droite de ForgeGui connues) et le nom de l'objet tenu
        event.registerAbove(VanillaGuiOverlay.ITEM_NAME.id(), "farm_card", FarmHud::render);
    }

    public static int climateColour(Climate.Status status) {
        return switch (status) {
            case IDEAL -> 0x6EDC5A;
            case GREENHOUSE -> 0x5AD2E6;
            case TOLERABLE -> 0xF0B040;
            case HOSTILE -> 0xF05050;
        };
    }

    /** Fiche du dernier tick (cle : heure, cible visee, Maj). */
    @org.jetbrains.annotations.Nullable
    private static FarmCard tickCard;
    private static long tickCardAt = Long.MIN_VALUE, tickCardTarget;
    private static int tickCardKind;
    private static boolean tickCardSneak;

    /**
     * 1.0.5-beta (lot 29, reprise du §2 « client ») : la fiche au regard (lecture de la tile visee, textes traduits) est faite UNE FOIS PAR
     * TICK DU CLIENT pour une meme cible (case ou bete visee, Maj), et non plus a chaque image ; une autre cible la refait aussitot.
     */
    @org.jetbrains.annotations.Nullable
    private static FarmCard cachedCard(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (mc.level == null || mc.player == null || hit == null) {
            return card(mc);
        }
        int kind = hit instanceof EntityHitResult ? 1 : hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK ? 2 : 0;
        long target = kind == 1 ? ((EntityHitResult) hit).getEntity().getId() : kind == 2 ? ((BlockHitResult) hit).getBlockPos().asLong() : 0;
        long tick = mc.level.getGameTime();
        boolean sneak = mc.player.isShiftKeyDown();
        if (tick != tickCardAt || kind != tickCardKind || target != tickCardTarget || sneak != tickCardSneak) {
            tickCard = card(mc);
            tickCardAt = tick;
            tickCardKind = kind;
            tickCardTarget = target;
            tickCardSneak = sneak;
        }
        return tickCard;
    }

    private static FarmCard card(Minecraft mc) {
        HitResult hit = mc.hitResult;
        Level level = mc.level;
        if (hit == null || level == null || mc.player == null) {
            return null;
        }
        boolean sneaking = mc.player.isShiftKeyDown();
        if (hit instanceof EntityHitResult e) {
            for (EntityProvider provider : ENTITY_PROVIDERS) {
                FarmCard c = provider.card(e.getEntity(), sneaking);
                if (c != null) {
                    return c;
                }
            }
            return null;
        }
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = b.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropTopBlock) {
            pos = pos.below();
            state = level.getBlockState(pos);
        }
        if (state.getBlock() instanceof FarmCropBlock crop) {
            return cropCard(level, pos, state, crop, sneaking);
        }
        //1.0.5-beta (lot 41, retour 60) : plus de fiche au regard pour les postes de cuisine ni les appareils de la ferme a interface (l'interface
        //les remplace) ; restent l'epouvantail (decor), les cultures, les arbres, les animaux et les meules de fromage
        //Blocs de la ferme animee (epouvantail : le haut renvoie au bas)
        if (state.getBlock() instanceof ScarecrowBlock && state.getValue(ScarecrowBlock.HALF)
            == DoubleBlockHalf.UPPER) {
            pos = pos.below();
        }
        if (level.getBlockEntity(pos) instanceof FarmCard.Source source) {
            return source.card(sneaking);
        }
        //1.4 : fiches calculees par le serveur (age mecanique, forge primitive...)
        FarmCard remote = LookCardClient.card(b.getBlockPos());
        if (remote != null) {
            return remote;
        }
        //1.0.5-beta (lot 7) : piege a rat (etat lu dans le bloc)
        if (state.getBlock() instanceof fr.pioneers.wild.RatTrapBlock trap) {
            return trap.card(state);
        }
        if (state.getBlock() instanceof CheeseWheelBlock wheel) {
            return wheel.card(level, pos, state);
        }
        if (state.getBlock() instanceof FruitLeavesBlock leaves && state.getValue(FruitLeavesBlock.FRUIT) > 0) {
            int stage = state.getValue(FruitLeavesBlock.FRUIT);
            List<FarmCard.Line> lines = new ArrayList<>();
            lines.add(FarmCard.Line.gauge(FarmCard.label("fruit"), (stage - 1) / 3F, FarmCard.word("fruit_stage." + stage), stage == 4 ? 0xF0C040 : 0x7CC860));
            Climate.Status climate = leaves.climate(level, pos);
            lines.add(FarmCard.Line.pair(FarmCard.label("climate"), climateWord(climate), climateColour(climate)));
            return new FarmCard(new ItemStack(state.getBlock()), Component.translatable("farm.pioneers.tree." + leaves.def().id()), 0x7CC860, lines);
        }
        return null;
    }

    private static FarmCard cropCard(Level level, BlockPos pos, BlockState state, FarmCropBlock crop, boolean sneaking) {
        CropDef def = crop.def();
        Genes genes = FarmCropBlock.genes(level, pos);
        List<FarmCard.Line> lines = new ArrayList<>();
        if (sneaking) {
            //1.6 : accroupi = les quatre genes, rien d'autre
            for (int i = 0; i < 4; i++) {
                int v = genes.get(i);
                int colour = v >= 8 ? FarmCard.OK : v >= 5 ? 0xE8D84A : v >= 3 ? FarmCard.WARN : FarmCard.BAD;
                lines.add(FarmCard.Line.gauge(Component.translatable("farm.pioneers.gene." + i), v / 10F, FarmCard.ratio(v, 10), colour));
            }
        } else {
            int age = crop.getAge(state), max = crop.getMaxAge();
            lines.add(age >= max ? FarmCard.Line.gauge(FarmCard.label("growth"), 1F, FarmCard.word("ripe").withStyle(s -> s.withColor(0xF0C040)), 0xF0C040)
                                 : FarmCard.Line.gauge(FarmCard.label("growth"), age / (float) max, FarmCard.percent(age / (float) max), 0x7CC860));
            Climate.Status climate = crop.climate(level, pos);
            lines.add(FarmCard.Line.pair(FarmCard.label("climate"), climateWord(climate), climateColour(climate)));
            //1.0.5 (lot 1) : saison (jusqu'a quand ca pousse) et delai de grace hors climat
            FarmCard.Line season = seasonLine(level, pos, def, genes, climate);
            if (season != null) {
                lines.add(season);
            }
            if (crop instanceof MushroomCropBlock mushroom) {
                //1.0.3 (lot 13) : champignon : lumiere (a l'ombre) et sol (substrat, mycelium / podzol, hydroponique)
                if (!MushroomCropBlock.shaded(level, pos)) {
                    lines.add(FarmCard.Line.pair(FarmCard.label("light"), FarmCard.word("too_bright"), FarmCard.BAD));
                }
                float soil = mushroom.soilFactor(level, pos);
                lines.add(FarmCard.Line.pair(FarmCard.label("water"), FarmCard.word(soil > 1.3F ? "soil_hydroponic" : soil > 1F ? "soil_substrate"
                      : "soil_wild"), soil > 1F ? FarmCard.OK : FarmCard.WARN));
            } else if (def.paddy() && !FarmCropBlock.flooded(level, pos)) {
                lines.add(FarmCard.Line.pair(FarmCard.label("water"), FarmCard.word("paddy_dry"), FarmCard.BAD));
            } else if (FarmCropBlock.hydroponic(level, pos)) {
                //1.12.3 (lot F3) : substrat hydroponique : pousse plus vite, climat inchange
                lines.add(FarmCard.Line.pair(FarmCard.label("water"), FarmCard.word("soil_hydroponic"), FarmCard.OK));
            } else if (!FarmCropBlock.irrigated(level, pos)) {
                lines.add(FarmCard.Line.pair(FarmCard.label("water"), FarmCard.word("soil_dry"), FarmCard.WARN));
            }
            lines.add(FarmCard.Line.pair(FarmCard.label("genes"), stars(genes.stars()), 0xF0C040));
            lines.add(FarmCard.Line.hint(FarmCard.word("hint.genes")));
        }
        return new FarmCard(crop.seedStack(genes), Component.translatable("farm.pioneers.crop." + def.id()), 0xFF000000 | def.leaf(), lines);
    }

    /**
     * 1.0.5 (lot 1) : ligne de saison de la fiche d'une culture : « Climat idéal jusqu'à la fin de l'été », « Pousse ralentie jusqu'à la fin
     * de l'automne », « Cultivable toute l'année ici », « Sous terre : climat fixe, 8 °C », « Hors saison : fanera dans 18 h » (cultivable
     * a une autre saison), « Hors climat : fanera dans 1 j » ; rien sous serre. Prevision : {@link Climate#outlook} (pur, sur un an).
     */
    @Nullable
    static FarmCard.Line seasonLine(Level level, BlockPos pos, CropDef def, Genes genes, Climate.Status climate) {
        if (climate == Climate.Status.GREENHOUSE) {
            return null;
        }
        boolean irrigated = FarmCropBlock.irrigated(level, pos);
        Climate.Outlook o = Climate.outlook(Climate.place(level, pos), fr.pioneers.season.Seasons.now(level), def.tMin(), def.tMax(), def.hMin(),
              def.hMax(), genes.tolerance(), irrigated);
        if (!climate.grows()) {
            fr.pioneers.farm.crop.CropTile tile = FarmCropBlock.tile(level, pos);
            long left = FarmCropBlock.graceLeft(tile == null ? -1 : tile.hostileSince(), level.getDayTime());
            return FarmCard.Line.text(FarmCard.word(o.inSeason() ? "season.out_of_season" : "season.out_of_climate",
                  fr.pioneers.season.SeasonTexts.duration(left)), FarmCard.BAD);
        }
        if (Climate.underground(level, pos)) {
            return FarmCard.Line.text(FarmCard.word("season.underground"), FarmCard.INFO);
        }
        if (!o.seasonal()) {
            return null;
        }
        if (o.until() == null) {
            return FarmCard.Line.text(FarmCard.word("season.all_year"), FarmCard.OK);
        }
        Component end = Component.translatable("season.pioneers.end_of." + o.until().id());
        return FarmCard.Line.text(FarmCard.word(climate == Climate.Status.IDEAL ? "season.ideal_until" : "season.slow_until", end),
              climate == Climate.Status.IDEAL ? FarmCard.OK : FarmCard.WARN);
    }

    /** 1.6 : climat en un mot (Ideal, Serre, Difficile, Hostile). */
    public static Component climateWord(Climate.Status status) {
        return FarmCard.word("climate." + status.name().toLowerCase(Locale.ROOT));
    }

    /** Etoiles pleines et vides (sur 5). */
    public static Component stars(int n) {
        return Component.literal("\u2605".repeat(Math.max(0, Math.min(5, n))) + "\u2606".repeat(5 - Math.max(0, Math.min(5, n))));
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) {
            return;
        }
        FarmCard card = cachedCard(mc);
        if (card == null) {
            return;
        }
        //1.0.5-beta (lot 14) : coin, ecart et taille reglables (pioneers-client.toml, section look_card ; guide « Affichage a l'ecran »),
        //defaut en bas a droite a 75 % ; la fiche evite la barre d'objets, les barres, les sous-titres, les effets, la minimap et le Periple
        int good = 0, bad = 0;
        for (net.minecraft.world.effect.MobEffectInstance e : mc.player.getActiveEffects()) {
            if (e.showIcon()) {
                if (e.getEffect().isBeneficial()) {
                    good++;
                } else {
                    bad++;
                }
            }
        }
        List<int[]> zones = CardPlacement.vanillaZones(width, height, Math.max(gui.leftHeight, gui.rightHeight), mc.options.showSubtitles().get(), good, bad);
        zones.addAll(HudZones.active(CardPlacement.GAP));
        drawPlaced(g, mc.font, card, width, height, mc.getWindow().getGuiScale(), zones);
    }

    @org.jetbrains.annotations.Nullable
    private static FarmCard placedCard;
    @org.jetbrains.annotations.Nullable
    private static Font placedFont;
    private static long placedKey;
    private static int[] placedBox;
    private static CardLayout.Layout placedLayout;

    /** 1.0.5-beta (lot 26) : point d'ancrage tenu tant que la fiche reste a l'ecran ({@link CardPlacement.Latch}). */
    private static final CardPlacement.Latch LATCH = new CardPlacement.Latch();

    /**
     * 1.0.5-beta (lot 14) : met en page ({@link CardLayout}), choisit l'echelle ({@link LookHudConfig#effective} ; reduite si la fiche ne
     * tiendrait pas en hauteur), place et dessine la fiche. Rend {x, y, largeur, hauteur} a l'ecran.
     * <p>
     * 1.0.5-beta (lot 26, retour 16) : la place part d'un POINT D'ANCRAGE ({@link #anchorPoint}) qui ne depend jamais du contenu et qui est
     * tenu tant que la fiche reste affichee : en bas a droite, le coin bas droit ne bouge plus d'un pixel quand des lignes apparaissent ou
     * que des valeurs changent.
     */
    public static int[] drawPlaced(GuiGraphics g, Font font, FarmCard card, int width, int height, double guiScale, List<int[]> zones) {
        LookHudConfig.Corner corner = LookHudConfig.corner();
        int percent = LookHudConfig.scale(), offX = LookHudConfig.offsetX(), offY = LookHudConfig.offsetY();
        long key = ((((long) width * 8191 + height) * 131 + Math.round(guiScale * 8)) * 7 + corner.ordinal()) * 1009 + percent * 401L + offX * 37L + offY;
        int[] at = LATCH.hold(key, net.minecraft.Util.getMillis(), anchorPoint(width, height, guiScale, percent, corner, offX, offY, zones));
        //1.0.5-beta (lot 29) : mise en page gardee tant que la fiche (meme objet), la police et la place ne changent pas (avant : refaite a
        //chaque image, decoupage des lignes compris)
        long placeKey = (key * 31 + at[0]) * 8191 + at[1];
        int[] box;
        CardLayout.Layout layout;
        if (card == placedCard && font == placedFont && placeKey == placedKey) {
            box = placedBox;
            layout = placedLayout;
        } else {
            box = arrangeAt(font.getSplitter(), card, width, height, guiScale, percent, corner, at[0], at[1]);
            layout = CardLayout.layout(font.getSplitter(), card, box[5]);
            placedCard = card;
            placedFont = font;
            placedKey = placeKey;
            placedBox = box;
            placedLayout = layout;
        }
        float s = Float.intBitsToFloat(box[4]);
        g.pose().pushPose();
        g.pose().translate(box[0], box[1], 200);
        g.pose().scale(s, s, 1);
        draw(g, font, layout);
        g.pose().popPose();
        return new int[]{box[0], box[1], box[2], box[3]};
    }

    /**
     * 1.0.5-beta (lot 26) : point d'ancrage {x, y} de la fiche (pur, lu par les bancs) : la place d'une fiche de REFERENCE (largeur maximale
     * {@link CardLayout#MAX_W} a l'echelle reglee, hauteur {@link CardPlacement#REF_H}) qui evite les zones, puis son coin du cote du reglage.
     */
    public static int[] anchorPoint(int width, int height, double guiScale, int percent, LookHudConfig.Corner corner, int offX, int offY, List<int[]> zones) {
        float s = LookHudConfig.effective(percent, guiScale);
        int refW = (int) Math.ceil(CardLayout.MAX_W * s);
        return CardPlacement.anchor(width, height, refW, CardPlacement.REF_H, corner, offX, offY, zones);
    }

    /**
     * Calcul pur de la place (aussi lu par le banc) : {x, y, largeur a l'ecran, hauteur a l'ecran, bits de l'echelle, largeur permise de la
     * fiche}. 1.0.5-beta (lot 26) : point d'ancrage d'abord ({@link #anchorPoint}), puis {@link #arrangeAt}.
     */
    public static int[] arrange(net.minecraft.client.StringSplitter splitter, FarmCard card, int width, int height, double guiScale, int percent,
          LookHudConfig.Corner corner, int offX, int offY, List<int[]> zones) {
        int[] at = anchorPoint(width, height, guiScale, percent, corner, offX, offY, zones);
        return arrangeAt(splitter, card, width, height, guiScale, percent, corner, at[0], at[1]);
    }

    /**
     * 1.0.5-beta (lot 26) : fiche accrochee au point ({@code ax}, {@code ay}) : la largeur permise = la place du point jusqu'au bord oppose
     * (moins 2 px), ramenee a l'echelle ; si la fiche est plus haute que la place, l'echelle baisse jusqu'a ce qu'elle tienne (jamais sous
     * 0,5) ; la boite part du point vers le cote oppose et n'est ecretee que contre le bord oppose ({@link HudAnchor#box}).
     */
    public static int[] arrangeAt(net.minecraft.client.StringSplitter splitter, FarmCard card, int width, int height, double guiScale, int percent,
          LookHudConfig.Corner corner, int ax, int ay) {
        HudAnchor anchor = CardPlacement.anchorOf(corner);
        int[] room = anchor.room(ax, ay, width, height);
        int roomW = Math.max(16, room[0] - 2), roomH = Math.max(16, room[1] - 2);
        float s = LookHudConfig.effective(percent, guiScale);
        int fit = (int) Math.floor(roomW / s);
        CardLayout.Layout layout = CardLayout.layout(splitter, card, fit);
        if (layout.h() * s > roomH) {
            s = Math.max(0.5F, roomH / (float) layout.h());
            fit = (int) Math.floor(roomW / s);
            layout = CardLayout.layout(splitter, card, fit);
        }
        int w = (int) Math.ceil(layout.w() * s), h = (int) Math.ceil(layout.h() * s);
        int[] b = anchor.box(ax, ay, w, h, width, height);
        return new int[]{b[0], b[1], b[2] - b[0], b[3] - b[1], Float.floatToIntBits(s), fit};
    }

    // ------------------------------------------------------------------ dessin (1.0.5-beta lot 14 : primitives de CardLayout, rien ne deborde)

    /** Dessine une fiche mise en page, coin haut gauche en (0, 0) du repere courant. */
    public static void draw(GuiGraphics g, Font font, CardLayout.Layout l) {
        int w = l.w(), h = l.h();
        g.fill(0, 0, w, h, CardLayout.C_PANEL);
        g.fill(0, 0, w, 1, 0xFF000000 | l.accent());
        g.fill(0, h - 1, w, h, CardLayout.C_EDGE);
        g.fill(0, 0, 1, h, CardLayout.C_EDGE);
        g.fill(w - 1, 0, w, h, CardLayout.C_EDGE);
        g.renderItem(l.icon(), 2, 1);
        for (CardLayout.Bar b : l.bars()) {
            g.fill(b.x(), b.y() + 2, b.x() + CardLayout.BAR_W, b.y() + 7, CardLayout.C_TRACK);
            int fw = Math.round(CardLayout.BAR_W * Math.max(0, b.fill()));
            g.fill(b.x(), b.y() + 2, b.x() + fw, b.y() + 7, 0xFF000000 | b.colour());
            g.fill(b.x(), b.y() + 2, b.x() + fw, b.y() + 3, 0x40FFFFFF);
        }
        for (CardLayout.Icon i : l.icons()) {
            g.renderItem(i.stack(), i.x(), i.y());
            g.renderItemDecorations(font, i.stack(), i.x(), i.y());
            if (i.fill() >= 0) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(i.x() + 1, i.y() + 15, i.x() + 15, i.y() + 17, CardLayout.C_TRACK);
                g.fill(i.x() + 1, i.y() + 15, i.x() + 1 + Math.round(14 * Math.min(1, i.fill())), i.y() + 17, i.fill() >= 1 ? 0xFF6EDC5A : 0xFFF0B040);
                g.pose().popPose();
            }
        }
        for (CardLayout.Text t : l.texts()) {
            g.drawString(font, Language.getInstance().getVisualOrder(t.text()), t.x(), t.y(), t.colour(), true);
        }
    }
}
