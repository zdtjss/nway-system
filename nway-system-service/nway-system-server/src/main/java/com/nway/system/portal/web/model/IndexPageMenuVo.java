package com.nway.system.portal.web.model;

import lombok.Data;

import java.util.List;

@Data
public class IndexPageMenuVo {

    private FavMenuViewVo index;

    private List<FavMenuViewVo> favorite;

}
