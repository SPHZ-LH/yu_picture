package com.yupi.yupicturebackend.api.aliyunai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class ImageOutPaintingRequest implements Serializable {
    private String model;

    private Input input;

    private Parameters parameters;

    @Data
    public static class Input implements Serializable {
        @JsonProperty("image_url")
        private String imageUrl;
    }

    @Data
    public static class Parameters implements Serializable {
        private Integer angle;

        @JsonProperty("output_ratio")
        private String outputRatio;

        @JsonProperty("x_scale")
        private Float xScale;

        @JsonProperty("y_scale")
        private Float yScale;

        @JsonProperty("top_offset")
        private Integer topOffset;

        @JsonProperty("bottom_offset")
        private Integer bottomOffset;

        @JsonProperty("left_offset")
        private Integer leftOffset;

        @JsonProperty("right_offset")
        private Integer rightOffset;

        @JsonProperty("best_quality")
        private Boolean bestQuality;

        @JsonProperty("limit_image_size")
        private Boolean limitImageSize;

        @JsonProperty("add_watermark")
        private Boolean addWatermark;
    }
}
