# 01 - 项目初始化 + Maven 多模块 + Docker 基础设施

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 搭建后端 Maven 多模块骨架、前端项目骨架、Docker Compose 基础设施，确保所有模块可编译通过。

**Architecture:** 父 POM 管理全局依赖版本，子模块各自打包为 Spring Boot fat jar。基础设施用 Docker Compose 一键启动。

**Tech Stack:** Maven 3.9+, JDK 17, Spring Boot 3.3.x, Spring Cloud 2023.0.x, Spring Cloud Alibaba 2023.0.x, Docker Compose

---

## Task 1: 创建父 POM

**Files:**
- Create: `medical-ai/pom.xml`

**Step 1: 创建根目录和父 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.medical</groupId>
    <artifactId>medical-ai</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>medical-ai</name>
    <description>AI 数字人医疗小助手系统</description>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- Spring 版本锁定 -->
        <spring-boot.version>3.3.6</spring-boot.version>
        <spring-cloud.version>2023.0.4</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.3.2</spring-cloud-alibaba.version>
        <spring-ai.version>1.0.0-M5</spring-ai.version>

        <!-- 第三方依赖版本 -->
        <mybatis-plus.version>3.5.9</mybatis-plus.version>
        <mysql.version>8.0.33</mysql.version>
        <druid.version>1.2.23</druid.version>
        <hutool.version>5.8.34</hutool.version>
        <sa-token.version>1.39.0</sa-token.version>
        <knife4j.version>4.5.0</knife4j.version>
        <milvus-sdk.version>2.4.3</milvus-sdk.version>
        <aliyun-sdk-nls.version>2.2.1</aliyun-sdk-nls.version>
        <mapstruct.version>1.6.3</mapstruct.version>
        <lombok.version>1.18.36</lombok.version>
    </properties>

    <modules>
        <module>medical-common</module>
        <module>medical-gateway</module>
        <module>medical-api</module>
        <module>medical-service</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud Alibaba BOM -->
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring AI BOM -->
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 内部模块版本 -->
            <dependency>
                <groupId>com.medical</groupId>
                <artifactId>medical-common-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.medical</groupId>
                <artifactId>medical-common-security</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.medical</groupId>
                <artifactId>medical-common-mybatis</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.medical</groupId>
                <artifactId>medical-common-redis</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.medical</groupId>
                <artifactId>medical-user-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.medical</groupId>
                <artifactId>medical-doctor-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.medical</groupId>
                <artifactId>medical-appointment-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.medical</groupId>
                <artifactId>medical-knowledge-api</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- MyBatis-Plus -->
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <!-- MySQL -->
            <dependency>
                <groupId>com.mysql</groupId>
                <artifactId>mysql-connector-j</artifactId>
                <version>${mysql.version}</version>
            </dependency>
            <!-- Druid -->
            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>druid-spring-boot-3-starter</artifactId>
                <version>${druid.version}</version>
            </dependency>
            <!-- Hutool -->
            <dependency>
                <groupId>cn.hutool</groupId>
                <artifactId>hutool-all</artifactId>
                <version>${hutool.version}</version>
            </dependency>
            <!-- Sa-Token -->
            <dependency>
                <groupId>cn.dev33</groupId>
                <artifactId>sa-token-spring-boot3-starter</artifactId>
                <version>${sa-token.version}</version>
            </dependency>
            <dependency>
                <groupId>cn.dev33</groupId>
                <artifactId>sa-token-redis-jackson</artifactId>
                <version>${sa-token.version}</version>
            </dependency>
            <dependency>
                <groupId>cn.dev33</groupId>
                <artifactId>sa-token-reactor-spring-boot3-starter</artifactId>
                <version>${sa-token.version}</version>
            </dependency>
            <!-- Knife4j -->
            <dependency>
                <groupId>com.github.xiaoymin</groupId>
                <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
                <version>${knife4j.version}</version>
            </dependency>
            <!-- Milvus -->
            <dependency>
                <groupId>io.milvus</groupId>
                <artifactId>milvus-sdk-java</artifactId>
                <version>${milvus-sdk.version}</version>
            </dependency>
            <!-- MapStruct -->
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                    <executions>
                        <execution>
                            <goals>
                                <goal>repackage</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
    </repositories>
