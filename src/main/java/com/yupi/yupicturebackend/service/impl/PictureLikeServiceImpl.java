package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.PictureLikeMapper;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.PictureLike;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.service.PictureLikeService;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 针对表【picture_like(图片点赞关系)】的数据库操作Service实现
 * @createDate 2026-09-27
 */
@Slf4j
@Service
public class PictureLikeServiceImpl extends ServiceImpl<PictureLikeMapper, PictureLike>
        implements PictureLikeService {

    /** 点赞列表每页上限：与收藏列表保持一致 */
    private static final int MAX_PAGE_SIZE = 20;

    /** 批量查点赞状态的单次 id 上限：与收藏批量口径一致 */
    private static final int MAX_BATCH_SIZE = 30;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Override
    public boolean doLike(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        Picture picture = pictureService.getById(pictureId);
        pictureService.checkPictureReadable(picture, loginUser);
        PictureLike like = new PictureLike();
        like.setUserId(loginUser.getId());
        like.setPictureId(pictureId);
        try {
            this.save(like);
        } catch (DuplicateKeyException e) {
            // 并发重复点赞被 uk_userId_pictureId 拦下，按已点赞幂等处理
        }
        return true;
    }

    @Override
    public boolean cancelLike(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        this.remove(new LambdaQueryWrapper<PictureLike>()
                .eq(PictureLike::getUserId, loginUser.getId())
                .eq(PictureLike::getPictureId, pictureId));
        return true;
    }

    @Override
    public List<Long> listLikedPictureIds(List<Long> pictureIds, User loginUser) {
        // 未登录当作「一张都没点赞」：红心是列表的附属信息，返回空集合比抛异常更合适
        if (loginUser == null || CollUtil.isEmpty(pictureIds)) {
            return new ArrayList<>();
        }
        List<Long> validIds = pictureIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (validIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 超限直接报错而不是截断：截断会让部分图块的红心静默显示成「未点赞」
        ThrowUtils.throwIf(validIds.size() > MAX_BATCH_SIZE, ErrorCode.PARAMS_ERROR,
                "一次最多查询 " + MAX_BATCH_SIZE + " 张图片的点赞状态");
        return this.list(new LambdaQueryWrapper<PictureLike>()
                        .select(PictureLike::getPictureId)
                        .eq(PictureLike::getUserId, loginUser.getId())
                        .in(PictureLike::getPictureId, validIds))
                .stream()
                .map(PictureLike::getPictureId)
                .collect(Collectors.toList());
    }

    @Override
    public Page<PictureVO> listMyLikedPictures(PageRequest pageRequest, User loginUser) {
        ThrowUtils.throwIf(pageRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        long current = Math.max(pageRequest.getCurrent(), 1);
        long pageSize = Math.min(Math.max(pageRequest.getPageSize(), 1), MAX_PAGE_SIZE);
        Page<Picture> picturePage = new Page<>(current, pageSize);
        baseMapper.selectLikedPicturePage(picturePage, loginUser.getId(), userService.isAdmin(loginUser));
        return pictureService.getPictureVOPage(picturePage);
    }

    @Override
    public long getReceivedLikeCount(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
        return baseMapper.selectReceivedLikeCount(userId);
    }
}
