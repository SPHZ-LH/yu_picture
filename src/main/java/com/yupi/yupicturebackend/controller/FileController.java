package com.yupi.yupicturebackend.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@RequestMapping("/file")
@RestController
public class FileController {

    /**
     * 允许上传的图片后缀，与魔数表一一对应（docs/adr/0009）
     */
    private static final List<String> ALLOW_AVATAR_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");

    @Resource
    private CosManager cosManager;

    @Resource
    private UserService userService;

    /**
     * 图片上传（登录用户可用，供头像等个人图片）
     * <p>
     * 不复用图片上传模板：模板经数据万象生成缩略图，头像不需要。
     * 校验与存放约定见 docs/adr/0009。
     *
     * @param multipartFile 前端传来的图片
     * @param request       前端发送的 session
     * @return 对象可访问地址
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        validAvatarImage(multipartFile);
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        String key = String.format("avatar/%s/%s_%s.%s", loginUser.getId(), DateUtil.formatDate(new Date()),
                RandomUtil.randomString(16), suffix);
        File file = null;
        try {
            file = File.createTempFile(key, null);
            multipartFile.transferTo(file);
            cosManager.putObject(key, file);
            return ResultUtils.success(cosManager.buildObjectUrl(key));
        } catch (Exception e) {
            log.error("avatar upload error, key = {}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null && !file.delete()) {
                log.error("file delete error, filepath = {}", file.getAbsolutePath());
            }
        }
    }

    /**
     * 头像上传校验：大小 + 后缀白名单 + 魔数嗅探（防改名文件混入对象存储）
     */
    private void validAvatarImage(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(multipartFile.getSize() > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!ALLOW_AVATAR_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
        byte[] header = new byte[12];
        try (InputStream in = multipartFile.getInputStream()) {
            int read = in.read(header);
            ThrowUtils.throwIf(!matchesMagicBytes(suffix, header, read),
                    ErrorCode.PARAMS_ERROR, "文件内容与扩展名不匹配");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件读取失败");
        }
    }

    private boolean matchesMagicBytes(String suffix, byte[] header, int read) {
        switch (suffix) {
            case "jpeg":
            case "jpg":
                return read >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
            case "png":
                return read >= 4 && header[0] == (byte) 0x89 && header[1] == (byte) 0x50
                        && header[2] == (byte) 0x4E && header[3] == (byte) 0x47;
            case "webp":
                return read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default:
                return false;
        }
    }

    /**
     * 测试文件上传（本地上传）
     *
     * @param multipartFile 前端传来的图片
     * @return 图片地址
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {
        // 文件目录
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("/test/%s", filename);
        File file = null;
        try {
            // 上传文件
            // 创建临时文件（内容为空）
            file = File.createTempFile(filepath, null);
            // 内容传输
            multipartFile.transferTo(file);
            cosManager.putObject(filepath, file);
            // 返回可访问地址
            return ResultUtils.success(filepath);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }

    /**
     * 测试文件上传（流式上传）
     *
     * @param multipartFile 前端传来的图片
     * @return 图片地址
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/uploadSteam")
    public BaseResponse<String> testUploadSteam(@RequestPart("file") MultipartFile multipartFile) throws Exception {
        // 文件目录
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("/test/%s", filename);
        // 获取流式对象
        InputStream inputStream = multipartFile.getInputStream();
        cosManager.putObjectStream(filepath, inputStream);
        // 返回可访问地址
        return ResultUtils.success(filepath);
    }

    /**
     * 测试文件下载
     *
     * @param filepath 文件路径
     * @param response 响应对象
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download/")
    public void testDownloadFile(String filepath, HttpServletResponse response) throws IOException {
        COSObjectInputStream cosObjectInput = null;
        try {
            COSObject cosObject = cosManager.getObject(filepath);
            cosObjectInput = cosObject.getObjectContent();
            // 处理下载到的流
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);
            // 写入响应
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filepath = {}", filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }
    }


}
