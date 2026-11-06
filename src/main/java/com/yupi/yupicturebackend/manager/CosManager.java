package com.yupi.yupicturebackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.common.ImageProcessRequest;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.yupi.yupicturebackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    // ... 一些操作 COS 的方法

    /**
     * 上传本地对象
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传对象（附带图片信息，并持久化生成缩略图）
     * <p>
     * 缩略图由数据万象在云端生成，规则为最长边/宽度 600，输出为同名 <code>_thumbnail.jpg</code>，
     * 原图保持不变，因此每次上传会在 COS 中产生「原图 + 缩略图」两个对象。
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 持久化生成缩略图（CI 规则的 fileId 必须以 / 开头，缺少时数据万象会静默跳过处理）
        PicOperations.Rule thumbnailRule = new PicOperations.Rule();
        thumbnailRule.setBucket(cosClientConfig.getBucket());
        thumbnailRule.setFileId("/" + getThumbnailPath(key).replaceAll("^/+", ""));
        thumbnailRule.setRule("imageMogr2/thumbnail/600x/format/jpg");
        picOperations.setRules(Collections.singletonList(thumbnailRule));
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传对象并取回云端实测的图片信息，不生成任何派生对象。
     * <p>
     * 与 {@link #putPictureObject} 的差别只有缩略图规则：视频封面只需要宽高/格式，
     * 再套一层 imageMogr2 会在桶里多出一个没人引用的 _thumbnail.jpg。
     *
     * @param key  唯一键
     * @param file 文件
     * @return 数据万象返回的原图信息（宽高、格式、体积）
     */
    public ImageInfo putImageObjectWithInfo(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest).getCiUploadResult().getOriginalInfo().getImageInfo();
    }

    /**
     * 生成带"另存为"文件名的限时下载链接。
     * <p>
     * 直接用对象 url 下载会得到存储 key 里的随机文件名；源站域名本身就会附带
     * Content-Disposition: attachment，但预签名里显式覆盖才能把文件名还给用户。
     * 文件名按 RFC 5987 的 filename* 编码，中文名不会被截断成乱码。
     */
    public String buildDownloadUrl(String key, String filename, int expireMinutes) {
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(cosClientConfig.getBucket(), key.replaceAll("^/+", ""), HttpMethodName.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + expireMinutes * 60_000L));
        ResponseHeaderOverrides responseHeaders = new ResponseHeaderOverrides();
        responseHeaders.setContentDisposition("attachment; filename*=UTF-8''"
                + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        request.setResponseHeaders(responseHeaders);
        return cosClient.generatePresignedUrl(request).toString();
    }

    /**
     * 根据原图 key 计算缩略图的对象路径（与上传时的数据万象规则保持一致）
     */
    public static String getThumbnailPath(String key) {
        int dotIndex = key.lastIndexOf('.');
        String mainName = dotIndex > 0 ? key.substring(0, dotIndex) : key;
        return mainName + "_thumbnail.jpg";
    }

    /**
     * 根据原图 key 计算编辑产物的对象路径（保留原扩展名，即保持原图格式）
     */
    public static String getEditedPath(String key, String suffix) {
        int dotIndex = key.lastIndexOf('.');
        String mainName = dotIndex > 0 ? key.substring(0, dotIndex) : key;
        String ext = dotIndex > 0 ? key.substring(dotIndex + 1) : "jpg";
        return String.format("%s_edit_%s.%s", mainName, suffix, ext);
    }

    // ==================== 画面编辑（云上处理） ====================

    /**
     * 云上数据处理（同步）：对 COS 中已存在的对象执行 imageMogr2 规则并另存到 targetKey。
     * <p>
     * transformRule 是 imageMogr2 之后的规则段（如 "/rotate/90/flip/horizontal"）。
     * 注意 imageMogr2 各操作的执行顺序与书写顺序无关（实测裁剪 cut 总是先于
     * 缩放/旋转执行），需要控制先后顺序时拆成多次调用。
     *
     * @return 处理结果：OriginalInfo 为源对象信息，ProcessResults 为各规则产物信息
     */
    public CIUploadResult transformPictureObject(String objectKey, String targetKey, String transformRule) {
        ImageProcessRequest request = new ImageProcessRequest(cosClientConfig.getBucket(), objectKey);
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);
        PicOperations.Rule rule = new PicOperations.Rule();
        rule.setBucket(cosClientConfig.getBucket());
        // CI 规则的 fileId 必须以 / 开头，缺少时数据万象会静默跳过处理
        rule.setFileId("/" + targetKey.replaceAll("^/+", ""));
        rule.setRule("imageMogr2" + transformRule);
        picOperations.setRules(Collections.singletonList(rule));
        request.setPicOperations(picOperations);
        return cosClient.processImage(request);
    }

    /**
     * 查询对象字节数（编辑后回填 picSize 用）
     */
    public long getObjectSize(String key) {
        return cosClient.getObjectMetadata(cosClientConfig.getBucket(), key).getContentLength();
    }

    /**
     * 拼接对象的公网访问 url（host 与 key 各自规范化斜杠，避免出现 // 形式）
     */
    public String buildObjectUrl(String key) {
        return cosClientConfig.getHost().replaceAll("/+$", "") + "/" + key.replaceAll("^/+", "");
    }

    /**
     * 从图片 url 提取 COS 对象 key：去掉协议与域名、去掉前导斜杠。
     * 例：https://host/public/1/a.png -> public/1/a.png
     */
    public String extractObjectKey(String url) {
        String host = cosClientConfig.getHost();
        String key = url.startsWith(host) ? url.substring(host.length()) : url;
        return key.replaceAll("^/+", "");
    }

    /**
     * 提取 COS 对象 key，但仅当 url 确实指向本桶（域名前缀匹配）；否则返回 null。
     * <p>
     * 清理任务删除对象前必须过这道闸：{@link #extractObjectKey} 对外部 url 会原样返回，
     * 直接拿去 deleteObject 等于拿着任意字符串当 key 使。域名只做字符串前缀匹配即可——
     * 即便恶意构造出 host.evil.com 这种前缀撞车，删除动作也只落在本桶内，无越权面。
     */
    public String tryExtractOwnObjectKey(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String host = cosClientConfig.getHost().replaceAll("/+$", "");
        return url.startsWith(host) ? extractObjectKey(url) : null;
    }

    /**
     * 上传流式对象
     *
     * @param key         唯一键
     * @param inputStream 流式文件
     */
    public PutObjectResult putObjectStream(String key, InputStream inputStream) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, inputStream, objectMetadata);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 流式上传对象（附带图片信息）
     *
     * @param key         唯一键
     * @param inputStream 流式文件
     */
    public PutObjectResult putPictureObjectStream(String key, InputStream inputStream) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, inputStream, objectMetadata);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 下载流式对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 删除对象（只删指定 key，缩略图等同前缀对象需要时单独删除）
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }


}

