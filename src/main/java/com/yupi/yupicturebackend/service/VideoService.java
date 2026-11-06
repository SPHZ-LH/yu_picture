package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.video.VideoQueryRequest;
import com.yupi.yupicturebackend.model.dto.video.VideoReviewRequest;
import com.yupi.yupicturebackend.model.dto.video.VideoUploadRequest;
import com.yupi.yupicturebackend.model.entity.Video;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.VideoVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author SPHZ
 * @description 针对表【video(视频/动态壁纸)】的数据库操作Service
 */
public interface VideoService extends IService<Video> {

    /**
     * 上传动态壁纸：视频与前端抽好的封面在同一次请求内提交，先传封面再传视频，
     * 宽高以数据万象对封面的实测值为准（见 docs/adr/0011）。
     *
     * @param videoFile         视频文件（mp4）
     * @param coverFile         封面文件（jpg）
     * @param videoUploadRequest 上传请求（名称、分类、标签、时长、目标空间）
     * @param loginUser         登入用户
     * @return 视频视图
     */
    VideoVO uploadVideo(MultipartFile videoFile, MultipartFile coverFile,
                        VideoUploadRequest videoUploadRequest, User loginUser);

    /**
     * 删除视频并同步扣减空间用量（逻辑删除，对象存储文件保留）
     *
     * @param oldVideo 待删除视频
     * @return 是否删除成功
     */
    boolean deleteVideo(Video oldVideo);

    /**
     * 下载视频：校验可见性、下载量原子自增，返回带文件名的限时下载链接。
     *
     * @param videoId   视频 id
     * @param loginUser 登录用户，可为 null（未登录仅可下载公共图库过审视频）
     * @return 预签名下载地址
     */
    String downloadVideo(Long videoId, User loginUser);

    /**
     * 校验当前用户对视频的可读性（与图片一致）：
     * 公共图库视频须已过审，未过审统一按不存在返回；空间视频仅空间成员或站点管理员。
     *
     * @param video     视频（为 null 时抛出未找到）
     * @param loginUser 登录用户，可为 null
     */
    void checkVideoReadable(Video video, User loginUser);

    /**
     * 当前用户是否有权编辑/删除该视频：上传者本人、站点管理员，或所在空间的管理员。
     * 与图片同一条规则，见 {@link PictureService#hasEditPermission}。
     */
    boolean hasEditPermission(Video video, User loginUser);

    /**
     * 获取查询条件
     */
    LambdaQueryWrapper<Video> getQueryWrapper(VideoQueryRequest videoQueryRequest);

    /**
     * 根据视频获取视频视图（含作者信息）
     */
    VideoVO getVideoVO(Video video);

    /**
     * 根据视频分页获取视频视图分页（作者信息一次批量查出）
     */
    Page<VideoVO> getVideoVOPage(Page<Video> videoPage);

    /**
     * 视频实体基础校验
     */
    void validVideo(Video video);

    /**
     * 视频审核（管理员，人工审核）
     */
    void doVideoReview(VideoReviewRequest videoReviewRequest, User loginUser);

    /**
     * 根据权限填充视频审核参数：管理员自动过审，其余转入待审核
     */
    void fillReviewParams(Video video, User loginUser);
}
