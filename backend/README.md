# 安全文件管理系统

> 课程设计项目 - 重点展示文件加密存储、完整性校验与权限控制

## 📋 项目简介

这是一个基于 Spring Boot 的安全文件管理系统后端，专为课程设计和实验演示而设计。系统实现了完整的文件加密存储、完整性校验和用户隔离机制。

### 核心特性

✅ **文件加密存储**：采用 AES-256-GCM 加密算法\
✅ **完整性校验**：双重校验机制（GCM Auth Tag + SHA-256）\
✅ **用户隔离**：强制权限控制，用户间文件完全隔离\
✅ **密钥管理**：三层密钥架构（系统密钥 → 用户密钥 → 文件密钥）\
✅ **密码安全**：PBKDF2-HMAC-SHA256 + 独立盐值

***

## 🏗️ 技术栈

| 类别  | 技术           | 版本      |
| --- | ------------ | ------- |
| 语言  | Java         | 17      |
| 框架  | Spring Boot  | 2.6.15  |
| ORM | MyBatis-Plus | 3.5.3.1 |
| 数据库 | MySQL        | 8.x     |
| 连接池 | Druid        | 1.2.16  |
| 文档  | Knife4j      | 3.0.3   |
| 认证  | JWT          | 0.11.5  |

***

## 🔐 安全设计

### 1. 文件加密方案

```
【加密算法】AES-256-GCM
【密钥长度】256 位
【IV长度】96 位（GCM 推荐）
【认证标签】128 位
```

**为什么选择 GCM 模式？**

- ✅ AEAD（认证加密）：同时提供机密性和完整性
- ✅ 无需 padding，避免 padding oracle 攻击
- ✅ 并行计算，性能优于 CBC 模式
- ✅ TLS 1.3 默认使用，业界标准

### 2. 密钥管理架构

```
系统主密钥（System Master Key）
    ↓ 加密
用户主密钥（KEK - Key Encryption Key）
    ↓ 加密
文件密钥（DEK - Data Encryption Key）
    ↓ 加密
文件内容
```

**三层密钥隔离**：

1. 系统主密钥：存储在配置文件
2. 用户主密钥：随机生成，用系统密钥加密后存入数据库
3. 文件密钥：每个文件独立，用用户密钥加密后存入数据库

### 3. 完整性校验

**双重校验机制**：

1. **GCM Auth Tag**：解密时自动校验，防止加密数据被篡改
2. **SHA-256 哈希**：对比原始文件哈希，确保解密正确性

### 4. 密码存储

```
【算法】PBKDF2-HMAC-SHA256
【迭代次数】210,000（OWASP 2023 推荐）
【盐值长度】16 字节
【输出长度】32 字节
```

***

## 🧪 安全机制验证指南

为了验证本系统的安全性，您可以尝试以下操作：

### 1. 验证密码存储安全性

- 查看数据库 `t_user` 表，观察 `password_hash` 和 `password_salt` 字段。
- **验证点**：不同的用户即使密码相同，其 `password_hash` 也是完全不同的（因为 `password_salt` 不同）。
- **验证点**：`password_hash` 长度固定，且不可逆。

### 2. 验证文件加密存储

- 上传一个文本文件（如 `test.txt`），内容为 "Hello World"。
- 在 `storage/` 目录下找到对应的物理文件。
- 使用记事本打开该文件。
- **验证点**：文件内容应为乱码（加密后的数据），无法看到 "Hello World"。

### 3. 验证完整性校验（防篡改）

- 上传文件后，使用二进制编辑器（如 Hex Editor）修改 `storage/` 下对应加密文件的一个字节。
- 尝试在系统中下载该文件。
- **验证点**：下载请求应失败，服务器日志会抛出 `AEADBadTagException` 或完整性校验错误，证明系统成功检测到了篡改。

***

## 📁 项目结构

