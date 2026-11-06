package com.yupi.yupicturebackend;

import com.qcloud.cos.COSClient;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import javax.annotation.Resource;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手动验证 COS 缩略图生成（会真实上传对象并产生数据万象处理费用）。
 */
@SpringBootTest
class CosThumbnailGenerateTest {

    @Resource
    private PictureUploadTemplate filePictureUpload;

    @Resource
    private COSClient cosClient;

    @Resource
    private CosClientConfig cosClientConfig;

    @Disabled("真实调用 COS 并计费，需要验证缩略图生成时手动运行")
    @Test
    void uploadAndGenerateThumbnail() throws Exception {
        // 生成一张 1200x600 的本地测试 PNG
        BufferedImage image = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(66, 133, 244));
        g.fillRect(0, 0, 1200, 600);
        g.setColor(Color.WHITE);
        g.drawString("thumbnail verify", 40, 60);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bos);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "verify.png", "image/png", bos.toByteArray());

        UploadPictureResult result = null;
        try {
            result = filePictureUpload.uploadPicture(multipartFile, "thumbnail-verify");
            System.out.println("[验证] 原图 url = " + result.getUrl());
            System.out.println("[验证] 缩略图 url = " + result.getThumbnailUrl());
            String originKey = result.getUrl().substring(cosClientConfig.getHost().length())
                    .replaceAll("^/+", "/");
            String thumbnailKey = CosManager.getThumbnailPath(originKey);
            boolean originExists = cosClient.doesObjectExist(cosClientConfig.getBucket(), originKey);
            boolean thumbnailExists = cosClient.doesObjectExist(cosClientConfig.getBucket(), thumbnailKey);
            System.out.println("[验证] 原图对象(" + originKey + ")存在 = " + originExists
                    + "，缩略图对象(" + thumbnailKey + ")存在 = " + thumbnailExists);
            assertTrue(originExists, "原图应存在于 COS");
            assertTrue(thumbnailExists, "缩略图应由数据万象持久化生成");
        } finally {
            // 清理测试对象（host 后的路径去掉多余的前导斜杠即对象 key）
            if (result != null) {
                String originKey = result.getUrl().substring(cosClientConfig.getHost().length())
                        .replaceAll("^/+", "/");
                cosClient.deleteObject(cosClientConfig.getBucket(), originKey);
                cosClient.deleteObject(cosClientConfig.getBucket(), CosManager.getThumbnailPath(originKey));
            }
        }
    }
}
