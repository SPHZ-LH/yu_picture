package com.yupi.yupicturebackend.config;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.tiia.v20190529.TiiaClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 相似图搜索（腾讯云图像搜索，TIIA 图库接口）配置，决策见 docs/adr/0008。
 * secretId/secretKey 复用 COS 的账号级密钥（同一腾讯云账号凭证）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "image-search")
public class ImageSearchConfig {

    /**
     * 服务区域
     */
    private String region = "ap-guangzhou";

    /**
     * 图库 ID（仅支持字母、数字和下划线），创建后不可修改
     */
    private String groupId = "yu_picture_gallery";

    /**
     * 图库名称描述
     */
    private String groupName = "云图库相似图库";

    /**
     * 图库最大图片特征条数（一张图对应一条），创建后不支持修改
     */
    private Long maxCapacity = 10000L;

    @Bean
    public TiiaClient tiiaClient(CosClientConfig cosClientConfig) {
        Credential cred = new Credential(cosClientConfig.getSecretId(), cosClientConfig.getSecretKey());
        return new TiiaClient(cred, region);
    }
}
