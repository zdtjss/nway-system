package com.nway.system.common.util;

import com.nway.system.common.web.PageVo;
import org.springframework.beans.BeanUtils;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConverterUtil {

    public static <T, S> T convert(S obj, Class<T> clazz, String... ignoreProperties) {
        T newInstance = BeanUtils.instantiateClass(clazz);
        BeanUtils.copyProperties(obj, newInstance, ignoreProperties);
        return newInstance;
    }

    public static <T, S> T convert(S obj, Class<T> clazz, Customer<T, S> cust, String... ignoreProperties) {
        T newInstance = BeanUtils.instantiateClass(clazz);
        BeanUtils.copyProperties(obj, newInstance, ignoreProperties);
        cust.accept(obj, newInstance);
        return newInstance;
    }

    public static <T, S> List<T> convert(List<S> obj, Class<T> clazz, String... ignoreProperties) {
        if (obj == null || obj.isEmpty()) {
            return Collections.emptyList();
        }
        return obj.stream().map(o -> convert(o, clazz, ignoreProperties)).collect(Collectors.toList());
    }

    public static <T, S> List<T> convert2(List<S> obj, Class<T> clazz, Customer<T, S> cust, String... ignoreProperties) {
        if (obj == null || obj.isEmpty()) {
            return Collections.emptyList();
        }
        return obj.stream().map(o -> convert(o, clazz, cust, ignoreProperties)).collect(Collectors.toList());
    }

    public static <T, S> PageVo<T> convert(com.nway.spring.jdbc.pagination.Page<S> obj, Class<T> clazz, String... ignoreProperties) {
        PageVo<T> page = new PageVo<>();
        page.setTotal(obj.getTotal());
        page.setData(convert(obj.getPageData(), clazz, ignoreProperties));
        return page;
    }

    public static <T, S> PageVo<T> convert2(com.nway.spring.jdbc.pagination.Page<S> obj, Class<T> clazz, Customer<T, S> cust, String... ignoreProperties) {
        PageVo<T> page = new PageVo<>();
        page.setTotal(obj.getTotal());
        page.setData(convert2(obj.getPageData(), clazz, cust, ignoreProperties));
        return page;
    }

    public static <T, E> List<T> extract(List<E> items, Function<E, T> extractor) {
        return items.stream().map(extractor).collect(Collectors.toList());
    }

    public interface Customer<T, S> {
        void accept(S source, T target);
    }
}
