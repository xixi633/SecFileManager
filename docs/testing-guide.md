# 安全文件管理系统 - 测试指南

## 一、测试环境准备

### 1.1 数据库准备

```bash
# 1. 创建数据库并执行建表脚本
mysql -u root -p < database/schema.sql

# 2. 验证表是否创建成功
mysql -u root -p
USE secure_file_manager;
SHOW TABLES;
```

### 1.2 配置文件检查

检查 `application.yml` 配置：
- 数据库连接信息
- 文件存储路径（确保目录存在或有创建权限）
- 系统主密钥

### 1.3 启动项目

```bash
# Maven方式启动
mvn spring-boot:run

# 或编译后运行
mvn clean package
java -jar target/secure-file-manager-1.0.0.jar
```

启动成功后，访问 Swagger 文档：
```
http://localhost:8080/api/doc.html
```

---

## 二、功能测试用例

### 2.1 用户注册测试

**测试目的**：验证用户注册功能和密码安全存储

**测试步骤**：

1. 打开 Swagger 文档
2. 找到 `POST /api/user/register` 接口
3. 输入测试数据：
```json
{
  "username": "testuser",
  "password": "Test123456",
  "email": "test@example.com"
}
```
4. 点击"执行"

**预期结果**：
```json
{
  "code": 200,
  "message": "注册成功",
  "data": null
}
```

**数据库验证**：
```sql
SELECT username, password_hash, password_salt, 
       LENGTH(password_hash), LENGTH(password_salt)
FROM t_user 
WHERE username = 'testuser';
```

**验证要点**：
- ✅ `password_hash` 不是明文密码
- ✅ `password_salt` 每个用户不同（多注册几个用户对比）
- ✅ `master_key_encrypted` 已生成

---

### 2.2 用户登录测试

**测试目的**：验证密码校验和JWT生成

**测试步骤**：

1. 使用正确密码登录：
```json
{
  "username": "testuser",
  "password": "Test123456"
}
```

**预期结果**：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 1,
    "username": "testuser"
  }
}
```

2. 使用错误密码登录：
```json
{
  "username": "testuser",
  "password": "WrongPassword"
}
```

**预期结果**：
```json
{
  "code": 500,
  "message": "用户名或密码错误"
}
```

**验证要点**：
- ✅ 正确密码返回Token
- ✅ 错误密码返回失败
- ✅ Token格式正确

---

### 2.3 文件上传测试

**测试目的**：验证文件加密存储功能

**前置条件**：已登录并获取Token

**测试步骤**：

1. 在 Swagger 中点击右上角"Authorize"按钮
2. 输入Token（格式：`Bearer {token}`）
3. 找到 `POST /api/file/upload` 接口
4. 选择一个测试文件（建议：小文本文件，方便验证）
5. 填写描述（可选）
6. 点击"执行"

**预期结果**：
```json
{
  "code": 200,
  "message": "上传成功",
  "data": 1
}
```

**数据库验证**：
```sql
SELECT id, original_filename, file_size, encrypted_size,
       storage_path, iv, auth_tag, file_hash
FROM t_file 
WHERE id = 1;
```

**文件系统验证**：
```bash
# 查看加密文件
cat D:/projects/SecFileManager/storage/2026/01/{uuid}.enc

# 应该看到乱码，不是原始内容
```

**验证要点**：
- ✅ 数据库记录已创建
- ✅ 物理文件已加密存储
- ✅ `iv` 和 `auth_tag` 已记录
- ✅ `file_hash` 已计算
- ✅ 加密文件无法直接阅读

---

### 2.4 文件列表测试

**测试目的**：验证用户隔离

**测试步骤**：

1. 用户A登录并上传文件
2. 用户B登录并上传文件
3. 用户A查询文件列表（`GET /api/file/list`）

**预期结果**：
- 用户A只能看到自己的文件
- 用户B只能看到自己的文件

**数据库验证**：
```sql
-- 查看所有文件
SELECT id, user_id, original_filename 
FROM t_file;

