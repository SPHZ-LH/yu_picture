package com.yupi.yupicturebackend.mapper;

import com.yupi.yupicturebackend.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yupi.yupicturebackend.model.vo.CreatorVO;
import org.apache.ibatis.annotations.Param;

/**
* @author SPHZ
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2025-11-23 22:43:10
* @Entity com.yupi.yupicturebackend.model.entity.User
*/
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子调整积分：delta 为负时保证余额不透支（不满足条件返回 0 行）。
     * 绕过 CacheAwareServiceImpl 写钩子直接走原生 SQL——积分不进 UserVO 缓存，
     * 无需失效（同 downloadCount 的处理，见 docs/adr/0002）。
     *
     * @param userId 用户 id
     * @param delta  变动值（正得负耗）
     * @return 影响行数：0 表示余额不足（调整后为负）
     */
    int adjustPoints(@Param("userId") Long userId, @Param("delta") int delta);

    /**
     * 创作者目录分页：有公开图库过审作品的用户，按聚合指标排序（创作者页）。
     * 作品数/下载量实时聚合；粉丝数用子查询计数，支持「人气优先」排序。
     *
     * @param page     分页对象
     * @param sortItem 排序依据：popular-人气(粉丝数)/works-作品数/newest-最新加入，
     *                 空与非法值按 popular 处理（并列时加入时间新者在前）
     * @return 创作者聚合视图分页
     */
    IPage<CreatorVO> selectCreatorPage(IPage<CreatorVO> page, @Param("sortItem") String sortItem);
}




