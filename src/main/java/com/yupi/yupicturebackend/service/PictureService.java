package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.model.dto.picture.PictureExpandSaveRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureExpandSubmitRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureReviewRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureTransformRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureAiPrefillVO;
import com.yupi.yupicturebackend.model.vo.PictureDownloadTierVO;
import com.yupi.yupicturebackend.model.vo.PictureExpandResultVO;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author SPHZ
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-12-03 00:51:07
 */
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     * request 请求为 null 则为上传，否则为修改
     *
     * @param inputSoures          输入源
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            登入用户
     * @return 图片视图
     */
    PictureVO uploadPicture(Object inputSoures, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 批量抓取图片请求
     * @param loginUser                   登入用户
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    /**
     * 删除图片并同步扣减空间用量（逻辑删除）
     *
     * @param oldPicture 待删除图片
     * @return 是否删除成功
     */
    boolean deletePicture(Picture oldPicture);

    /**
     * 下载图片：校验可见性、下载量原子自增并失效图片缓存，返回图片地址供 302 重定向。
     * <p>
     * 下载量不去重：每次触发下载 +1。tier 为空下载原图；指定档位（8k/4k/2k/1080p）
     * 且原图大于该档时，返回按宽度等比缩小的 COS 衍生地址（imageMogr2 实时处理，
     * 见 docs/adr/0017）；非本 COS 域名或档位不合法时回退原图。
     *
     * @param pictureId 图片 id
     * @param loginUser 登录用户，可为 null（未登录仅可下载公共图库过审图片）
     * @param tier      分辨率档位，可空（原图）
     * @return 图片地址（原图或衍生档）
     */
    String downloadPicture(Long pictureId, User loginUser, String tier);

    /**
     * 图片可用的下载档位列表（详情页「选择分辨率下载」）：
     * 只含严格小于原图的档位（原图即最大档），按 8K→4K→2K→1080P 排列。
     *
     * @param pictureId 图片 id
     * @return 档位与估算尺寸列表
     */
    List<PictureDownloadTierVO> listDownloadTiers(Long pictureId);

    /**
     * 设置/取消编辑精选（管理员，逐张设置不互斥）
     *
     * @param pictureId  图片 id
     * @param isFeatured 是否精选
     * @return 是否成功
     */
    boolean setFeatured(Long pictureId, boolean isFeatured);

    /**
     * 设置今日一图（管理员，全站至多一张，设新图自动取代旧图）
     *
     * @param pictureId 图片 id
     * @return 是否成功
     */
    boolean setDailyPick(Long pictureId);

    /**
     * 今日一图（公开）：公共图库过审口径，未设置时返回 null
     *
     * @return 图片视图
     */
    PictureVO getDailyPickPicture();

    /**
     * 提交草稿进入审核流（作者本人或管理员）：DRAFT → REVIEWING（管理员直接 PASS）。
     *
     * @param pictureId 图片 id
     * @param loginUser 登录用户
     * @return 是否成功
     */
    boolean submitDraft(Long pictureId, User loginUser);

    /**
     * 为缺少色系的图片回填主色/色系（管理员运维接口），返回成功回填的数量
     */
    int backfillColorInfo();

    /**
     * 收藏量原子增减并失效图片缓存（collect 收藏关系表变更后调用）。
     *
     * @param pictureId 图片 id
     * @param delta     增量：+1 收藏 / -1 取消收藏
     */
    void adjustCollectCount(Long pictureId, int delta);

    /**
     * 校验当前用户对图片的可读性（与列表页可见性规则一致）：
     * 公共图库图片须已过审；空间图片仅空间成员或站点管理员；未登录仅可读公共图库过审图片。
     *
     * @param picture   图片（为 null 时抛出未找到）
     * @param loginUser 登录用户，可为 null
     */
    void checkPictureReadable(Picture picture, User loginUser);

    /**
     * 当前用户是否有权编辑/删除该图片：上传者本人、站点管理员，或所在空间的管理员。
     * 编辑者只能动自己上传的，已由「上传者本人」这一条覆盖。
     * <p>
     * 返回布尔而不抛异常，是因为删除接口对无权者统一回「不存在」以免泄露图片存在性，
     * 与编辑接口的「无权限」不是同一个错误码。
     */
    boolean hasEditPermission(Picture picture, User loginUser);

    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest 查询请求
     * @return 查询条件
     */
    LambdaQueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 根据图片获取图片视图
     *
     * @param picture 图片
     * @return 图片视图
     */
    PictureVO getPictureVO(Picture picture);

    /**
     * 根据 id 获取图片视图，走多级缓存。
     * <p>
     * 读路径：L1（本地 Caffeine）-> L2（Redis）-> 数据库，逐层回源并逐层回填。
     * <p>
     * 空间图片要求调用者是成员，因此必须带上登录用户（可为 null，表示匿名）。
     * 不提供无 loginUser 的重载：留一个不鉴权的入口，迟早有人拿去接新接口。
     *
     * @param id        图片 id
     * @param loginUser 登录用户，可为 null
     * @return 图片视图；不存在时返回 null
     */
    PictureVO getPictureVOById(Long id, User loginUser);

    /**
     * 根据图片视图获取图片
     *
     * @param pictureVO 图片视图
     * @return 图片
     */
    Picture getPicture(PictureVO pictureVO);

    /**
     * 根据图片分页获取图片视图分页
     *
     * @param picturePage 图片分页
     * @return 图片视图分页
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage);

    /**
     * 图片校验
     *
     * @param picture 图片
     */
    void validPicture(Picture picture);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            登入用户
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 根据权限填充图片审核参数
     *
     * @param picture   图片
     * @param loginUser 登入用户
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 以图搜图：以本地图片文件、远程图片地址或库内图片 id 为检索输入，返回按相似度排序的图片视图
     *
     * @param file      本地图片文件（三选一）
     * @param pictureId 库内图片 id（三选一）
     * @param imageUrl  图片的网络地址（三选一），由服务端抓取，仅支持公网 http/https
     * @param limit     返回条数上限
     * @return 按相似度降序排列的图片视图
     */
    List<PictureVO> searchPictureByImage(MultipartFile file, Long pictureId, String imageUrl, long limit);

    /**
     * 全量同步图片到以图搜图图库（管理员）：只有公共图库且已过审的图片会入库，
     * 其余图片会被移出图库。首次调用会尝试开通图库（已开通则静默跳过）。
     *
     * @return 成功入库的图片数
     */
    int syncAllPicturesToGallery();

    /**
     * 按图库可见性规则同步单张图片（公共图库且已过审才入库，否则移出）。
     * 供审核、编辑、管理端更新等改变图片可见性的操作在落库后调用。
     *
     * @param pictureId 图片 id
     */
    void syncPictureGalleryById(Long pictureId);

    /**
     * 画面编辑（裁剪/等比缩放/90°旋转/翻转）：像素处理走数据万象云上处理，
     * 产物另存为新对象并原地更新图片记录（旧文件保留作回滚，见 ADR 0001）。
     * <p>
     * 审核规则沿用现状：空间图片免审保持可见；公共图库图片按用户角色回填审核状态
     * （管理员自动过审，普通用户重新进入待审核）。
     *
     * @param pictureTransformRequest 编辑参数（裁剪坐标基于旋转+翻转后的预览图）
     * @param loginUser               登入用户
     * @return 更新后的图片视图
     */
    PictureVO transformPicture(PictureTransformRequest pictureTransformRequest, User loginUser);

    /**
     * 提交画面扩展任务（百炼异步；后端无状态代理，见 docs/adr/0010）
     *
     * @param submitRequest 图片 id 与目标宽高比
     * @param loginUser     登入用户（须为作者 / 空间所有者 / 管理员）
     * @return 任务 id
     */
    String submitExpand(PictureExpandSubmitRequest submitRequest, User loginUser);

    /**
     * 查询画面扩展任务状态
     *
     * @param taskId 任务 id
     * @return 任务状态与结果图地址
     */
    PictureExpandResultVO queryExpandResult(String taskId);

    /**
     * 保存画面扩展结果：后端重查任务取结果图，下载写入 COS 新对象并原地更新记录，
     * 语义与编辑保存一致（旧对象保留、公共图重审）。
     *
     * @param saveRequest 图片 id 与任务 id（不接受客户端传入结果 URL）
     * @param loginUser   登入用户（须为作者 / 空间所有者 / 管理员）
     * @return 更新后的图片视图
     */
    PictureVO saveExpand(PictureExpandSaveRequest saveRequest, User loginUser);

    /**
     * AI 读图产出图片信息预填（名称/简介/分类/标签）。
     * <p>
     * **只产出建议、不落库**：前端填进表单，人确认后仍走编辑保存（docs/adr/0012）。
     * 读不出可用内容时返回 null，调用方按"没填"处理，不视为错误。
     *
     * @param pictureId 图片 id
     * @param loginUser 登入用户（须为作者 / 空间所有者 / 管理员）
     * @return 预填建议，或 null
     */
    PictureAiPrefillVO aiPrefillPictureInfo(Long pictureId, User loginUser);
}
