package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.CollectMapper;
import com.yupi.yupicturebackend.model.dto.picture.PicturePublicCollectRequest;
import com.yupi.yupicturebackend.model.entity.Collect;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureCollectStatusVO;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.service.CollectService;
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
 * @description 针对表【collect(用户收藏图片关系)】的数据库操作Service实现
 * @createDate 2026-09-13
 */
@Slf4j
@Service
public class CollectServiceImpl extends ServiceImpl<CollectMapper, Collect> implements CollectService {

    /** 收藏列表每页上限：与 /picture/list/page/vo 保持一致，压住单次查询的量 */
    private static final int MAX_PAGE_SIZE = 20;

    /** 批量查收藏状态的单次 id 上限：列表页一页最多 20 张，超出按参数错误拒绝 */
    private static final int MAX_BATCH_SIZE = 30;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Override
    public PictureCollectStatusVO doPictureCollect(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        Picture picture = pictureService.getById(pictureId);
        pictureService.checkPictureReadable(picture, loginUser);
        // 幂等：已收藏直接返回当前状态
        if (this.hasCollect(loginUser.getId(), pictureId)) {
            return this.buildStatus(pictureId, true);
        }
        Collect collect = new Collect();
        collect.setUserId(loginUser.getId());
        collect.setPictureId(pictureId);
        try {
            this.save(collect);
        } catch (DuplicateKeyException e) {
            // 并发重复收藏被 uk_userId_pictureId 拦下，按已收藏幂等处理
            return this.buildStatus(pictureId, true);
        }
        pictureService.adjustCollectCount(pictureId, 1);
        return this.buildStatus(pictureId, true);
    }

    @Override
    public PictureCollectStatusVO cancelPictureCollect(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        Picture picture = pictureService.getById(pictureId);
        boolean removed = this.remove(new LambdaQueryWrapper<Collect>()
                .eq(Collect::getUserId, loginUser.getId())
                .eq(Collect::getPictureId, pictureId));
        // 图片已被删除时残留关系在此兜底清理，但不再回调整计数（计数随图片一起失效）
        if (removed && picture != null) {
            pictureService.adjustCollectCount(pictureId, -1);
        }
        return this.buildStatus(pictureId, false);
    }

    @Override
    public PictureCollectStatusVO getPictureCollectStatus(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        boolean collected = loginUser != null && this.hasCollect(loginUser.getId(), pictureId);
        return this.buildStatus(pictureId, collected);
    }

    @Override
    public Page<PictureVO> listMyCollectPictures(PageRequest pageRequest, User loginUser) {
        ThrowUtils.throwIf(pageRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        long current = Math.max(pageRequest.getCurrent(), 1);
        long pageSize = Math.min(Math.max(pageRequest.getPageSize(), 1), MAX_PAGE_SIZE);
        // 分页查询由 mapper 一次 JOIN 完成：按收藏时间排序，并把「对本人不可见」的图片排除在
        // 分页之外，避免页短于 pageSize、total 虚高
        Page<Picture> picturePage = new Page<>(current, pageSize);
        baseMapper.selectCollectedPicturePage(picturePage, loginUser.getId(), userService.isAdmin(loginUser));
        return pictureService.getPictureVOPage(picturePage);
    }

    @Override
    public List<Long> listCollectedPictureIds(List<Long> pictureIds, User loginUser) {
        // 未登录当作「一张都没收藏」：星标是列表的附属信息，返回空集合比抛异常更合适
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
        // 超限直接报错而不是截断：截断会让部分图块的星标静默显示成「未收藏」
        ThrowUtils.throwIf(validIds.size() > MAX_BATCH_SIZE, ErrorCode.PARAMS_ERROR,
                "一次最多查询 " + MAX_BATCH_SIZE + " 张图片的收藏状态");
        return this.list(new LambdaQueryWrapper<Collect>()
                        .select(Collect::getPictureId)
                        .eq(Collect::getUserId, loginUser.getId())
                        .in(Collect::getPictureId, validIds))
                .stream()
                .map(Collect::getPictureId)
                .collect(Collectors.toList());
    }

    @Override
    public Page<PictureVO> listPublicCollectPictures(PicturePublicCollectRequest publicCollectRequest) {
        ThrowUtils.throwIf(publicCollectRequest == null || publicCollectRequest.getUserId() == null
                || publicCollectRequest.getUserId() <= 0, ErrorCode.PARAMS_ERROR);
        long current = Math.max(publicCollectRequest.getCurrent(), 1);
        long pageSize = Math.min(Math.max(publicCollectRequest.getPageSize(), 1), MAX_PAGE_SIZE);
        User owner = userService.getById(publicCollectRequest.getUserId());
        ThrowUtils.throwIf(owner == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        // 开关决定一切：未公开返回空页而不是报错，前端按 UserVO.collectPublic 决定是否渲染 tab
        if (!Boolean.TRUE.equals(owner.getCollectPublic())) {
            return new Page<>(current, pageSize, 0);
        }
        Page<Picture> picturePage = new Page<>(current, pageSize);
        baseMapper.selectPublicCollectedPicturePage(picturePage, owner.getId());
        return pictureService.getPictureVOPage(picturePage);
    }

    private boolean hasCollect(Long userId, Long pictureId) {
        return this.count(new LambdaQueryWrapper<Collect>()
                .eq(Collect::getUserId, userId)
                .eq(Collect::getPictureId, pictureId)) > 0;
    }

    /**
     * 组装状态视图。收藏量从图片记录实时读取（getById 不走缓存，见 docs/adr/0002），
     * 图片已删除时按 0 返回。
     */
    private PictureCollectStatusVO buildStatus(Long pictureId, boolean collected) {
        PictureCollectStatusVO vo = new PictureCollectStatusVO();
        vo.setPictureId(pictureId);
        vo.setCollected(collected);
        Picture picture = pictureService.getById(pictureId);
        vo.setCollectCount(picture == null || picture.getCollectCount() == null ? 0L : picture.getCollectCount());
        return vo;
    }
}
