package com.yupi.yupicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.topic.TopicAddRequest;
import com.yupi.yupicturebackend.model.dto.topic.TopicPictureOpRequest;
import com.yupi.yupicturebackend.model.dto.topic.TopicUpdateRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.TopicVO;
import com.yupi.yupicturebackend.service.TopicService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 专题接口：公开浏览（列表/精选/详情/专题内图片）+ 管理端维护。
 * 专题由编辑维护（屿图「专题」板块），详见 docs/adr/0014。
 *
 * @author SPHZ
 */
@RestController
@RequestMapping("/topic")
public class TopicController {

    @Resource
    private TopicService topicService;

    @Resource
    private UserService userService;

    // ==================== 公开接口 ====================

    /**
     * 专题列表（含实时图片计数与策划人信息，精选在前）
     *
     * @param category 分类筛选，空表示全部
     */
    @GetMapping("/list/vo")
    public BaseResponse<List<TopicVO>> listTopicVOs(@RequestParam(required = false) String category) {
        return ResultUtils.success(topicService.listTopicVOs(category));
    }

    /**
     * 本周精选专题（全站至多一条，没有精选时 data 为 null）
     */
    @GetMapping("/featured/vo")
    public BaseResponse<TopicVO> getFeaturedTopicVO() {
        return ResultUtils.success(topicService.getFeaturedTopicVO());
    }

    /**
     * 专题详情
     *
     * @param topicId 专题 id
     */
    @GetMapping("/get/vo")
    public BaseResponse<TopicVO> getTopicVO(@RequestParam("topicId") long topicId) {
        return ResultUtils.success(topicService.getTopicVO(topicId));
    }

    /**
     * 专题内图片分页（只出公共图库过审图，按加入时间倒序）
     *
     * @param topicId  专题 id
     * @param current  页码
     * @param pageSize 每页数量
     */
    @GetMapping("/picture/page")
    public BaseResponse<Page<PictureVO>> listTopicPictures(@RequestParam("topicId") long topicId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long pageSize) {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setCurrent((int) current);
        pageRequest.setPageSize((int) pageSize);
        return ResultUtils.success(topicService.listTopicPictures(topicId, pageRequest));
    }

    // ==================== 管理端 ====================

    /**
     * 创建专题（管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addTopic(@RequestBody TopicAddRequest topicAddRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(topicService.addTopic(topicAddRequest, loginUser));
    }

    /**
     * 更新专题（管理员）：字段传了才更新；isFeatured=true 自动清掉其他精选位
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateTopic(@RequestBody TopicUpdateRequest topicUpdateRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(topicService.updateTopic(topicUpdateRequest, loginUser));
    }

    /**
     * 删除专题（管理员，逻辑删除）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteTopic(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(topicService.deleteTopic(deleteRequest.getId(), loginUser));
    }

    /**
     * 图片加入专题（管理员）：要求图片是公共图库过审图，重复加入幂等
     */
    @PostMapping("/picture/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> addTopicPicture(@RequestBody TopicPictureOpRequest opRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(topicService.addTopicPicture(opRequest, loginUser));
    }

    /**
     * 图片移出专题（管理员）：未在专题内时幂等
     */
    @PostMapping("/picture/remove")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> removeTopicPicture(@RequestBody TopicPictureOpRequest opRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(topicService.removeTopicPicture(opRequest, loginUser));
    }
}
