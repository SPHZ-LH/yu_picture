package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.vo.SpaceRankAnalyzeResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author SPHZ
 * @description 针对表【space(空间)】的数据库操作Mapper
 * @Entity com.yupi.yupicturebackend.model.entity.Space
 */
public interface SpaceMapper extends BaseMapper<Space> {

    /**
     * 空间使用排行：按存储使用量取前 N 个空间
     *
     * @param topN 前 N 个
     * @return 排行列表
     */
    List<SpaceRankAnalyzeResponse> selectSpaceRankAnalyze(@Param("topN") int topN);
}