-- 验证查询带user_id条件
-- 查看MyBatis日志，确认SQL包含 WHERE user_id = ?
```

---

### 2.5 文件下载测试

**测试目的**：验证解密和完整性校验

**测试步骤**：

1. 记录上传文件的原始内容（如：test.txt内容为"Hello World"）
2. 上传文件，获取文件ID
3. 下载文件：`GET /api/file/download/{fileId}`
4. 对比下载的文件内容

**预期结果**：
- ✅ 下载的文件内容与原始文件完全一致
- ✅ 文件名正确
- ✅ 下载次数 +1

**验证要点**：
```sql
-- 验证下载次数增加
SELECT id, download_count, last_download_time 
FROM t_file 
WHERE id = {fileId};
```

---

### 2.6 越权访问测试

**测试目的**：验证权限控制

**测试场景1：访问其他用户的文件**

1. 用户A上传文件，获取文件ID = 1
2. 用户B登录，尝试下载文件ID = 1

**预期结果**：
```json
{
  "code": 500,
  "message": "文件不存在或无访问权限"
}
```

**测试场景2：未登录访问**

1. 不携带Token
2. 尝试访问 `/api/file/list`

**预期结果**：
- HTTP 401 Unauthorized

---

### 2.7 完整性校验测试

**测试目的**：验证文件篡改检测

**测试场景1：修改加密文件**

```sql
-- 1. 上传文件并记录storage_path
SELECT id, storage_path FROM t_file WHERE id = 1;

-- 2. 手动修改加密文件（用十六进制编辑器修改几个字节）

-- 3. 尝试下载文件
```

**预期结果**：
```json
{
  "code": 500,
  "message": "文件完整性校验失败，数据已被篡改"
}
```

**测试场景2：修改Auth Tag**

```sql
-- 修改数据库中的auth_tag
UPDATE t_file 
SET auth_tag = REPLACE(auth_tag, '0', '1') 
WHERE id = 1;

-- 尝试下载
```

**预期结果**：
- 解密失败，抛出异常

**测试场景3：修改文件哈希**

```sql
-- 修改数据库中的file_hash
UPDATE t_file 
SET file_hash = REPLACE(file_hash, 'a', 'b') 
WHERE id = 1;

-- 尝试下载
```

**预期结果**：
```json
{
  "code": 500,
  "message": "文件完整性校验失败"
}
```

---

## 三、安全性测试

### 3.1 密码安全测试

**测试目的**：验证密码不可逆

**测试步骤**：

1. 注册用户，密码为 `Test123456`
2. 查询数据库：
```sql
SELECT username, password_hash, password_salt 
FROM t_user 
WHERE username = 'testuser';
```

**验证要点**：
- ❌ 无法从 `password_hash` 反推出 `Test123456`
- ✅ 相同密码在不同用户产生不同哈希值（因为盐值不同）

**演示**：
```sql
-- 创建两个用户，使用相同密码
INSERT INTO users (username, password) VALUES ('user1', 'SamePassword');
INSERT INTO users (username, password) VALUES ('user2', 'SamePassword');

-- 查看哈希值
SELECT username, password_hash FROM t_user;

-- 结果：password_hash 不同
```

---

### 3.2 文件加密测试

**测试目的**：验证文件内容不可直接读取

**测试步骤**：

1. 创建测试文件 `secret.txt`，内容：
```
This is a secret message!
Do not leak this information.
```

2. 上传文件
3. 找到物理存储路径
4. 尝试直接打开加密文件

**预期结果**：
- ✅ 文件内容为乱码
- ✅ 无法识别原始内容
- ✅ 搜索关键词（如"secret"）无匹配

**验证命令**：
```bash
# Linux/Mac
cat /path/to/encrypted/file.enc | grep "secret"
# 应该无结果

# Windows
type D:\path\to\encrypted\file.enc | findstr "secret"
# 应该无结果
```

---

### 3.3 用户隔离测试

**测试目的**：验证用户间数据完全隔离

**测试步骤**：

1. 创建用户A，上传文件 `fileA.txt`
2. 创建用户B，上传文件 `fileB.txt`
3. 查询数据库确认文件ID
4. 用户A尝试下载 `fileB.txt`

**SQL验证**：
```sql
-- 验证查询条件
-- 开启MyBatis SQL日志，观察生成的SQL
-- 应该包含: WHERE id = ? AND user_id = ?
```

**预期结果**：
- ✅ 用户A无法下载用户B的文件
- ✅ 返回"无访问权限"错误

---

### 3.4 密钥隔离测试

**测试目的**：验证密钥层次架构

**测试步骤**：

1. 查询两个用户的主密钥：
```sql
SELECT id, username, master_key_encrypted 
FROM t_user 
WHERE id IN (1, 2);
```

2. 观察 `master_key_encrypted` 不同

**原理说明**：
```
用户A：
  用户主密钥A → 加密文件密钥A1, A2, A3
  
