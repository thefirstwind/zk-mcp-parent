package com.zkinfo.demo.service.impl;

import com.zkinfo.demo.model.Product;
import com.zkinfo.demo.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 产品服务实现类
 */
@Slf4j
@DubboService(version = "1.0.0", group = "demo")
public class ProductServiceImpl implements ProductService {
    
    private final Map<Long, Product> productStorage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    public ProductServiceImpl() {
        // 初始化一些测试数据
        initTestData();
    }
    
    private void initTestData() {
        Product product1 = new Product(1L, "iPhone 15", "最新款iPhone手机", "手机数码", 
                                     7999.0, 100, "/images/iphone15.jpg", "Apple", 
                                     "ACTIVE", 1500, 4.8, LocalDateTime.now(), LocalDateTime.now());
        
        Product product2 = new Product(2L, "AirPods Pro", "苹果无线耳机", "手机数码", 
                                     1999.0, 50, "/images/airpods.jpg", "Apple", 
                                     "ACTIVE", 800, 4.7, LocalDateTime.now(), LocalDateTime.now());
        
        Product product3 = new Product(3L, "MacBook Pro", "苹果笔记本电脑", "电脑办公", 
                                     15999.0, 20, "/images/macbook.jpg", "Apple", 
                                     "ACTIVE", 300, 4.9, LocalDateTime.now(), LocalDateTime.now());
        
        Product product4 = new Product(4L, "小米13", "小米旗舰手机", "手机数码", 
                                     3999.0, 80, "/images/mi13.jpg", "小米", 
                                     "ACTIVE", 2000, 4.6, LocalDateTime.now(), LocalDateTime.now());
        
        Product product5 = new Product(5L, "华为MateBook", "华为笔记本电脑", "电脑办公", 
                                     6999.0, 0, "/images/matebook.jpg", "华为", 
                                     "OUT_OF_STOCK", 150, 4.5, LocalDateTime.now(), LocalDateTime.now());
        
        productStorage.put(1L, product1);
        productStorage.put(2L, product2);
        productStorage.put(3L, product3);
        productStorage.put(4L, product4);
        productStorage.put(5L, product5);
        idGenerator.set(6L);
    }
    
    @Override
    public Product getProductById(Long productId) {
        log.info("Getting product by id: {}", productId);
        Product product = productStorage.get(productId);
        if (product == null) {
            log.warn("Product not found with id: {}", productId);
        }
        return product;
    }
    
    @Override
    public List<Product> getProductsByCategory(String category) {
        log.info("Getting products by category: {}", category);
        List<Product> products = productStorage.values().stream()
                .filter(product -> product.getCategory().equals(category))
                .collect(Collectors.toList());
        log.info("Found {} products in category: {}", products.size(), category);
        return products;
    }
    
    @Override
    public List<Product> searchProducts(String keyword) {
        log.info("Searching products with keyword: {}", keyword);
        List<Product> products = productStorage.values().stream()
                .filter(product -> 
                    product.getName().toLowerCase().contains(keyword.toLowerCase()) ||
                    product.getDescription().toLowerCase().contains(keyword.toLowerCase()) ||
                    product.getBrand().toLowerCase().contains(keyword.toLowerCase())
                )
                .collect(Collectors.toList());
        log.info("Found {} products matching keyword: {}", products.size(), keyword);
        return products;
    }
    
    @Override
    public List<Product> getPopularProducts(int limit) {
        log.info("Getting popular products, limit: {}", limit);
        List<Product> products = productStorage.values().stream()
                .filter(product -> "ACTIVE".equals(product.getStatus()))
                .sorted((p1, p2) -> Integer.compare(p2.getSalesCount(), p1.getSalesCount()))
                .limit(limit)
                .collect(Collectors.toList());
        log.info("Found {} popular products", products.size());
        return products;
    }
    
    @Override
    public boolean updateStock(Long productId, int stock) {
        log.info("Updating stock for product: {} to {}", productId, stock);
        Product product = productStorage.get(productId);
        if (product == null) {
            log.warn("Product not found for stock update: {}", productId);
            return false;
        }
        
        product.setStock(stock);
        product.setUpdateTime(LocalDateTime.now());
        
        // 根据库存更新状态
        if (stock > 0 && "OUT_OF_STOCK".equals(product.getStatus())) {
            product.setStatus("ACTIVE");
        } else if (stock == 0 && "ACTIVE".equals(product.getStatus())) {
            product.setStatus("OUT_OF_STOCK");
        }
        
        productStorage.put(productId, product);
        log.info("Stock updated successfully for product: {} to {}", productId, stock);
        return true;
    }
    
    @Override
    public Double getProductPrice(Long productId) {
        log.info("Getting price for product: {}", productId);
        Product product = productStorage.get(productId);
        if (product == null) {
            log.warn("Product not found for price query: {}", productId);
            return null;
        }
        
        Double price = product.getPrice();
        log.info("Product price: {} = {}", productId, price);
        return price;
    }
}