</project>
```

**Step 2: 验证 POM 语法**

Run: `mvn validate` (在 medical-ai 目录)
Expected: BUILD SUCCESS（此时子模块还不存在会警告，但 POM 本身合法）

**Step 3: Commit**

```bash
git add medical-ai/pom.xml
git commit -m "chore: init parent pom with dependency management"
```

---

## Task 2: 创建 medical-common 聚合模块

**Files:**
- Create: `medical-ai/medical-common/pom.xml`

**Step 1: 创建 common 聚合 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-ai</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-common</artifactId>
    <packaging>pom</packaging>
    <name>medical-common</name>

    <modules>
        <module>medical-common-core</module>
        <module>medical-common-security</module>
        <module>medical-common-mybatis</module>
        <module>medical-common-redis</module>
    </modules>
</project>
```

---

## Task 3: 创建 medical-common-core 骨架

**Files:**
- Create: `medical-ai/medical-common/medical-common-core/pom.xml`
- Create: `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/package-info.java`

**Step 1: 创建 core POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-common</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-common-core</artifactId>
    <name>medical-common-core</name>
    <description>通用工具、异常定义、响应体</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
        </dependency>
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
```

**Step 2: 创建 package-info 占位**

```java
/**
 * 通用核心模块 - 工具类、异常、响应体
 */
package com.medical.common.core;
```

---

## Task 4: 创建 medical-common-security 骨架

**Files:**
- Create: `medical-ai/medical-common/medical-common-security/pom.xml`
- Create: `medical-ai/medical-common/medical-common-security/src/main/java/com/medical/common/security/package-info.java`

**Step 1: 创建 security POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-common</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-common-security</artifactId>
    <name>medical-common-security</name>
    <description>Sa-Token 鉴权公共配置</description>

    <dependencies>
        <dependency>
            <groupId>com.medical</groupId>
            <artifactId>medical-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-redis-jackson</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## Task 5: 创建 medical-common-mybatis 骨架

**Files:**
- Create: `medical-ai/medical-common/medical-common-mybatis/pom.xml`
- Create: `medical-ai/medical-common/medical-common-mybatis/src/main/java/com/medical/common/mybatis/package-info.java`

**Step 1: 创建 mybatis POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-common</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-common-mybatis</artifactId>
    <name>medical-common-mybatis</name>
    <description>MyBatis-Plus 公共配置</description>

    <dependencies>
        <dependency>
            <groupId>com.medical</groupId>
            <artifactId>medical-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-3-starter</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## Task 6: 创建 medical-common-redis 骨架

**Files:**
- Create: `medical-ai/medical-common/medical-common-redis/pom.xml`
- Create: `medical-ai/medical-common/medical-common-redis/src/main/java/com/medical/common/redis/package-info.java`

**Step 1: 创建 redis POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-common</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-common-redis</artifactId>
    <name>medical-common-redis</name>
    <description>Redis 缓存公共配置</description>

    <dependencies>
        <dependency>
            <groupId>com.medical</groupId>
            <artifactId>medical-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## Task 7: 创建 medical-api 聚合模块 + 4 个 Feign API 骨架

**Files:**
- Create: `medical-ai/medical-api/pom.xml`
- Create: `medical-ai/medical-api/medical-user-api/pom.xml`
- Create: `medical-ai/medical-api/medical-doctor-api/pom.xml`
- Create: `medical-ai/medical-api/medical-appointment-api/pom.xml`
- Create: `medical-ai/medical-api/medical-knowledge-api/pom.xml`

**Step 1: 创建 api 聚合 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-ai</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-api</artifactId>
    <packaging>pom</packaging>
    <name>medical-api</name>

    <modules>
        <module>medical-user-api</module>
        <module>medical-doctor-api</module>
        <module>medical-appointment-api</module>
        <module>medical-knowledge-api</module>
    </modules>
</project>
```

