package com.yupi.yupicturebackend.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 色系提取的分桶规则（CONTEXT.md「色系」）：dark → gray → 六个色相桶。
 * 用 PNG（无损）构造已知颜色的图，断言整图与混合图的主色/色系。
 */
class ColorExtractorTest {

    /** 纯色 PNG 的字节 */
    private byte[] solidImage(int rgb) throws IOException {
        return twoToneImage(rgb, rgb, 1.0);
    }

    /** 上下拼接两块纯色（topRatio 为上部占比） */
    private byte[] twoToneImage(int topRgb, int bottomRgb, double topRatio) throws IOException {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 64; y++) {
            int rgb = y < 64 * topRatio ? topRgb : bottomRgb;
            for (int x = 0; x < 64; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void pureBlueGoesToBlueFamily() throws IOException {
        String[] result = ColorExtractor.extract(solidImage(new Color(0, 0, 255).getRGB()));
        assertEquals("blue", result[1]);
        assertEquals("#0000FF", result[0]);
    }

    @Test
    void pureRedGoesToRedFamily() throws IOException {
        String[] result = ColorExtractor.extract(solidImage(new Color(255, 0, 0).getRGB()));
        assertEquals("red", result[1]);
        assertEquals("#FF0000", result[0]);
    }

    @Test
    void blackGoesToDarkFamily() throws IOException {
        String[] result = ColorExtractor.extract(solidImage(new Color(0, 0, 0).getRGB()));
        assertEquals("dark", result[1]);
        assertEquals("#000000", result[0]);
    }

    @Test
    void whiteGoesToGrayFamily() throws IOException {
        // 白色亮度 1.0 不满足 dark，饱和度 0 落入 gray
        String[] result = ColorExtractor.extract(solidImage(new Color(255, 255, 255).getRGB()));
        assertEquals("gray", result[1]);
        assertEquals("#FFFFFF", result[0]);
    }

    @Test
    void midGrayGoesToGrayFamily() throws IOException {
        String[] result = ColorExtractor.extract(solidImage(new Color(128, 128, 128).getRGB()));
        assertEquals("gray", result[1]);
        assertEquals("#808080", result[0]);
    }

    @Test
    void dominantBucketWinsAndAveragesItsOwnPixels() throws IOException {
        // 3/4 蓝 + 1/4 红：蓝桶样本多数胜出，主色是蓝桶自己的平均（纯蓝，不被红色像素稀释）
        byte[] bytes = twoToneImage(new Color(0, 0, 255).getRGB(), new Color(255, 0, 0).getRGB(), 0.75);
        String[] result = ColorExtractor.extract(bytes);
        assertEquals("blue", result[1]);
        assertEquals("#0000FF", result[0]);
    }

    @Test
    void unparseableBytesReturnNull() throws IOException {
        assertNull(ColorExtractor.extract("not an image".getBytes(StandardCharsets.UTF_8)));
    }
}
