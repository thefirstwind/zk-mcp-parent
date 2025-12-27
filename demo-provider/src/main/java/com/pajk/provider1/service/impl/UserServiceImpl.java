package com.pajk.provider1.service.impl;

import com.pajk.provider1.model.User;
import com.pajk.provider1.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户服务实现类
 */
@Slf4j
@DubboService(version = "1.0.0", group = "demo", interfaceClass = UserService.class)
public class UserServiceImpl implements UserService {
    
    private final Map<Long, User> userStorage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    public UserServiceImpl() {
        // 初始化一些测试数据
        initTestData();
    }
    
    private void initTestData() {
        User user1 = new User(1L, "alice", "alice@example.com", "13800138001", 
                             "Alice Wang", 25, "F", "ACTIVE", 
                             LocalDateTime.now(), LocalDateTime.now());
        User user2 = new User(2L, "bob", "bob@example.com", "13800138002", 
                             "Bob Chen", 30, "M", "ACTIVE", 
                             LocalDateTime.now(), LocalDateTime.now());
        User user3 = new User(3L, "charlie", "charlie@example.com", "13800138003", 
                             "Charlie Li", 28, "M", "INACTIVE", 
                             LocalDateTime.now(), LocalDateTime.now());
        
        userStorage.put(1L, user1);
        userStorage.put(2L, user2);
        userStorage.put(3L, user3);
        idGenerator.set(4L);
    }
    
    public User getUserById(Long userId) {
        log.info("Getting user by id: {}", userId);
        User user = userStorage.get(userId);
        if (user == null) {
            log.warn("User not found with id: {}", userId);
        }
        return user;
    }
    
    public List<User> getAllUsers() {
        log.info("Getting all users, total count: {}", userStorage.size());
        return new ArrayList<>(userStorage.values());
    }
    
    public User createUser(User user) {
        log.info("Creating new user: {}", user.getUsername());
        user.setId(idGenerator.getAndIncrement());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setStatus("ACTIVE");
        
        userStorage.put(user.getId(), user);
        log.info("User created successfully with id: {}", user.getId());
        return user;
    }
    
    public User updateUser(User user) {
        log.info("Updating user: {}", user.getId());
        User existingUser = userStorage.get(user.getId());
        if (existingUser == null) {
            log.warn("User not found for update: {}", user.getId());
            return null;
        }
        
        user.setUpdateTime(LocalDateTime.now());
        user.setCreateTime(existingUser.getCreateTime()); // 保持原创建时间
        userStorage.put(user.getId(), user);
        log.info("User updated successfully: {}", user.getId());
        return user;
    }
    
    public boolean deleteUser(Long userId) {
        log.info("Deleting user: {}", userId);
        User removedUser = userStorage.remove(userId);
        boolean success = removedUser != null;
        if (success) {
            log.info("User deleted successfully: {}", userId);
        } else {
            log.warn("User not found for deletion: {}", userId);
        }
        return success;
    }
}