**Step 2: 每个 api 子模块 POM（以 user-api 为例，其余类似）**

```xml
<!-- medical-api/medical-user-api/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-api</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-user-api</artifactId>
    <name>medical-user-api</name>
    <description>用户服务 Feign API 定义</description>

    <dependencies>
        <dependency>
            <groupId>com.medical</groupId>
            <artifactId>medical-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
    </dependencies>
</project>
```

每个 api 模块创建对应 `src/main/java/com/medical/api/<module>/package-info.java` 占位文件。

---

## Task 8: 创建 medical-service 聚合模块 + 5 个业务服务骨架

**Files:**
- Create: `medical-ai/medical-service/pom.xml`
- Create: `medical-ai/medical-service/medical-user-service/pom.xml`
- Create: `medical-ai/medical-service/medical-doctor-service/pom.xml`
- Create: `medical-ai/medical-service/medical-ai-service/pom.xml`
- Create: `medical-ai/medical-service/medical-appointment-service/pom.xml`
- Create: `medical-ai/medical-service/medical-knowledge-service/pom.xml`

**Step 1: 创建 service 聚合 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-ai</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-service</artifactId>
    <packaging>pom</packaging>
    <name>medical-service</name>

    <modules>
        <module>medical-user-service</module>
        <module>medical-doctor-service</module>
        <module>medical-ai-service</module>
        <module>medical-appointment-service</module>
        <module>medical-knowledge-service</module>
    </modules>
</project>
```

**Step 2: user-service POM（其他服务类似模式）**

```xml
<!-- medical-service/medical-user-service/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-service</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-user-service</artifactId>
    <name>medical-user-service</name>

    <dependencies>
        <dependency>
            <groupId>com.medical</groupId>
            <artifactId>medical-common-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.medical</groupId>
            <artifactId>medical-common-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.medical</groupId>
            <artifactId>medical-common-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.medical</groupId>
            <artifactId>medical-user-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 3: 为每个 service 创建 Application 启动类**

```java
// medical-service/medical-user-service/src/main/java/com/medical/user/UserServiceApplication.java
package com.medical.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

**Step 4: 为每个 service 创建 application.yml**

```yaml
# medical-service/medical-user-service/src/main/resources/application.yml
server:
  port: 8081

spring:
  application:
    name: medical-user-service
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
        file-extension: yml
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/medical_user?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root123}

mybatis-plus:
  mapper-locations: classpath:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

对其他 4 个服务重复 Step 3-4，端口分别为：
- doctor-service: 8082, DB: medical_doctor
- ai-service: 8083, DB: medical_ai
- appointment-service: 8084, DB: medical_appointment
- knowledge-service: 8085, DB: medical_knowledge

---

## Task 9: 创建 medical-gateway 模块

**Files:**
- Create: `medical-ai/medical-gateway/pom.xml`
- Create: `medical-ai/medical-gateway/src/main/java/com/medical/gateway/GatewayApplication.java`
- Create: `medical-ai/medical-gateway/src/main/resources/application.yml`

**Step 1: Gateway POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.medical</groupId>
        <artifactId>medical-ai</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>medical-gateway</artifactId>
    <name>medical-gateway</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-reactor-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-redis-jackson</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 2: Gateway 启动类**

```java
package com.medical.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

**Step 3: Gateway application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: medical-gateway
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
    gateway:
      routes:
        - id: user-service
          uri: lb://medical-user-service
          predicates:
            - Path=/api/user/**
          filters:
            - StripPrefix=2
        - id: doctor-service
          uri: lb://medical-doctor-service
          predicates:
            - Path=/api/doctor/**
          filters:
            - StripPrefix=2
        - id: ai-service
          uri: lb://medical-ai-service
          predicates:
            - Path=/api/ai/**
          filters:
            - StripPrefix=2
        - id: appointment-service
          uri: lb://medical-appointment-service
          predicates:
            - Path=/api/appointment/**
          filters:
            - StripPrefix=2
        - id: knowledge-service
          uri: lb://medical-knowledge-service
          predicates:
            - Path=/api/knowledge/**
          filters:
            - StripPrefix=2
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOriginPatterns: "*"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379

sa-token:
  token-name: Authorization
  token-prefix: Bearer
  is-concurrent: true
  is-share: true
  is-read-cookie: false
  is-read-header: true
```

