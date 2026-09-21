package com.gonzotech.machines.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Альфа-маска переднего PNG-GUI: «где рисунок непрозрачный — шкалам туда нельзя».
 * <p>
 * Загружает передний PNG ({@link #forTexture}) через {@link NativeImage}, читает
 * альфу каждого пикселя и строит булеву маску «пиксель свободен для шкалы»
 * (альфа &le; порога). Рендер шкал спрашивает {@link #isOpenAt} для каждого
 * пикселя окна — так проявляющаяся заливка физически не может вылезти за дырки в
 * рисунке: всё, что не дырка, навсегда погребено под оверлеем.
 * <p>
 * Координаты: маска хранится в системе КООРДИНАТ ОКНА (0..175, 0..165). При
 * загрузке учитывается смещение блита листа ({@code texOffsetX/Y}), поэтому для
 * листа 512 со сдвигом −128 пиксель окна {@code (wx,wy)} читается из листа как
 * {@code (wx−offX, wy−offY) = (wx+128, wy+128)}.
 * <p>
 * Кэшируется по {@link ResourceLocation}. Если текстуру не удалось прочитать —
 * возвращается «всё открыто» (шкалы рисуются как раньше), чтобы не ломать GUI.
 */
public final class GuiMask {

    /** Пиксель считается «дыркой» (открытым для шкалы), если альфа ниже порога. */
    private static final int ALPHA_THRESHOLD = 8; // ~ alpha <= 8/255

    private static final int DEFAULT_WIN_W = 176;
    private static final int DEFAULT_WIN_H = 166;

    private static final Map<ResourceLocation, GuiMask> CACHE = new HashMap<>();
    private static final GuiMask ALL_OPEN = new GuiMask(null, 0, 0);

    /** {@code open[y*winW + x]} = можно ли рисовать шкалу в этом пикселе окна. */
    private final boolean[] open;
    private final int winW;
    private final int winH;

    private GuiMask(boolean[] open, int winW, int winH) {
        this.open = open;
        this.winW = winW;
        this.winH = winH;
    }

    /** Маска для стандартного окна 176×166. */
    public static GuiMask forTexture(ResourceLocation fgTexture, int offX, int offY) {
        return forTexture(fgTexture, offX, offY, DEFAULT_WIN_W, DEFAULT_WIN_H);
    }

    /**
     * Маска для переднего PNG размера окна {@code winW×winH}. {@code offX/offY} —
     * смещение блита листа относительно угла окна (для 512-листа обычно −128).
     * Окна нестандартной высоты (завод сплавов 222, фильтр предметов 184) должны
     * передавать свой размер: ниже стандартных 166 px рисунок fg тогда тоже
     * клипует шкалы.
     */
    public static GuiMask forTexture(ResourceLocation fgTexture, int offX, int offY, int winW, int winH) {
        if (fgTexture == null) return ALL_OPEN;
        GuiMask cached = CACHE.get(fgTexture);
        if (cached != null) return cached;
        GuiMask built = build(fgTexture, offX, offY, winW, winH);
        CACHE.put(fgTexture, built);
        return built;
    }

    private static GuiMask build(ResourceLocation tex, int offX, int offY, int winW, int winH) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> res = mc.getResourceManager().getResource(tex);
        if (res.isEmpty()) return ALL_OPEN;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            boolean[] open = new boolean[winW * winH];
            int w = img.getWidth();
            int h = img.getHeight();
            for (int wy = 0; wy < winH; wy++) {
                for (int wx = 0; wx < winW; wx++) {
                    // пиксель окна (wx,wy) → пиксель листа (wx-offX, wy-offY)
                    int sx = wx - offX;
                    int sy = wy - offY;
                    boolean free;
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h) {
                        free = true; // вне листа — считаем свободным
                    } else {
                        int argb = img.getPixel(sx, sy); // NativeImage: 0xAABBGGRR
                        int alpha = (argb >> 24) & 0xFF;
                        free = alpha <= ALPHA_THRESHOLD;
                    }
                    open[wy * winW + wx] = free;
                }
            }
            return new GuiMask(open, winW, winH);
        } catch (Exception e) {
            return ALL_OPEN;
        }
    }

    /** Можно ли рисовать шкалу в пикселе окна (wx,wy)? Вне окна/без маски — да. */
    public boolean isOpenAt(int wx, int wy) {
        if (open == null) return true;
        if (wx < 0 || wy < 0 || wx >= winW || wy >= winH) return true;
        return open[wy * winW + wx];
    }

    /**
     * Наибольший «открытый» (дырочный) прямоугольник внутри заданной области окна.
     * Для прямоугольных дырок в PNG это точное пересечение; шкала клипуется по
     * нему и не может залезть под непрозрачный рисунок.
     *
     * @return {@code [minX,minY,maxXexcl,maxYexcl]} в координатах окна, либо
     *         {@code null} если вся область непрозрачна (шкалу рисовать нельзя).
     */
    public int[] openSubRect(int rx, int ry, int rw, int rh) {
        if (open == null) return new int[]{rx, ry, rx + rw, ry + rh};
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = ry; y < ry + rh; y++) {
            for (int x = rx; x < rx + rw; x++) {
                if (isOpenAt(x, y)) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x + 1 > maxX) maxX = x + 1;
                    if (y + 1 > maxY) maxY = y + 1;
                }
            }
        }
        if (maxX < 0) return null;
        return new int[]{minX, minY, maxX, maxY};
    }

    /** Сбросить кэш (напр. при перезагрузке ресурспаков). */
    public static void clearCache() {
        CACHE.clear();
    }
}
