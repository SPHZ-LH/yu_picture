package com.yupi.yupicturebackend.constant;

/**
 * 相似图搜索的输入约定。
 * <p>
 * 查询图大小上限只有一处定义：本地文件（Controller 的 multipart 校验）与图片链接
 * （{@code RemoteImageManager} 的下载限额）是同一件事的两种输入方式，两处各写一份迟早
 * 出现「本地上传能收、链接上传被拒」的口径错位。取值与图库上传上限（50MB）对齐——
 * 用户能传进图库的图，也应该能拿来搜。
 *
 * @author SPHZ
 */
public interface PictureSearchConstant {

    /**
     * 查询图大小上限（字节）
     */
    long MAX_QUERY_IMAGE_BYTES = 50 * 1024 * 1024L;

    /**
     * 查询图大小上限（MB），由字节上限算出，用于拼接提示语
     */
    long MAX_QUERY_IMAGE_MB = MAX_QUERY_IMAGE_BYTES / 1024 / 1024;

    /**
     * 超限提示语：两条输入路径与前端三处提示共用同一句话
     */
    String QUERY_IMAGE_OVER_SIZE_MESSAGE = "图片不能超过 " + MAX_QUERY_IMAGE_MB + "MB";
}