---

## Task 10: 创建 Docker Compose 基础设施

**Files:**
- Create: `medical-ai/docker/docker-compose.yml`
- Create: `medical-ai/docker/mysql/init.sql`

**Step 1: docker-compose.yml**

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: medical-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

  redis:
    image: redis:7-alpine
    container_name: medical-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  nacos:
    image: nacos/nacos-server:v2.3.2
    container_name: medical-nacos
    environment:
      MODE: standalone
      SPRING_DATASOURCE_PLATFORM: ""
      JVM_XMS: 256m
      JVM_XMX: 256m
    ports:
      - "8848:8848"
      - "9848:9848"

  milvus-etcd:
    image: quay.io/coreos/etcd:v3.5.5
    container_name: medical-milvus-etcd
    environment:
      ETCD_AUTO_COMPACTION_MODE: revision
      ETCD_AUTO_COMPACTION_RETENTION: "1000"
      ETCD_QUOTA_BACKEND_BYTES: "4294967296"
      ETCD_SNAPSHOT_COUNT: "50000"
    volumes:
      - etcd-data:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd

  milvus-minio:
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    container_name: medical-milvus-minio
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    ports:
      - "9001:9001"
    volumes:
      - minio-data:/minio_data
    command: minio server /minio_data --console-address ":9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  milvus:
    image: milvusdb/milvus:v2.4.5
    container_name: medical-milvus
    depends_on:
      - milvus-etcd
      - milvus-minio
    environment:
      ETCD_ENDPOINTS: milvus-etcd:2379
      MINIO_ADDRESS: milvus-minio:9000
    ports:
      - "19530:19530"
      - "9091:9091"
    volumes:
      - milvus-data:/var/lib/milvus

volumes:
  mysql-data:
  redis-data:
  etcd-data:
  minio-data:
  milvus-data:
```

**Step 2: MySQL 初始化脚本**

```sql
-- 创建各服务数据库
CREATE DATABASE IF NOT EXISTS medical_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS medical_doctor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS medical_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS medical_appointment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS medical_knowledge DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**Step 3: 验证 Docker Compose**

Run: `docker compose config` (在 docker 目录)
Expected: 输出合法的 YAML 配置，无错误

**Step 4: 启动基础设施**

Run: `docker compose up -d`
Expected: 5 个容器全部 running

**Step 5: Commit**

```bash
git add .
git commit -m "chore: init project skeleton with Maven multi-module and Docker infra"
```

---

## Task 11: 创建 .gitignore

**Files:**
- Create: `medical-ai/.gitignore`

```gitignore
# Maven
target/
*.jar
*.war

# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/

# Docker
docker/**/data/

# Env
.env
*.env.local
```

---

## Task 12: 全量编译验证

**Step 1: 编译整个项目**

Run: `mvn clean compile -f medical-ai/pom.xml`
Expected: BUILD SUCCESS，所有模块编译通过

**Step 2: Commit（如有修复）**

```bash
git add .
git commit -m "chore: fix compilation issues and verify project structure"
```

---

## 检查清单

完成本计划后应具备：
- [ ] 父 POM 管理所有依赖版本
- [ ] 4 个 common 子模块（core/security/mybatis/redis）骨架就绪
- [ ] 4 个 API 模块（Feign 接口定义）骨架就绪
- [ ] 5 个业务服务模块各有启动类和 application.yml
- [ ] Gateway 模块路由配置就绪
- [ ] Docker Compose 可一键启动 MySQL + Redis + Nacos + Milvus
- [ ] `mvn clean compile` 全量编译通过
