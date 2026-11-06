package com.yupi.yupicturebackend.util;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片主色与色系提取：降采样到 32x32 后逐像素转 HSB 分桶，
 * 取样本最多的桶的平均 RGB 作为主色；另可提取 top-N 调色板（详情页「画面色彩」色点）。
 *
 * <p>分桶规则（与 CONTEXT.md「色系」一致，屿图 9 色系）：
 * 亮度 < 0.18 → dark；亮度 ≥ 0.88 且饱和度 ≤ 0.30 → light（暖白相 hue 15-100° → cream）；
 * 饱和度 < 0.12 → gray；其余按色相：红 <15°/≥345°、黄 15-70°、绿 70-170°、蓝 170-260°、
 * 紫 260-345°（原 pink 桶已并入）。
 */
public final class ColorExtractor {

    private static final int SAMPLE_SIZE = 32;

    /** 桶下标与色系名的对应关系，顺序不可变（bucketOf 依赖下标） */
    private static final String[] FAMILY_NAMES = {
        "red", "yellow", "green", "blue", "purple", "gray", "dark", "light", "cream",
    };

    private static final int RED = 0;
    private static final int YELLOW = 1;
    private static final int GREEN = 2;
    private static final int BLUE = 3;
    private static final int PURPLE = 4;
    private static final int GRAY = 5;
    private static final int DARK = 6;
    private static final int LIGHT = 7;
    private static final int CREAM = 8;

    /** 调色板取色时两色最小 RGB 距离（欧氏），低于该值视为重复色不重复收录 */
    private static final double PALETTE_MIN_DISTANCE = 60.0;

    private ColorExtractor() {
    }

    /**
     * 提取主色与色系
     *
     * @param imageBytes 图片字节（JPG/PNG 等 ImageIO 可解析格式）
     * @return [mainColor hex, colorFamily]，图片无法解析时返回 null
     */
    public static String[] extract(byte[] imageBytes) throws IOException {
        BufferedImage sample = sample(imageBytes);
        if (sample == null) {
            return null;
        }
        long[] sumR = new long[FAMILY_NAMES.length];
        long[] sumG = new long[FAMILY_NAMES.length];
        long[] sumB = new long[FAMILY_NAMES.length];
        long[] counts = new long[FAMILY_NAMES.length];
        for (int y = 0; y < SAMPLE_SIZE; y++) {
            for (int x = 0; x < SAMPLE_SIZE; x++) {
                int rgb = sample.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                int bucket = bucketOf(hsb[0], hsb[1], hsb[2]);
                sumR[bucket] += r;
                sumG[bucket] += g;
                sumB[bucket] += b;
                counts[bucket]++;
            }
        }
        int dominant = -1;
        long max = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > max) {
                max = counts[i];
                dominant = i;
            }
        }
        if (dominant < 0) {
            return null;
        }
        String hex = String.format("#%02X%02X%02X",
                Math.round(sumR[dominant] / (float) counts[dominant]),
                Math.round(sumG[dominant] / (float) counts[dominant]),
                Math.round(sumB[dominant] / (float) counts[dominant]));
        return new String[]{hex, FAMILY_NAMES[dominant]};
    }

    /**
     * 提取主色板（详情页「画面色彩」色点）：降采样图按 5bit/通道量化计数，
     * 从高频色起贪心收录，与已收录色距离过近的跳过，最多 maxColors 个。
     *
     * @param imageBytes 图片字节
     * @param maxColors  最多返回几个色
     * @return hex 列表（按占比从高到低），图片无法解析时返回空列表
     */
    public static List<String> extractPalette(byte[] imageBytes, int maxColors) throws IOException {
        List<String> empty = List.of();
        if (maxColors <= 0) {
            return empty;
        }
        BufferedImage sample = sample(imageBytes);
        if (sample == null) {
            return empty;
        }
        // 5bit/通道量化（32768 桶）计数：key = r<<10 | g<<5 | b
        Map<Integer, long[]> buckets = new HashMap<>();
        for (int y = 0; y < SAMPLE_SIZE; y++) {
            for (int x = 0; x < SAMPLE_SIZE; x++) {
                int rgb = sample.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int key = (r >> 3) << 10 | (g >> 3) << 5 | (b >> 3);
                long[] stat = buckets.computeIfAbsent(key, k -> new long[4]);
                stat[0]++;
                stat[1] += r;
                stat[2] += g;
                stat[3] += b;
            }
        }
        List<Map.Entry<Integer, long[]>> sorted = new ArrayList<>(buckets.entrySet());
        sorted.sort(Comparator.comparingLong((Map.Entry<Integer, long[]> e) -> e.getValue()[0]).reversed());
        List<String> palette = new ArrayList<>(maxColors);
        List<double[]> accepted = new ArrayList<>(maxColors);
        for (Map.Entry<Integer, long[]> entry : sorted) {
            long[] stat = entry.getValue();
            double r = stat[1] / (double) stat[0];
            double g = stat[2] / (double) stat[0];
            double b = stat[3] / (double) stat[0];
            boolean tooClose = false;
            for (double[] c : accepted) {
                if (distance(r, g, b, c[0], c[1], c[2]) < PALETTE_MIN_DISTANCE) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }
            accepted.add(new double[]{r, g, b});
            palette.add(String.format("#%02X%02X%02X",
                    (int) Math.round(r), (int) Math.round(g), (int) Math.round(b)));
            if (palette.size() >= maxColors) {
                break;
            }
        }
        return palette;
    }

    private static BufferedImage sample(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            return null;
        }
        BufferedImage sample = new BufferedImage(SAMPLE_SIZE, SAMPLE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sample.createGraphics();
        graphics.drawImage(image, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE, null);
        graphics.dispose();
        return sample;
    }

    private static double distance(double r1, double g1, double b1, double r2, double g2, double b2) {
        double dr = r1 - r2;
        double dg = g1 - g2;
        double db = b1 - b2;
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /**
     * HSB → 色系桶下标
     */
    private static int bucketOf(float hue, float saturation, float brightness) {
        if (brightness < 0.18f) {
            return DARK;
        }
        float deg = hue * 360f;
        if (brightness >= 0.88f && saturation <= 0.30f) {
            // 近白高亮：偏黄相（15°-100°）是暖白（米白），其余是冷白（月白）
            return deg >= 15f && deg < 100f ? CREAM : LIGHT;
        }
        if (saturation < 0.12f) {
            return GRAY;
        }
        if (deg < 15f || deg >= 345f) {
            return RED;
        }
        if (deg < 70f) {
            return YELLOW;
        }
        if (deg < 170f) {
            return GREEN;
        }
        if (deg < 260f) {
            return BLUE;
        }
        return PURPLE;
    }
}
