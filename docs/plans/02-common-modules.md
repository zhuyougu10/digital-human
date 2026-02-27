# 02 - 公共模块实现

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 4 个公共模块的核心代码：统一响应体、全局异常处理、Sa-Token 鉴权配置、MyBatis-Plus 自动填充、Redis 工具类。

**Architecture:** 各公共模块以 jar 方式被业务服务依赖，通过 Spring Boot 自动配置机制生效。

**Tech Stack:** Spring Boot 3.3.x, Sa-Token, MyBatis-Plus, Redis, Lombok, MapStruct

**前置依赖:** `01-project-init.md` 完成

---

## Task 1: medical-common-core - 统一响应体 R

**Files:**
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/domain/R.java`

**Step 1: 实现统一响应体**

```java
package com.medical.common.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
public class R<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public static final int SUCCESS_CODE = 200;
    public static final int FAIL_CODE = 500;

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(SUCCESS_CODE, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS_CODE, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(SUCCESS_CODE, msg, data);
    }

    public static <T> R<T> fail() {
        return new R<>(FAIL_CODE, "操作失败", null);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(FAIL_CODE, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public boolean isSuccess() {
        return SUCCESS_CODE == this.code;
    }
}
```

---

## Task 2: medical-common-core - 业务异常类

**Files:**
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/exception/BusinessException.java`
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/exception/ErrorCode.java`

**Step 1: 错误码枚举**

```java
package com.medical.common.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),

    // 用户模块 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    USER_PASSWORD_ERROR(1003, "密码错误"),
    USER_DISABLED(1004, "账户已禁用"),
    WX_LOGIN_FAIL(1005, "微信登录失败"),

    // 医生模块 2xxx
    DOCTOR_NOT_FOUND(2001, "医生不存在"),
    DEPARTMENT_NOT_FOUND(2002, "科室不存在"),
    SCHEDULE_CONFLICT(2003, "排班冲突"),

    // AI 模块 3xxx
    AI_SERVICE_ERROR(3001, "AI服务异常"),
    AI_RATE_LIMIT(3002, "请求过于频繁，请稍后再试"),
    TTS_ERROR(3003, "语音合成失败"),

    // 预约模块 4xxx
    SLOT_NOT_AVAILABLE(4001, "号源不可用"),
    APPOINTMENT_NOT_FOUND(4002, "预约不存在"),
    APPOINTMENT_ALREADY_EXISTS(4003, "重复预约"),
    APPOINTMENT_CANCEL_FAIL(4004, "取消预约失败"),

    // 知识库模块 5xxx
    KNOWLEDGE_BASE_NOT_FOUND(5001, "知识库不存在"),
    DOCUMENT_PARSE_ERROR(5002, "文档解析失败"),
    EMBEDDING_ERROR(5003, "向量化处理失败");

    private final int code;
    private final String msg;
}
```

**Step 2: 业务异常类**

```java
package com.medical.common.core.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.FAIL.getCode();
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
```

---

## Task 3: medical-common-core - 全局异常处理器

**Files:**
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/handler/GlobalExceptionHandler.java`

**Step 1: 实现全局异常处理**

```java
package com.medical.common.core.handler;

import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} URI: {}", e.getMessage(), request.getRequestURI());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} URI: {}", e.getMessage(), request.getRequestURI(), e);
        return R.fail(ErrorCode.FAIL.getCode(), "系统内部错误");
    }
}
```

---

## Task 4: medical-common-core - 分页请求/响应

**Files:**
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/domain/PageQuery.java`
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/domain/PageResult.java`

**Step 1: 分页请求**

```java
package com.medical.common.core.domain;

import lombok.Data;

@Data
public class PageQuery {
    /** 页码，从1开始 */
    private Integer pageNum = 1;
    /** 每页数量 */
    private Integer pageSize = 10;
    /** 排序字段 */
    private String orderBy;
    /** 排序方向 asc/desc */
    private String orderDirection = "desc";
}
```

**Step 2: 分页响应**

```java
package com.medical.common.core.domain;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {
    /** 总记录数 */
    private long total;
    /** 当前页数据 */
    private List<T> records;
    /** 当前页码 */
    private int pageNum;
    /** 每页数量 */
    private int pageSize;

    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }
}
```

---

## Task 5: medical-common-core - 通用常量和工具

**Files:**
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/constant/Constants.java`
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/constant/UserConstants.java`

**Step 1: 通用常量**

```java
package com.medical.common.core.constant;

public class Constants {
    /** 成功标记 */
    public static final int SUCCESS = 200;
    /** 失败标记 */
    public static final int FAIL = 500;

    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 请求头中的用户ID */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** 请求头中的用户角色 */
    public static final String HEADER_USER_ROLE = "X-User-Role";
    /** 请求头中的用户名 */
    public static final String HEADER_USERNAME = "X-Username";
}
```

**Step 2: 用户常量**

```java
package com.medical.common.core.constant;

public class UserConstants {
    /** 角色：患者 */
    public static final String ROLE_PATIENT = "PATIENT";
    /** 角色：医生 */
    public static final String ROLE_DOCTOR = "DOCTOR";
    /** 角色：管理员 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 0;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 1;
}
```

---

## Task 6: medical-common-core - BaseEntity 基础实体