用户B：
  用户主密钥B → 加密文件密钥B1, B2, B3

即使用户A获取了用户B的加密文件和加密文件密钥：
→ 无法解密文件密钥（因为需要用户B的主密钥）
→ 无法解密文件内容
```

---

## 四、性能测试

### 4.1 文件大小测试

**测试文件**：
- 小文件：1KB
- 中文件：1MB
- 大文件：10MB

**测试指标**：
- 上传耗时
- 加密耗时
- 下载耗时
- 解密耗时

**记录格式**：
```
文件大小 | 上传时间 | 加密时间 | 下载时间 | 解密时间
--------|---------|---------|---------|----------
1KB     | 50ms    | 10ms    | 30ms    | 8ms
1MB     | 200ms   | 50ms    | 150ms   | 45ms
10MB    | 1500ms  | 400ms   | 1200ms  | 380ms
```

---

## 五、课程演示建议

### 5.1 演示流程

**第一部分：基本功能演示**（5分钟）
1. 用户注册
2. 用户登录
3. 文件上传
4. 文件下载
5. 文件列表查询

**第二部分：安全机制演示**（10分钟）

1. **密码安全**：
   - 展示数据库中的密码哈希
   - 说明无法反推原密码

2. **文件加密**：
   - 对比原始文件和加密文件
   - 说明AES-GCM算法

3. **完整性校验**：
   - 修改加密文件
   - 触发校验失败

4. **权限隔离**：
   - 演示越权访问失败
   - 说明SQL强制user_id条件

**第三部分：架构讲解**（10分钟）
- 三层密钥架构图
- 文件加密流程图
- 完整性校验机制
- 攻击防御能力

---

## 六、常见问题排查

### 6.1 启动失败

**问题**：`java.sql.SQLException: Access denied`

**解决**：检查 `application.yml` 中的数据库用户名密码

---

**问题**：`Table 't_user' doesn't exist`

**解决**：执行建表脚本 `database/schema.sql`

---

### 6.2 文件上传失败

**问题**：`java.io.FileNotFoundException`

**解决**：
1. 检查 `storage-root` 配置
2. 确保目录存在或有创建权限
3. Windows系统注意路径格式（如 `D:/projects/...`）

---

### 6.3 Token验证失败

**问题**：`401 Unauthorized`

**解决**：
1. 检查Token格式：`Bearer {token}`
2. 检查Token是否过期（默认24小时）
3. 重新登录获取新Token

---

### 6.4 文件下载乱码

**问题**：文件名显示为乱码

**解决**：
- 已在代码中使用 `URLEncoder.encode()` 处理
- 确保浏览器支持UTF-8编码

---

## 七、测试报告模板

### 7.1 功能测试报告

| 测试项 | 测试结果 | 备注 |
|-------|---------|------|
| 用户注册 | ✅ 通过 | 密码已加密存储 |
| 用户登录 | ✅ 通过 | JWT Token正常生成 |
| 文件上传 | ✅ 通过 | 文件已加密存储 |
| 文件下载 | ✅ 通过 | 解密正确 |
| 文件列表 | ✅ 通过 | 用户隔离正常 |
| 越权访问 | ✅ 通过 | 成功拦截 |
| 完整性校验 | ✅ 通过 | 篡改检测正常 |

### 7.2 安全测试报告

| 安全项 | 测试方法 | 测试结果 |
|-------|---------|---------|
| 密码存储 | 查看数据库 | ✅ 已哈希 |
| 文件加密 | 直接读取加密文件 | ✅ 无法识别 |
| 权限控制 | 越权访问测试 | ✅ 拒绝访问 |
| 完整性 | 修改文件/哈希 | ✅ 检测到篡改 |
| 密钥管理 | 查看密钥存储 | ✅ 已加密存储 |

---

## 八、测试数据清理

测试完成后，清理测试数据：

```sql
-- 清空数据
TRUNCATE TABLE t_file;
TRUNCATE TABLE t_user;

-- 或删除整个数据库
DROP DATABASE secure_file_manager;
```

删除物理文件：
```bash
rm -rf /path/to/storage/*
```

---

**测试文档版本**：v1.0  
**更新日期**：2026-01-23
