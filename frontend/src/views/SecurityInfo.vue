<template>
  <div>
    <el-card>
      <template #header>
        <h2 style="margin: 0;">系统安全设计说明</h2>
      </template>
      
      <el-collapse v-model="activeNames" accordion>
        <el-collapse-item title="1. 为什么需要文件加密存储？" name="1">
          <div class="content-section">
            <p><strong>安全威胁：</strong></p>
            <ul>
              <li>服务器被入侵，攻击者可能直接读取磁盘上的文件</li>
              <li>数据库泄露，文件存储路径可能被暴露</li>
              <li>内部人员滥用权限，直接访问存储目录</li>
            </ul>
            
            <p><strong>加密保护：</strong></p>
            <ul>
              <li>所有文件使用 <code>AES-256-GCM</code> 算法加密后存储</li>
              <li>即使攻击者获取到加密文件，没有密钥也无法解密</li>
              <li>每个文件使用独立的密钥，密钥本身也被加密保护</li>
            </ul>
          </div>
        </el-collapse-item>
        
        <el-collapse-item title="2. 文件完整性校验的作用" name="2">
          <div class="content-section">
            <p><strong>完整性威胁：</strong></p>
            <ul>
              <li>文件在传输过程中可能被篡改</li>
              <li>存储介质损坏导致文件部分损坏</li>
              <li>恶意攻击者替换或修改文件内容</li>
            </ul>
            
            <p><strong>校验机制：</strong></p>
            <ul>
              <li>上传时计算文件的 SHA-256 哈希值</li>
              <li>加密时生成认证标签（Authentication Tag）</li>
              <li>下载时自动验证哈希值和认证标签</li>
              <li>任何篡改都会导致校验失败，系统拒绝提供文件</li>
            </ul>
          </div>
        </el-collapse-item>
        
        <el-collapse-item title="3. 用户文件隔离策略" name="3">
          <div class="content-section">
            <p><strong>隔离原则：</strong></p>
            <ul>
              <li>每个用户只能访问自己上传的文件</li>
              <li>所有文件操作接口都强制检查 user_id</li>
              <li>即使知道其他用户的文件ID，也无法访问</li>
            </ul>
            
            <p><strong>技术实现：</strong></p>
            <ul>
              <li>登录时生成包含用户ID的 JWT Token</li>
              <li>每次请求都验证 Token 并提取用户ID</li>
              <li>数据库查询强制添加 user_id 条件</li>
              <li>文件密钥使用用户主密钥加密，其他用户无法解密</li>
            </ul>
          </div>
        </el-collapse-item>
        
        <el-collapse-item title="4. 密钥管理体系" name="4">
          <div class="content-section">
            <p><strong>三层密钥架构：</strong></p>
            
            <div style="margin: 15px 0;">
              <strong>第一层：系统主密钥（System Master Key）</strong>
              <ul>
                <li>存储在系统配置文件或环境变量中</li>
                <li>用于加密所有用户的用户主密钥</li>
                <li>即使数据库泄露，没有此密钥也无法还原任何密钥</li>
              </ul>
            </div>
            
            <div style="margin: 15px 0;">
              <strong>第二层：用户主密钥（User Master Key）</strong>
              <ul>
                <li>每个用户注册时随机生成的 256 位密钥</li>
                <li>用于加密该用户所有文件的文件密钥</li>
                <li>使用系统主密钥加密后存储在数据库</li>
              </ul>
            </div>
            
            <div style="margin: 15px 0;">
              <strong>第三层：文件密钥（File Key）</strong>
              <ul>
                <li>每个文件上传时独立生成的随机密钥</li>
                <li>用于加密具体的文件内容</li>
                <li>使用用户主密钥加密后存储在数据库</li>
              </ul>
            </div>
            
            <p><strong>安全优势：</strong></p>
            <ul>
              <li><strong>密钥隔离：</strong> 不同用户的密钥完全相互独立</li>
              <li><strong>最小权限：</strong> 即使攻破一层，仍需其他密钥配合才能解密</li>
              <li><strong>数据隔离：</strong> 数据库泄露无法导致文件内容泄露</li>
            </ul>
          </div>
        </el-collapse-item>
        
        <el-collapse-item title="5. 注销账号后的数据处理" name="5">
          <div class="content-section">
            <p><strong>注销操作会执行：</strong></p>
            <ul>
              <li>永久删除用户账号信息（包括用户名、邮箱等）</li>
              <li>永久删除用户的主密钥（无法恢复任何文件）</li>
              <li>删除数据库中所有文件记录</li>
              <li>删除服务器上所有加密文件</li>
            </ul>
            
            <p><strong>重要提示：</strong></p>
            <el-alert type="error" :closable="false" style="margin-top: 10px;">
              <ul style="margin: 5px 0; padding-left: 20px;">
                <li>账号注销是<strong>不可逆操作</strong></li>
                <li>注销后无法通过任何方式恢复数据</li>
                <li>请在注销前确保已备份重要文件</li>
              </ul>
            </el-alert>
          </div>
        </el-collapse-item>
        
        <el-collapse-item title="6. 登录安全机制" name="6">
          <div class="content-section">
            <p><strong>密码存储安全：</strong></p>
            <ul>
              <li>算法：<code>PBKDF2-HMAC-SHA256</code></li>
              <li>迭代次数：<strong>210,000 次</strong>（符合 OWASP 2023 安全推荐）</li>
              <li>盐值（Salt）：每个用户拥有独立的 16 字节随机盐值，防止彩虹表攻击</li>
              <li>服务器不存储明文密码，只存储哈希值</li>
            </ul>
            
            <p><strong>防时序攻击（Timing Attack）：</strong></p>
            <ul>
              <li>系统在验证密码时使用<strong>常量时间比较</strong>算法</li>
              <li>无论密码正确与否，验证耗时保持一致</li>
              <li>攻击者无法通过测量响应时间来推测密码的任何一位</li>
            </ul>

            <p><strong>会话安全：</strong></p>
            <ul>
              <li>使用 JWT Token 管理会话</li>
              <li>Token 有效期 24 小时</li>
              <li>Token 包含用户ID和用户名，不可伪造</li>
              <li>退出登录后 Token 失效</li>
            </ul>
          </div>
        </el-collapse-item>
        
        <el-collapse-item title="7. 建议的安全实践" name="7">
          <div class="content-section">
            <p><strong>用户侧：</strong></p>
            <ul>
              <li>使用强密码（至少 12 位，包含大小写字母、数字、特殊字符）</li>
              <li>定期更换密码</li>
              <li>不要在不信任的设备上登录</li>
              <li>及时退出登录</li>
              <li>重要文件建议本地备份</li>
            </ul>
            
            <p><strong>系统侧：</strong></p>
            <ul>
              <li>定期更新系统补丁</li>
              <li>监控异常登录行为</li>
              <li>定期备份加密文件</li>
              <li>使用 HTTPS 传输数据</li>
            </ul>
          </div>
        </el-collapse-item>

        <el-collapse-item title="8. 系统使用流程与安全说明" name="8">
          <div class="content-section">
            <p><strong>注册与登录：</strong></p>
            <ul>
              <li>注册时生成用户主密钥（仅用于加密文件密钥）</li>
              <li>登录成功后签发 JWT，用于后续接口鉴权</li>
            </ul>

            <p><strong>上传与加密：</strong></p>
            <ul>
              <li>每个文件生成独立的文件密钥</li>
              <li>使用 <code>AES-256-GCM</code> 加密文件内容</li>
              <li>文件密钥再用用户主密钥加密后入库</li>
              <li>大文件会分块加密，提升安全性与稳定性</li>
            </ul>

            <p><strong>下载与解密：</strong></p>
            <ul>
              <li>下载前必须通过鉴权与权限校验</li>
              <li>服务端解密后返回文件内容</li>
              <li>GCM 认证标签可检测篡改</li>
            </ul>

            <p><strong>预览与访问控制：</strong></p>
            <ul>
              <li>预览也需要登录与鉴权</li>
              <li>超过预览阈值的文件不允许在线预览</li>
            </ul>

            <p><strong>安全性总结：</strong></p>
            <ul>
              <li>文件内容加密存储，服务器只保存密文</li>
              <li>多层密钥保护，降低单点泄露风险</li>
              <li>完整性校验防止文件被篡改</li>
            </ul>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const activeNames = ref(['1']);
</script>

<style scoped>
.content-section {
  line-height: 1.8;
  font-size: 14px;
}

.content-section p {
  margin: 10px 0;
}

.content-section ul {
  margin: 10px 0;
  padding-left: 25px;
}

.content-section li {
  margin: 5px 0;
}

.content-section code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 3px;
  color: #e74c3c;
  font-family: 'Courier New', monospace;
}

.content-section strong {
  color: #303133;
}
</style>