```
src/main/java/com/security/filemanager/
├── controller/          # 控制器层
│   ├── UserController.java
│   └── FileController.java
├── service/            # 服务层
│   ├── UserService.java
│   └── FileService.java
├── mapper/             # 数据访问层
│   ├── UserMapper.java
│   └── FileMapper.java
├── entity/             # 实体类
│   ├── User.java
│   └── FileInfo.java
├── dto/                # 数据传输对象
│   ├── Result.java
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   └── FileInfoResponse.java
├── util/               # 工具类
│   ├── AESUtil.java         # AES 加密工具
│   ├── PasswordUtil.java    # 密码工具
│   └── JwtUtil.java         # JWT 工具
├── config/             # 配置类
│   ├── SwaggerConfig.java
│   └── WebMvcConfig.java
├── interceptor/        # 拦截器
│   └── AuthInterceptor.java
└── exception/          # 异常处理
    └── GlobalExceptionHandler.java

src/main/resources/
├── mapper/             # MyBatis XML
│   ├── UserMapper.xml
│   └── FileMapper.xml
└── application.yml     # 配置文件

database/
└── schema.sql          # 数据库建表脚本
```

***

## 🚀 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+

### 2. 数据库初始化

```bash
# 执行建表脚本
mysql -u root -p < database/schema.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/secure_file_manager
    username: root
    password: your_password

secure-file:
  storage-root: D:/projects/SecFileManager/storage
  system-master-key: "YourSystemMasterKey32BytesLong!!"
```

### 4. 启动项目

```bash
mvn spring-boot:run
```

### 5. 访问 Swagger 文档

```
http://localhost:8080/api/doc.html
```

***

## 📖 API 接口

### 用户模块

#### 1. 用户注册

```http
POST /api/user/register
Content-Type: application/json

{
  "username": "zhangsan",
  "password": "password123",
  "email": "zhangsan@example.com"
}
```

#### 2. 用户登录

```http
POST /api/user/login
Content-Type: application/json

{
  "username": "zhangsan",
  "password": "password123"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 1,
    "username": "zhangsan"
  }
}
```

### 文件模块

所有文件接口需要在请求头中携带 Token：

```
Authorization: Bearer {token}
```

#### 3. 文件上传

```http
POST /api/file/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: <binary>
description: "测试文件"
```

#### 4. 文件下载

```http
GET /api/file/download/{fileId}
Authorization: Bearer {token}
```

#### 5. 文件列表

```http
GET /api/file/list
Authorization: Bearer {token}
```

#### 6. 删除文件

```http
DELETE /api/file/{fileId}
Authorization: Bearer {token}
```

***

## 🔒 安全机制详解

### 文件上传流程

```
1. 用户上传文件（MultipartFile）
   ↓
2. 计算原始文件 SHA-256 哈希
   ↓
3. 生成随机文件密钥（DEK，256位）
   ↓
4. 生成随机 IV（96位）
   ↓
5. 使用 AES-GCM 加密文件
   输出：密文 + Auth Tag
   ↓
6. 使用用户主密钥加密文件密钥
   ↓
7. 生成随机 UUID 存储路径
   ↓
8. 写入加密文件到磁盘
   ↓
9. 保存元数据到数据库
```

### 文件下载流程

```
1. 验证用户身份（JWT Token）
   ↓
2. 权限校验（WHERE user_id = ? AND id = ?）
   若查不到 → 无权限
   ↓
3. 读取数据库中的加密参数
   ↓
4. 解密文件密钥（使用用户主密钥）
   ↓
5. 读取加密文件
   ↓
6. 解密文件（GCM 自动校验 Auth Tag）
   若 Auth Tag 不匹配 → 文件被篡改
   ↓
7. 计算解密后文件的 SHA-256
   ↓
8. 与数据库中的哈希对比
   不匹配 → 完整性校验失败
   ↓
9. 返回原始文件
```

### 权限控制机制

**用户隔离**：

- 所有文件查询强制带 `user_id` 条件
- 即使知道文件ID，也无法访问其他用户的文件

**密钥隔离**：

- 每个用户的主密钥独立
- 文件密钥使用所有者的主密钥加密
- 其他用户无法解密文件密钥，即使获取加密文件也无法解密

***

## 🛡️ 防御的攻击类型

