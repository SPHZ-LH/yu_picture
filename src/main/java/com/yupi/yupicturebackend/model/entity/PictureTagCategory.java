package com.yupi.yupicturebackend.model.entity;

import lombok.Data;

import java.util.List;

@Data
public class PictureTagCategory {
    private List<String> tagList;
    List<String> categoryList;
}
