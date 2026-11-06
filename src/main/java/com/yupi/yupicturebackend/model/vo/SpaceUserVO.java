package com.yupi.yupicturebackend.model.vo;

import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间成员视图（脱敏，带成员的用户信息与是否为创建者标记）
 */
@Data
public class SpaceUserVO implements Serializable {

    /**
     * 成员关系 id
     */
    private Long id;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 成员的用户 id
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    /**
     * 空间角色中文名，前端直接展示用
     */
    private String spaceRoleText;

    /**
     * 是否为空间创建者。创建者不可被移出、不可被降级，前端据此禁用那两个操作
     */
    private Boolean creator;

    /**
     * 加入时间
     */
    private Date createTime;

    /**
     * 成员的用户信息（脱敏）
     */
    private UserVO user;

    /**
     * 该成员在本空间的图片数（列表接口实时聚合，非持久化字段）
     */
    private Long pictureCount;

    private static final long serialVersionUID = 1L;

    public static SpaceUserVO objToVo(SpaceUser spaceUser) {
        if (spaceUser == null) {
            return null;
        }
        SpaceUserVO spaceUserVO = new SpaceUserVO();
        BeanUtils.copyProperties(spaceUser, spaceUserVO);
        spaceUserVO.setSpaceRoleText(SpaceRoleEnum.getTextByValue(spaceUser.getSpaceRole()));
        return spaceUserVO;
    }
}