| 攻击类型       | 防御机制                        |
| ---------- | --------------------------- |
| **数据泄露**   | 文件内容加密存储，明文不落盘              |
| **暴力破解密码** | PBKDF2 高迭代次数，增加破解成本         |
| **彩虹表攻击**  | 每个用户独立盐值                    |
| **时序攻击**   | 常量时间字符串比较                   |
| **文件篡改**   | GCM Auth Tag + SHA-256 双重校验 |
| **越权访问**   | 强制 user\_id 条件，密钥隔离         |
| **路径遍历**   | UUID 随机路径，无法猜测              |
| **重放攻击**   | JWT 有效期限制                   |

***

## 📚 课程设计要点

### 可讲解的安全知识点

1. **对称加密与分组模式**
   - AES 算法原理
   - GCM 与 CBC 模式对比
   - AEAD 认证加密
2. **密钥管理**
   - 密钥派生函数（KDF）
   - 密钥包装（Key Wrapping）
   - 密钥层次结构
3. **密码学哈希**
   - SHA-256 算法
   - PBKDF2 密钥派生
   - HMAC 消息认证码
4. **完整性校验**
   - 哈希值对比
   - MAC（消息认证码）
   - 数字签名（扩展方向）
5. **身份认证**
   - JWT 无状态认证
   - Token 签名验证
   - 会话管理
6. **访问控制**
   - 基于用户的权限控制
   - 资源隔离
   - 最小权限原则

### 实验演示建议

1. **加密效果演示**：
   - 上传文本文件，查看存储的加密文件（无法识别内容）
   - 修改加密文件，下载时触发完整性校验失败
2. **权限隔离演示**：
   - 创建两个用户，分别上传文件
   - 尝试用用户A的Token访问用户B的文件ID（403）
3. **密码安全演示**：
   - 查看数据库中的密码哈希（无法反推原密码）
   - 相同密码在不同用户产生不同哈希（盐值作用）
4. **完整性校验演示**：
   - 手动修改数据库中的 `auth_tag` 或 `file_hash`
   - 下载文件触发校验失败

***

## ⚠️ 注意事项

### 课程设计用途声明

本项目专为教学演示设计，**不适合直接用于生产环境**。

**已简化的部分**：

- 系统主密钥写在配置文件（生产应使用 HSM 或密钥管理服务）
- 未实现密钥轮换（Key Rotation）
- 未实现文件分片上传
- 未实现文件预览功能
- 未做并发控制和限流

**如需生产化，需要增加**：

- 密钥管理服务（KMS）
- 审计日志
- 操作监控
- 备份恢复机制
- 分布式文件存储

***

## 📝 实验报告建议

### 系统设计部分

1. 总体架构图
2. 数据库ER图
3. 文件加密流程图
4. 密钥管理方案图

### 安全分析部分

1. 威胁模型分析
2. 安全机制设计
3. 攻击防御能力
4. 安全性证明

### 实验结果部分

1. 功能测试截图
2. 加密效果展示
3. 权限控制验证
4. 性能测试结果

***

## 👨‍🏫 常见问题

### Q1: 为什么不直接用用户密码加密文件？

**A**: 使用三层密钥架构有以下优势：

- 用户修改密码时，只需重新加密用户主密钥，不需要重新加密所有文件
- 支持多用户共享文件（扩展功能）
- 符合密钥管理最佳实践

### Q2: 为什么需要双重完整性校验？

**A**:

- GCM Auth Tag 保证加密数据完整性（传输层）
- SHA-256 保证原始文件完整性（应用层）
- 双重保险，防止解密过程中的错误

### Q3: 为什么选择 PBKDF2 而不是 bcrypt？

**A**:

- PBKDF2 是 Java 标准库原生支持，无需第三方库
- 符合 OWASP 推荐标准
- 课程演示更易理解
- 实际项目中 bcrypt 或 Argon2 更佳

***

## 📄 许可证

本项目仅用于教育和学习目的。

***

## 👥 贡献者

Course Design Project - 2026

***

## 📧 联系方式

如有问题，请通过以下方式联系：

- Issue: 项目 GitHub Issues
- Email: \[your-email]

***

**祝课程设计顺利！🎓**