**Files:**
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/domain/BaseEntity.java`

**Step 1: 基础实体**

```java
package com.medical.common.core.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BaseEntity implements Serializable {

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
```

**Step 3: Commit**

```bash
git add .
git commit -m "feat(common-core): add R, BusinessException, GlobalExceptionHandler, PageQuery/Result, BaseEntity"
```

---

## Task 7: medical-common-mybatis - MyBatis-Plus 自动配置

**Files:**
- Create: `medical-ai/medical-common/medical-common-mybatis/src/main/java/com/medical/common/mybatis/config/MybatisPlusConfig.java`
- Create: `medical-ai/medical-common/medical-common-mybatis/src/main/java/com/medical/common/mybatis/handler/MybatisPlusMetaObjectHandler.java`

**Step 1: MyBatis-Plus 配置类**

```java
package com.medical.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

**Step 2: 自动填充处理器**

```java
package com.medical.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "deleted", () -> 0, Integer.class);
        // createBy / updateBy 需从上下文获取当前用户ID，后续集成 Sa-Token 后补充
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
    }
}
```

**Step 3: 创建 spring.factories 自动配置**

```
# medical-common/medical-common-mybatis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.medical.common.mybatis.config.MybatisPlusConfig
com.medical.common.mybatis.handler.MybatisPlusMetaObjectHandler
```

**Step 4: Commit**

```bash
git add .
git commit -m "feat(common-mybatis): add MybatisPlus config with pagination and auto-fill"
```

---

## Task 8: medical-common-redis - Redis 配置和工具

**Files:**
- Create: `medical-ai/medical-common/medical-common-redis/src/main/java/com/medical/common/redis/config/RedisConfig.java`
- Create: `medical-ai/medical-common/medical-common-redis/src/main/java/com/medical/common/redis/util/RedisUtil.java`

**Step 1: Redis 序列化配置**

```java
package com.medical.common.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
```

**Step 2: Redis 工具类**

```java
package com.medical.common.redis.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Long decrement(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }
}
```

**Step 3: 创建自动配置注册**

```
# medical-common/medical-common-redis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.medical.common.redis.config.RedisConfig
```

**Step 4: Commit**

```bash
git add .
git commit -m "feat(common-redis): add Redis serialization config and RedisUtil"
```

---

## Task 9: medical-common-security - Sa-Token 鉴权配置

**Files:**
- Create: `medical-ai/medical-common/medical-common-security/src/main/java/com/medical/common/security/config/SaTokenConfig.java`
- Create: `medical-ai/medical-common/medical-common-security/src/main/java/com/medical/common/security/util/SecurityUtil.java`
- Create: `medical-ai/medical-common/medical-common-security/src/main/java/com/medical/common/security/handler/SaTokenExceptionHandler.java`

**Step 1: Sa-Token 配置**

```java
package com.medical.common.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/wx-login",
                        "/auth/register",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/actuator/**"
                );
    }
}
```

**Step 2: 安全工具类**

```java
package com.medical.common.security.util;

import cn.dev33.satoken.stp.StpUtil;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;

public class SecurityUtil {

    /** 获取当前登录用户ID */
    public static Long getUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 获取当前登录用户角色列表 */
    public static java.util.List<String> getRoles() {
        return StpUtil.getRoleList();
    }

    /** 检查当前用户是否具有指定角色 */
    public static boolean hasRole(String role) {
        return StpUtil.hasRole(role);
    }

    /** 判断是否已登录 */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /** 当前用户退出登录 */
    public static void logout() {
        StpUtil.logout();
    }
}
```

**Step 3: Sa-Token 异常处理**

```java
package com.medical.common.security.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@Order(-1) // 优先于 GlobalExceptionHandler
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        log.warn("未登录: {}", e.getMessage());
        return R.fail(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMsg());
    }

    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException e) {
        log.warn("无角色: {}", e.getMessage());
        return R.fail(ErrorCode.FORBIDDEN.getCode(), "无权限: 缺少角色 " + e.getRole());
    }

    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermission(NotPermissionException e) {
        log.warn("无权限: {}", e.getMessage());
        return R.fail(ErrorCode.FORBIDDEN.getCode(), "无权限: " + e.getPermission());
    }
}
```

**Step 4: 创建自动配置注册**

```
# medical-common/medical-common-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.medical.common.security.config.SaTokenConfig
com.medical.common.security.handler.SaTokenExceptionHandler
```

**Step 5: Commit**

```bash
git add .
git commit -m "feat(common-security): add Sa-Token auth config, SecurityUtil and exception handler"
```

---

## Task 10: 全量编译验证

**Step 1: 编译公共模块**

Run: `mvn clean compile -f medical-ai/pom.xml`
Expected: BUILD SUCCESS

**Step 2: 如有错误，修复后 Commit**

```bash
git add .
git commit -m "fix(common): fix compilation issues in common modules"
```

---

## 检查清单

完成本计划后应具备：
- [ ] R 统一响应体 + isSuccess() 方法
- [ ] ErrorCode 枚举覆盖所有模块错误码
- [ ] BusinessException + GlobalExceptionHandler
- [ ] PageQuery / PageResult 分页封装
- [ ] BaseEntity 含 createTime/updateTime/createBy/updateBy/deleted
- [ ] MybatisPlus 分页插件 + 自动填充 Handler
- [ ] Redis 序列化配置 + RedisUtil 工具类
- [ ] Sa-Token 拦截器配置 + SecurityUtil + 鉴权异常处理
- [ ] 所有公共模块 Spring Boot 自动配置注册完成
- [ ] `mvn clean compile` 通过
