<template>
  <div class="features-page dark-mode">
    <section id="features" class="features-section">
      <div class="section-container">
        <h2 class="section-title">坚不可摧的底层安全网</h2>
        <p class="section-subtitle">基于学术级与行业标准的加密规范设计，多维度防御潜在威胁</p>
        
        <div class="features-grid">
          <div class="feature-card glass-card">
            <div class="f-icon">🔒</div>
            <h3>AES-256-GCM 核心加密</h3>
            <p>明文数据不落盘，全链路加密保护，抵御任何形式的静态存储窃取分析。遵循 TLS 1.3 默认规范保障您的隐私。</p>
          </div>
          <div class="feature-card glass-card">
            <div class="f-icon">⛓️</div>
            <h3>双重完整性网络</h3>
            <p>基于 GCM Auth Tag 保障密文完整性外，加持 SHA-256 哈希双重校验，自动切断并报警任何恶意的字节篡改尝试。</p>
          </div>
          <div class="feature-card glass-card">
            <div class="f-icon">🔑</div>
            <h3>零信任环境与密钥隔离</h3>
            <p>系统、用户、文件三重密钥互相独立派生。即便数据库遭到脱库或遭受系统管理员越权巡查，依旧无法解密您的单一文件。</p>
          </div>
          <div class="feature-card glass-card">
            <div class="f-icon">🛡️</div>
            <h3>PBKDF2 企业级密码保护</h3>
            <p>采用独立盐值配合 PBKDF2-HMAC-SHA256 算法并执行 210,000 次计算迭代，轻松抵御彩虹表及暴力破解攻击。</p>
          </div>
        </div>
      </div>

      <footer class="footer">
        <div class="footer-content">
          <p class="footer-brand">secFILEmanager</p>
          <p class="footer-copy">© 2026 安全文件管理系统代码演示与架构规范</p>
        </div>
      </footer>
    </section>
  </div>
</template>

<script setup>
</script>

<style scoped>
.features-page {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  color: #f8fafc;
  background: #000000;
  min-height: 100vh;
  margin: 0;
  padding: 0;
}
.features-section {
  min-height: 100vh;
  padding: 60px 20px 40px;
  display: flex;
  flex-direction: column;
}
.section-container {
  max-width: 1000px;
  margin: 0 auto;
  flex: 1;
}
.section-title {
  font-size: 2.5rem;
  font-weight: 700;
  text-align: center;
  margin-bottom: 12px;
}
.section-subtitle {
  text-align: center;
  color: #94a3b8;
  margin-bottom: 60px;
}
.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 24px;
}
.feature-card {
  background: rgba(30, 41, 59, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.05);
  padding: 32px;
  border-radius: 20px;
  text-align: center;
  transition: transform 0.3s ease;
}
.feature-card:hover {
  transform: translateY(-5px);
  background: rgba(30, 41, 59, 0.6);
  border-color: rgba(255,255,255,0.1);
}
.f-icon {
  font-size: 3rem;
  margin-bottom: 20px;
}
.feature-card h3 {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 12px;
}
.feature-card p {
  color: #94a3b8;
  line-height: 1.6;
  font-size: 0.95rem;
}
.footer {
  text-align: center;
  padding: 40px 0 20px;
  margin-top: 60px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}
.footer-brand {
  font-weight: 700;
  margin-bottom: 8px;
}
.footer-copy {
  color: #64748b;
  font-size: 0.85rem;
}
</style>
