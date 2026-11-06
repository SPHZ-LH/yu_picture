package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-01-14
 * @description 文件上传图片
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate {


    /**
     * 校验输入源
     */
    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1. 校验文件大小（上限 50MB，与前端 PictureUpload.vue、PictureSearchConstant 三处同口径）
        long fileSize = multipartFile.getSize();
        final long MAX_SIZE = 50 * 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > MAX_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过 50M");
        // 2. 校验文件后缀（统一小写：.PNG / .JPG 这类大写后缀不该被无故拒绝）
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        fileSuffix = fileSuffix == null ? null : fileSuffix.toLowerCase();
        // 允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

    /**
     * 获取输入源的原始文件名
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    /**
     * 处理输入源并生成本地临时文件
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }
}
