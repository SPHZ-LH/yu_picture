package com.yupi.yupicturebackend.api.imagesearch.baidu;

import com.yupi.yupicturebackend.api.imagesearch.baidu.model.ImageSearchResult;
import com.yupi.yupicturebackend.api.imagesearch.baidu.sub.GetImageFirstUrlApi;
import com.yupi.yupicturebackend.api.imagesearch.baidu.sub.GetImageListApi;
import com.yupi.yupicturebackend.api.imagesearch.baidu.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-11
 * @description 以图搜图（门面类）
 */
@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     *
     * @param imageUrl 图片URL
     * @return 图片搜索集
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        return GetImageListApi.getImageList(imageFirstUrl);
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://www.codefather.cn/logo.png";
        List<ImageSearchResult> resultList = searchImage(imageUrl);
        System.out.println("结果列表" + resultList);
    }
}

