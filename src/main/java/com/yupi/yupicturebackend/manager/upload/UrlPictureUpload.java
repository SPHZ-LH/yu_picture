package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.RemoteImageManager;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-01-14
 * @description Url 上传图片
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    @Resource
    private RemoteImageManager remoteImageManager;

    /**
     * 校验输入源：协议与解析结果必须落在公网。
     * <p>
     * 类型与大小不再在这里预检——下载链路（{@link #processFile}）按文件头判定格式、
     * 边下边数限流，比 HEAD 响应头可靠（Content-Type 可伪造、Content-Length 可缺失）。
     */
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");
        remoteImageManager.validatePublicHttpUrl(fileUrl);
    }

    /**
     * 获取输入源的原始文件名
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    /**
     * 处理输入源并生成本地临时文件。
     * <p>
     * 下载必须走 {@link RemoteImageManager}：自己接管跳转并对<b>每一跳</b>重新做公网校验、
     * 流式边下边数（上限 20MB）。此前直接用 HttpUtil.downloadFile，它会自动跟随跳转
     * 且不校验跳转目标——公网域名 302 到内网即可绕过入口校验，是个登录用户可用的 SSRF 入口。
     */
    @Override
    protected void processFile(Object inputSource, File file) {
        String fileUrl = (String) inputSource;
        remoteImageManager.downloadToTempFile(fileUrl, file);
    }
}
