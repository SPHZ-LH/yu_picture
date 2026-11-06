package com.yupi.yupicturebackend.service.impl;

import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 图片可见性规则（checkPictureReadable）的分支覆盖。
 * 这条规则是下载与收藏两条链路的闸门，分支回归的代价高，值得用单测钉住。
 */
@ExtendWith(MockitoExtension.class)
class PictureReadableCheckTest {

    @Mock
    private SpaceService spaceService;

    @Mock
    private SpaceUserService spaceUserService;

    @InjectMocks
    private PictureServiceImpl pictureService;

    private Picture publicPicture(Integer reviewStatus) {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setSpaceId(null);
        picture.setReviewStatus(reviewStatus);
        return picture;
    }

    private Picture spacePicture(Long spaceId) {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setSpaceId(spaceId);
        return picture;
    }

    private User loginUser() {
        User user = new User();
        user.setId(100L);
        return user;
    }

    @Test
    void nullPictureIsNotFound() {
        assertThrows(BusinessException.class, () -> pictureService.checkPictureReadable(null, loginUser()));
    }

    @Test
    void approvedPublicPictureIsReadable() {
        Picture picture = publicPicture(PictureReviewStatusEnum.PASS.getValue());
        assertDoesNotThrow(() -> pictureService.checkPictureReadable(picture, loginUser()));
    }

    @Test
    void pendingAndRejectedPublicPicturesAreInvisible() {
        // 未过审统一按不存在返回，避免泄露审核中图片的存在性
        assertThrows(BusinessException.class,
                () -> pictureService.checkPictureReadable(publicPicture(PictureReviewStatusEnum.REVIEWING.getValue()), loginUser()));
        assertThrows(BusinessException.class,
                () -> pictureService.checkPictureReadable(publicPicture(PictureReviewStatusEnum.REJECT.getValue()), loginUser()));
        assertThrows(BusinessException.class,
                () -> pictureService.checkPictureReadable(publicPicture(null), loginUser()));
    }

    @Test
    void spacePictureOfMissingSpaceIsNotFound() {
        when(spaceService.getById(9L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> pictureService.checkPictureReadable(spacePicture(9L), loginUser()));
    }

    @Test
    void spacePictureReadableWhenPermissionCheckPasses() {
        when(spaceService.getById(9L)).thenReturn(new Space());
        // checkPermission 是 void：mock 默认不抛异常，即视为成员通过
        assertDoesNotThrow(() -> pictureService.checkPictureReadable(spacePicture(9L), loginUser()));
    }

    @Test
    void spacePictureRejectionPropagatesFromPermissionCheck() {
        when(spaceService.getById(9L)).thenReturn(new Space());
        doThrow(new BusinessException(com.yupi.yupicturebackend.exception.ErrorCode.NOT_FOUND_ERROR, "无权限查看该空间的图片"))
                .when(spaceUserService).checkPermission(any(Space.class), any(User.class), any(), anyString());
        assertThrows(BusinessException.class,
                () -> pictureService.checkPictureReadable(spacePicture(9L), loginUser()));
    }
}
