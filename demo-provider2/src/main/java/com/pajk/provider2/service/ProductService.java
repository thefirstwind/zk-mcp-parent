package com.pajk.provider2.service;

import com.pajk.provider2.model.Product;
import java.util.List;

/**
 * 产品服务接口
 */
public interface ProductService {
    
    /**
     * 根据产品ID获取产品信息
     * @param productId 产品ID
     * @return 产品信息
     */
    Product getProductById(Long productId);
    
    /**
     * 根据分类获取产品列表
     * @param category 产品分类
     * @return 产品列表
     */
    List<Product> getProductsByCategory(String category);
    
    /**
     * 搜索产品
     * @param keyword 关键词
     * @return 产品列表
     */
    List<Product> searchProducts(String keyword);
    
    /**
     * 获取热门产品
     * @param limit 数量限制
     * @return 热门产品列表
     */
    List<Product> getPopularProducts(int limit);
    
    /**
     * 更新产品库存
     * @param productId 产品ID
     * @param stock 库存数量
     * @return 是否更新成功
     */
    boolean updateStock(Long productId, int stock);
    
    /**
     * 获取产品价格
     * @param productId 产品ID
     * @return 产品价格
     */
    Double getProductPrice(Long productId);
}

