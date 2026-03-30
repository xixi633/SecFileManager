<template>
  <div class="landing-page">
    <!-- 动态液态金属海浪全屏背景层 -->
    <div class="ocean-background liquid-metal" :class="{ 'bg-scrolled': isScrolled }">
      <!-- 动态环境光矩阵 -->
      <div class="ambient-light main-light"></div>
      <div class="ambient-light sub-light"></div>

      <!-- 第一组：向右下倾斜的流体波浪 -->
      <svg class="fullscreen-waves wave-dir-1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 150 180" preserveAspectRatio="none" shape-rendering="auto">
        <defs>
          <linearGradient id="metal-1" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="rgba(125, 211, 252, 0.45)" />
            <stop offset="6%" stop-color="rgba(14, 165, 233, 0.6)" />
            <stop offset="22%" stop-color="rgba(2, 6, 23, 1)" />
            <stop offset="100%" stop-color="rgba(2, 6, 23, 1)" />
          </linearGradient>
          <path id="deep-wave" d="M-160 44c30 0 58-18 88-18s 58 18 88 18 58-18 88-18 58 18 88 18 v120h-352z" />
        </defs>
        <g class="metal-parallax">
          <use xlink:href="#deep-wave" x="48" y="-40" fill="url(#metal-1)" class="w-1" />
          <use xlink:href="#deep-wave" x="48" y="16" fill="url(#metal-1)" class="w-3" />
          <use xlink:href="#deep-wave" x="48" y="72" fill="url(#metal-1)" class="w-5" />
          <use xlink:href="#deep-wave" x="48" y="128" fill="url(#metal-1)" class="w-7" />
        </g>
      </svg>

      <!-- 第二组：向右上倾斜并反向流动的流体波浪，形成纵横交错感 -->
      <svg class="fullscreen-waves wave-dir-2" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 150 180" preserveAspectRatio="none" shape-rendering="auto">
        <defs>
          <linearGradient id="metal-2" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="rgba(186, 230, 253, 0.55)" /> 
            <stop offset="4%" stop-color="rgba(37, 99, 235, 0.7)" />
            <stop offset="18%" stop-color="rgba(2, 6, 23, 1)" />
            <stop offset="100%" stop-color="rgba(2, 6, 23, 1)" />
          </linearGradient>
          <path id="deep-wave-rev" d="M-160 44c30 0 58-18 88-18s 58 18 88 18 58-18 88-18 58 18 88 18 v120h-352z" />
        </defs>
        <g class="metal-parallax-rev">
          <use xlink:href="#deep-wave-rev" x="48" y="-12" fill="url(#metal-2)" class="w-2" />
          <use xlink:href="#deep-wave-rev" x="48" y="44" fill="url(#metal-2)" class="w-4" />
          <use xlink:href="#deep-wave-rev" x="48" y="100" fill="url(#metal-2)" class="w-6" />
        </g>
      </svg>

      <div class="glass-overlay"></div>
    </div>

    <!-- 顶部导航 -->
    <nav class="navbar" :class="{ 'scrolled': isScrolled }">
      <div class="nav-container">
        <div class="nav-left">
          <div class="logo-icon">
            <el-icon :size="24"><Files /></el-icon>
          </div>
          <span class="brand">SecFileManager</span>
        </div>
        <div class="nav-right">
          <div class="nav-tabs">
            <div 
              class="nav-tab-item" 
              :class="{ active: activeTab === 'login' }" 
              @click="switchTab('login')"
            >普通登录</div>
            <div 
              class="nav-tab-item" 
              :class="{ active: activeTab === 'register' }" 
              @click="switchTab('register')"
            >注册账号</div>
            <div 
              class="nav-tab-item" 
              :class="{ active: activeTab === 'admin' }" 
              @click="switchTab('admin')"
            >管理员入口</div>
            <div class="nav-slider" :style="sliderStyle"></div>
          </div>
        </div>
      </div>
    </nav>

    <!-- 全屏页面切换容器 -->
    <div 
      class="fullpage-container" 
      @wheel.prevent="handleWheel" 
      @touchstart="handleTouchStart" 
      @touchmove.prevent="handleTouchMove"
    >
      <div class="fullpage-wrapper" :style="wrapperStyle">
        
        <!-- 首屏：英雄区与表单 -->
        <section class="hero-section">
          <transition :name="transitionName" mode="out-in">
          
          <!-- 登录视图大页 -->
          <div class="hero-layout" v-if="activeTab === 'login'" key="login">
            <div class="hero-text">
              <div class="badge">军工级安全架构 🛡️</div>
              <h1 class="hero-title">保护您珍视的<br/><span class="highlight">每一份数据</span></h1>
              <p class="hero-desc">
                采用 TLS 1.3 标配的 AES-256-GCM 算法。<br/>
                零信任无盲区防线，三层独立密钥架构，确保除了您之外，<br/>
                任何人包括系统管理员都无法窥视您的文件。
              </p>
              <div class="hero-features-list">
                <div class="f-item"><el-icon><Check /></el-icon> 双重完整性校验</div>
                <div class="f-item"><el-icon><Check /></el-icon> 210,000次 PBKDF2 迭代</div>
                <div class="f-item"><el-icon><Check /></el-icon> 数据全程防篡改</div>
              </div>
            </div>
            <div class="hero-form">
              <div class="glass-card form-container">
                <div class="form-panel">
                  <h3 class="panel-title">欢迎回来</h3>
                  <p class="panel-subtitle">登录您的安全空间</p>
                  <el-form :model="loginForm" class="auth-form" size="large" @submit.prevent>
                    <el-form-item>
                      <el-input v-model="loginForm.username" placeholder="请输入用户名" :prefix-icon="User" />
                    </el-form-item>
                    <el-form-item>
                      <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password @keyup.enter="onLogin" />
                    </el-form-item>
                    <div class="forgot-row">
                      <el-button link type="primary" @click="openForgot">忘记密码？</el-button>
                    </div>
                    <el-button type="primary" :loading="loading" class="action-btn primary-btn" @click="onLogin">登 录</el-button>
                  </el-form>
                </div>
              </div>
            </div>
          </div>

          <!-- 注册视图大页 -->
          <div class="hero-layout" v-else-if="activeTab === 'register'" key="register">
            <div class="hero-text">
              <div class="badge">新时代零信任体系 🚀</div>
              <h1 class="hero-title">开启专属您的<br/><span class="highlight">私密文件库</span></h1>
              <p class="hero-desc">
                您的文件仅您可见。从注册这一刻起，系统会为您分配专属隔离盐。<br/>
                请牢记主密码，为了绝对的防线安全，我们无法协助恢复任何加密数据。
              </p>
              <div class="hero-features-list">
                <div class="f-item"><el-icon><Check /></el-icon> 专属随机盐值分配</div>
                <div class="f-item"><el-icon><Check /></el-icon> 密码学强哈希不可逆</div>
              </div>
            </div>
            <div class="hero-form">
              <div class="glass-card form-container">
                <div class="form-panel">
                  <h3 class="panel-title">创建账号</h3>
                  <p class="panel-subtitle">构筑您的专属安全边界</p>
                  <el-form :model="registerForm" class="auth-form" size="large" @submit.prevent>
                    <el-form-item>
                      <el-input v-model="registerForm.username" placeholder="设置用户名" :prefix-icon="User" />
                    </el-form-item>
                    <el-form-item>
                      <el-input v-model="registerForm.password" type="password" placeholder="密码 (8-32位，大小写字母+数字)" :prefix-icon="Lock" show-password />
                    </el-form-item>
                    <el-form-item>
                      <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" :prefix-icon="Lock" show-password />
                    </el-form-item>
                    <el-form-item>
                      <el-input v-model="registerForm.email" type="email" placeholder="邮箱地址（可选）" :prefix-icon="Message" />
                    </el-form-item>
                    <el-button type="primary" :loading="loading" class="action-btn success-btn" @click="onRegister">注 册</el-button>
                  </el-form>
                </div>
              </div>
            </div>
          </div>

          <!-- 管理员视图大页 -->
          <div class="hero-layout" v-else-if="activeTab === 'admin'" key="admin">
            <div class="hero-text">
              <div class="badge">系统最高权限节点 ⚡</div>
              <h1 class="hero-title">全域状态监控<br/><span class="highlight text-danger">全链路安全审计</span></h1>
              <p class="hero-desc">
                此通道受严格访问控制，您的所有管理行为均会被记入不可篡改日志。<br/>
                基于零信任原则限制，管理员同样无法越权访问核心用户存储文件。
              </p>
            </div>
            <div class="hero-form">
              <div class="glass-card form-container">
                <div class="form-panel">
                  <h3 class="panel-title text-danger">系统管理</h3>
                  <p class="panel-subtitle">仅限受权系统管理员访问</p>
                  <div class="admin-warning-box">
                    <el-icon><Warning /></el-icon> 访问将被全局审计和监控
                  </div>
                  <el-form :model="adminForm" class="auth-form" size="large" @submit.prevent>
                    <el-form-item>
                      <el-input v-model="adminForm.username" placeholder="管理员账号" :prefix-icon="UserFilled" />
                    </el-form-item>
                    <el-form-item>
                      <el-input v-model="adminForm.password" type="password" placeholder="安全令牌/密码" :prefix-icon="Lock" show-password @keyup.enter="onAdminLogin" />
                    </el-form-item>
                    <el-button type="danger" :loading="loading" class="action-btn danger-btn" @click="onAdminLogin">授权登录</el-button>
                  </el-form>
                </div>
              </div>
            </div>
          </div>

        </transition>
        
        <div class="scroll-down-hint" @click="scrollToPage(1)" style="cursor: pointer;">
          <span class="hint-text">滚动探索安全特性</span>
          <el-icon class="bounce-icon"><ArrowDown /></el-icon>
        </div>
      </section>

      <!-- 第二屏：底层安全网特性介绍 -->
      <section class="features-section">
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

        <!-- 页脚 -->
        <footer class="footer">
          <div class="footer-content">
            <p class="footer-brand">SecFileManager</p>
            <p class="footer-copy">© 2026 安全文件管理系统代码演示与架构规范</p>
          </div>
        </footer>
      </section>

      </div>
    </div>

    <!-- 忘记密码全屏毛玻璃弹窗 -->
    <el-dialog 
      v-model="forgotVisible" 
      title="找回安全凭证" 
      width="440px" 
      class="frosted-dialog" 
      :close-on-click-modal="false" 
      :append-to-body="false"
      @closed="onCloseForgot"
    >
      <div class="dialog-content">
        <el-steps :active="forgotStep" simple finish-status="success" class="glass-steps">
          <el-step title="账号校验" />
          <el-step title="安全验证" />
          <el-step title="重置" />
        </el-steps>

        <div class="forgot-body" v-if="forgotStep === 1">
          <el-form :model="forgotForm" label-position="top">
            <el-form-item label="登入用户名">
              <el-input v-model="forgotForm.username" placeholder="请输入绑定的用户名" />
            </el-form-item>
            <el-form-item label="关联邮箱">
              <el-input v-model="forgotForm.email" placeholder="请输入系统注册邮箱" />
            </el-form-item>
          </el-form>
        </div>

        <div class="forgot-body" v-else-if="forgotStep === 2">
          <el-form :model="forgotForm" label-position="top">
            <el-form-item label="一次性验证码">
              <el-input v-model="forgotForm.code" placeholder="输入邮箱获取的6位数字" maxlength="6" />
            </el-form-item>
          </el-form>
          <div class="code-actions">
            <el-button class="glass-btn-outline" :disabled="codeCooldown > 0" @click="onSendResetCode">
              {{ codeCooldown > 0 ? `重新下发 (${codeCooldown}s)` : '获取验证码' }}
            </el-button>
          </div>
        </div>

        <div class="forgot-body" v-else>
          <el-form :model="forgotForm" label-position="top">
            <el-form-item label="新密码">
              <el-input v-model="forgotForm.newPassword" type="password" show-password placeholder="8-32位，须含大小写字母与数字" />
            </el-form-item>
            <el-form-item label="二次确认密码">
              <el-input v-model="forgotForm.confirmPassword" type="password" show-password placeholder="再次输入进行确认" />
            </el-form-item>
          </el-form>
        </div>
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button class="glass-btn-text" @click="onCloseForgot">取 消</el-button>
          <el-button v-if="forgotStep === 1" type="primary" class="action-btn primary-btn inline-btn" :loading="forgotLoading" @click="onSendResetCode">核验身份并发送</el-button>
          <el-button v-else-if="forgotStep === 2" type="primary" class="action-btn primary-btn inline-btn" :loading="forgotLoading" @click="onVerifyResetCode">验证</el-button>
          <el-button v-else type="primary" class="action-btn primary-btn inline-btn" :loading="forgotLoading" @click="onConfirmReset">提交重置</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { User, Lock, Message, UserFilled, Files, Warning, Check, ArrowDown } from '@element-plus/icons-vue';
import { login, register, requestPasswordResetCode, verifyPasswordResetCode, confirmPasswordReset } from "../api/auth.js";

const router = useRouter();

// UI States
const isScrolled = ref(false);
const activeTab = ref("login");
const transitionName = ref("slide-left");
const tabIndexMap = { login: 0, register: 1, admin: 2 };

// Nav Slider logic
const sliderStyle = computed(() => {
  const index = tabIndexMap[activeTab.value];
  return {
    transform: `translateX(${index * 100}%)`
  };
});

const switchTab = (tabName) => {
  const currentIndex = tabIndexMap[activeTab.value];
  const nextIndex = tabIndexMap[tabName];
  // Determine slide direction
  transitionName.value = nextIndex > currentIndex ? "slide-left" : "slide-right";
  activeTab.value = tabName;
};

// --- Fullpage Scroll Logic ---
const currentPage = ref(0);
const isAnimating = ref(false);
const totalPages = 2; // 第一页登录，第二页特性介绍

const wrapperStyle = computed(() => {
  return {
    transform: `translateY(-${currentPage.value * 100}vh)`
  };
});

const scrollToPage = (pageIndex) => {
  if (isAnimating.value) return;
  if (pageIndex < 0 || pageIndex >= totalPages) return;
  
  isAnimating.value = true;
  currentPage.value = pageIndex;
  
  // Update nav background styling
  isScrolled.value = pageIndex > 0;

  // Let animation finish before accepting new scroll
  setTimeout(() => {
    isAnimating.value = false;
  }, 800); // match CSS transition duration
};

const handleWheel = (e) => {
  if (Math.abs(e.deltaY) < 30) return; // ignore tiny scrolls
  if (e.deltaY > 0) {
    // scroll down
    scrollToPage(currentPage.value + 1);
  } else {
    // scroll up
    scrollToPage(currentPage.value - 1);
  }
};

let touchStartY = 0;
const handleTouchStart = (e) => {
  touchStartY = e.touches[0].clientY;
};

const handleTouchMove = (e) => {
  if (isAnimating.value) return;
  const touchEndY = e.touches[0].clientY;
  const diff = touchStartY - touchEndY;
  
  if (Math.abs(diff) > 50) { // minimum swipe distance
    if (diff > 0) {
      scrollToPage(currentPage.value + 1);
    } else {
      scrollToPage(currentPage.value - 1);
    }
  }
};
// -----------------------------

// Logic States
const loading = ref(false);
const forgotVisible = ref(false);
const forgotStep = ref(1);
const forgotLoading = ref(false);
const codeCooldown = ref(0);
let codeTimer = null;

const loginForm = ref({ username: "", password: "" });
const adminForm = ref({ username: "", password: "" });
const registerForm = ref({ username: "", password: "", confirmPassword: "", email: "" });
const forgotForm = ref({ username: "", email: "", code: "", newPassword: "", confirmPassword: "" });

// Methods
const onLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning("请输入用户名和密码");
    return;
  }
  loading.value = true;
  try {
    const res = await login(loginForm.value);
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || "登录失败");
      return;
    }
    const data = payload?.data;
    if (!data?.token) {
      ElMessage.error("登录失败：未获取到 token");
      return;
    }
    localStorage.setItem("token", data.token);
    localStorage.setItem("role", data.role || "user");
    localStorage.setItem("username", data.username || "");
    localStorage.setItem("userId", data.userId ?? "");
    router.push("/files");
  } catch (e) {} finally {
    loading.value = false;
  }
};

const onAdminLogin = async () => {
  if (!adminForm.value.username || !adminForm.value.password) {
    ElMessage.warning("请输入管理员账号和密码");
    return;
  }
  loading.value = true;
  try {
    const res = await login(adminForm.value);
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || "登录失败");
      return;
    }
    const data = payload?.data;
    if (!data?.token) {
      ElMessage.error("登录失败：未获取到 token");
      return;
    }
    if (data.role !== "admin") {
      ElMessage.error("非系统权限，尝试被拦截");
      return;
    }
    localStorage.setItem("token", data.token);
    localStorage.setItem("role", data.role);
    localStorage.setItem("username", data.username || "");
    localStorage.setItem("userId", data.userId ?? "");
    router.push("/admin");
  } catch (e) {} finally {
    loading.value = false;
  }
};

const onRegister = async () => {
  const { username, password, confirmPassword, email } = registerForm.value;
  if (!username || !password) return ElMessage.warning("请输入用户名和密码");
  if (password !== confirmPassword) return ElMessage.warning("两次输入的密码不一致");
  if (password.length < 8 || password.length > 32) return ElMessage.warning("密码长度应在8-32位之间");
  if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/.test(password)) return ElMessage.warning("密码必须包含大写、小写字母和数字");
  
  loading.value = true;
  try {
    const res = await register({ username, password, email });
    const payload = res?.data;
    if (!payload || payload.code !== 200) return ElMessage.error(payload?.message || "注册失败");
    ElMessage.success("注册成功，请使用新账号登录");
    switchTab('login');
    loginForm.value.username = username;
    loginForm.value.password = "";
  } catch (e) {} finally {
    loading.value = false;
  }
};

const openForgot = () => {
  forgotVisible.value = true;
  forgotStep.value = 1;
};

const onCloseForgot = () => {
  forgotVisible.value = false;
  setTimeout(() => {
    forgotStep.value = 1;
    forgotForm.value = { username: "", email: "", code: "", newPassword: "", confirmPassword: "" };
    if (codeTimer) { clearInterval(codeTimer); codeTimer = null; }
    codeCooldown.value = 0;
  }, 300);
};

const startCooldown = (seconds) => {
  codeCooldown.value = seconds;
  if (codeTimer) clearInterval(codeTimer);
  codeTimer = setInterval(() => {
    if (codeCooldown.value <= 1) {
      clearInterval(codeTimer);
      codeTimer = null;
      codeCooldown.value = 0;
    } else {
      codeCooldown.value -= 1;
    }
  }, 1000);
};

const onSendResetCode = async () => {
  const username = forgotForm.value.username?.trim();
  const email = forgotForm.value.email?.trim();
  if (!username || !email) return ElMessage.warning("请输入用户名和邮箱");
  if (!/^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/.test(email)) return ElMessage.warning("邮箱格式不正确");
  
  forgotLoading.value = true;
  try {
    const res = await requestPasswordResetCode({ username, email });
    const payload = res?.data;
    if (!payload || payload.code !== 200) return ElMessage.error(payload?.message || "发送验证码请求失败");
    ElMessage.success("动态验证码已发送至您的邮箱");
    forgotStep.value = 2;
    startCooldown(60);
  } catch (e) {} finally {
    forgotLoading.value = false;
  }
};

const onVerifyResetCode = async () => {
  const username = forgotForm.value.username?.trim();
  const email = forgotForm.value.email?.trim();
  const code = forgotForm.value.code?.trim();
  if (!username || !email || !code) return ElMessage.warning("请填写完整信息");
  if (!/^\d{6}$/.test(code)) return ElMessage.warning("请输入6位验证码");
  
  forgotLoading.value = true;
  try {
    const res = await verifyPasswordResetCode({ username, email, code });
    const payload = res?.data;
    if (!payload || payload.code !== 200) return ElMessage.error(payload?.message || "验证拒绝");
    ElMessage.success("环境安全核验通过，请设置新凭据");
    forgotStep.value = 3;
  } catch (e) {} finally {
    forgotLoading.value = false;
  }
};

const onConfirmReset = async () => {
  const username = forgotForm.value.username?.trim();
  const email = forgotForm.value.email?.trim();
  const code = forgotForm.value.code?.trim();
  const newPassword = forgotForm.value.newPassword || '';
  const confirmPassword = forgotForm.value.confirmPassword || '';
  if (!newPassword || !confirmPassword) return ElMessage.warning("新密码不能为空");
  if (newPassword !== confirmPassword) return ElMessage.warning("两次密码输入不一致");
  if (newPassword.length < 8 || newPassword.length > 32) return ElMessage.warning("密码安全性需长达8-32位");
  if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/.test(newPassword)) return ElMessage.warning("组合强度弱：需含大小写字母及数字");
  
  forgotLoading.value = true;
  try {
    const res = await confirmPasswordReset({ username, email, code, newPassword });
    const payload = res?.data;
    if (!payload || payload.code !== 200) return ElMessage.error(payload?.message || "密码重置失败");
    ElMessage.success("密钥库已更新锁定，请进行登入");
    onCloseForgot();
    switchTab('login');
    loginForm.value.username = username;
    loginForm.value.password = "";
  } catch (e) {} finally {
    forgotLoading.value = false;
  }
};
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Syncopate:wght@400;700&family=Inter:wght@300;400;600;800&display=swap');

/* 全局与布局基础 */
.landing-page {
  font-family: 'Inter', system-ui, sans-serif;
  color: #fff;
  height: 100vh;
  width: 100%;
  position: relative;
  overflow: hidden;
}

.fullpage-container {
  height: 100vh;
  width: 100vw;
  overflow: hidden; /* Disable native scroll */
  position: relative;
}

.fullpage-wrapper {
  height: 200vh; /* 2 pages */
  width: 100%;
  transition: transform 0.8s cubic-bezier(0.645, 0.045, 0.355, 1); /* 丝滑过渡 */
  will-change: transform;
}

/* --- 全屏液态金属动态海浪背景层 --- */
.ocean-background {
  position: fixed;
  inset: 0;
  z-index: -1;
  background: #020617; /* 极深的暗蓝色底色保障前面的文字可读性 */
  overflow: hidden;
}

.ambient-light {
  position: absolute;
  border-radius: 50%;
  will-change: transform;
}

/* 环境光在暗处流转，模拟光照在金属液体表面的反光透射 */
.main-light {
  top: -20vh; left: 10vw;
  width: 60vw; height: 60vh;
  background: radial-gradient(circle, rgba(14, 165, 233, 0.25) 0%, rgba(14, 165, 233, 0.05) 50%, transparent 70%);
  animation: lightDrift 18s ease-in-out infinite alternate;
}

.sub-light {
  bottom: -10vh; right: -10vw;
  width: 50vw; height: 50vw;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.2) 0%, rgba(37, 99, 235, 0.05) 50%, transparent 70%);
  animation: lightDrift 22s ease-in-out infinite alternate-reverse;
}

/* 翻页时环境光变深色并移动，营造神秘感 */
.ocean-background.bg-scrolled .main-light {
  background: radial-gradient(circle, rgba(76, 29, 149, 0.25) 0%, transparent 70%);
  transform: translate(20vw, 30vh) scale(1.3);
}

.fullscreen-waves {
  position: absolute;
  width: 150vw; /* 加大覆盖面抵消旋转留边的空白 */
  height: 150vh;
  top: -25vh; left: -25vw;
  transition: all 1.8s cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: center center;
}

/* 构建两组相交的对角斜浪 */
.wave-dir-1 {
  transform: rotate(12deg);
  will-change: transform;
}
.wave-dir-2 {
  transform: rotate(-15deg);
  opacity: 0.85; /* 移除吃显卡的 mix-blend-mode，防止因与毛玻璃冲突导致计算降级和闪烁 */
  will-change: transform;
}

/* 优化滚动时的动画性能，避免巨型 SVG 使用 CSS filter 导致全屏重绘和各种死机乱闪 */
.ocean-background.bg-scrolled .wave-dir-1 {
  transform: rotate(12deg) translateY(-5vh); 
}
.ocean-background.bg-scrolled .wave-dir-2 {
  transform: rotate(-15deg) translateY(-5vh); 
}

.metal-parallax > use {
  animation: move-metal-wave 20s cubic-bezier(0.55, 0.5, 0.45, 0.5) infinite;
}

.metal-parallax-rev > use {
  animation: move-metal-wave-rev 22s cubic-bezier(0.55, 0.5, 0.45, 0.5) infinite;
}

/* 赋予7排重叠波浪不同的速率形成乱序流动的液态感 */
.w-1 { animation-duration: 28s; animation-delay: -3s; }
.w-2 { animation-duration: 24s; animation-delay: -6s; opacity: 0.95; }
.w-3 { animation-duration: 20s; animation-delay: -9s; opacity: 0.9; }
.w-4 { animation-duration: 17s; animation-delay: -12s; opacity: 0.95; }
.w-5 { animation-duration: 14s; animation-delay: -15s; }
.w-6 { animation-duration: 11s; animation-delay: -18s; opacity: 0.95; }
.w-7 { animation-duration: 8s; animation-delay: -21s; }

@keyframes move-metal-wave {
  0% { transform: translate3d(-90px, 0, 0); }
  100% { transform: translate3d(85px, 0, 0); }
}

@keyframes move-metal-wave-rev {
  0% { transform: translate3d(85px, 0, 0); }
  100% { transform: translate3d(-90px, 0, 0); }
}

@keyframes lightDrift {
  0% { transform: translate(0, 0) scale(1) rotate(0deg); }
  100% { transform: translate(15vw, 15vh) scale(1.2) rotate(15deg); }
}

.glass-overlay {
  position: absolute;
  inset: 0;
  background: rgba(2, 6, 23, 0.45); /* 加深压暗全场确保最高安全感和前台高亮对比 */
  /* 移除全屏的 backdrop-filter: blur() 以彻底解决掉帧和卡顿问题 */
  transition: background 1s ease;
}

/* 翻页时通过加深玻璃遮罩层实现环境骤暗，彻底替代耗费性能的底图重绘变色 */
.ocean-background.bg-scrolled .glass-overlay {
  background: rgba(2, 6, 23, 0.75); 
}

/* --- 顶部玻璃态导航栏 --- */
.navbar {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 70px;
  z-index: 100;
  transition: all 0.4s ease;
  border-bottom: 1px solid transparent;
}

.navbar.scrolled {
  background: rgba(10, 12, 16, 0.7);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  background: linear-gradient(135deg, rgba(255,255,255,0.1), rgba(255,255,255,0.02));
  border: 1px solid rgba(255,255,255,0.1);
  width: 40px; height: 40px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}

.brand {
  font-family: 'Syncopate', sans-serif;
  font-weight: 700;
  font-size: 18px;
  letter-spacing: 1px;
}

.nav-tabs {
  display: flex;
  position: relative;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 4px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.nav-tab-item {
  width: 110px; /* 统一宽度解决滑块偏移问题 */
  text-align: center;
  padding: 8px 0;
  font-size: 14px;
  font-weight: 500;
  color: #999;
  cursor: pointer;
  position: relative;
  z-index: 2;
  transition: color 0.3s ease;
}

.nav-tab-item.active {
  color: #fff;
}

.nav-slider {
  position: absolute;
  top: 4px; left: 4px; bottom: 4px;
  width: 110px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  z-index: 1;
  transition: transform 0.4s cubic-bezier(0.25, 1, 0.5, 1);
  box-shadow: 0 2px 10px rgba(0,0,0,0.2);
}

/* --- 首屏区域 --- */
.hero-section {
  height: 100vh;
  padding-top: 70px;
  display: flex;
  flex-direction: column;
  position: relative;
  box-sizing: border-box;
}

/* 扩展过渡域到最大的内容盒子，让整页切动 */
.hero-layout {
  flex: 1;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  display: flex;
  align-items: center;
  padding: 24px;
  gap: 60px;
}

.hero-text {
  flex: 1;
  padding-right: 20px;
}

.badge {
  display: inline-block;
  background: rgba(0, 255, 170, 0.1);
  border: 1px solid rgba(0, 255, 170, 0.3);
  color: #00ffaa;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
  margin-bottom: 24px;
}

.hero-title {
  font-size: 54px;
  font-weight: 800;
  line-height: 1.2;
  margin: 0 0 24px;
}

.highlight {
  background: linear-gradient(135deg, #fff 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-desc {
  font-size: 18px;
  line-height: 1.8;
  color: #a0a5b0;
  margin-bottom: 40px;
}

.hero-features-list {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.f-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #ddd;
  /* 修复毛玻璃在 vue transiton 切换时掉帧或短暂消失的渲染 Bug */
  transform: translateZ(0); 
  -webkit-transform: translateZ(0);
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  will-change: transform;
  background: rgba(255, 255, 255, 0.05);
  padding: 8px 16px;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.03);
}
.f-item .el-icon {
  color: #00ffaa;
}

/* --- 右侧表单区 --- */
.hero-form {
  flex: 0 0 420px;
  position: relative;
}

.glass-card {
  background: rgba(18, 20, 25, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(12px) saturate(120%);
  -webkit-backdrop-filter: blur(12px) saturate(120%);
  border-radius: 24px;
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255,255,255,0.1);
  will-change: transform;
}

.form-container {
  position: relative;
  overflow: hidden;
  height: 480px;
}

.form-panel {
  padding: 40px 32px;
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  display: flex;
  flex-direction: column;
}

.panel-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
}
.panel-subtitle {
  margin: 0 0 24px;
  color: #888;
  font-size: 14px;
}

.text-danger { color: #f87171; }
.admin-warning-box {
  background: rgba(248, 113, 113, 0.1);
  border: 1px solid rgba(248, 113, 113, 0.2);
  color: #f87171;
  padding: 10px;
  border-radius: 8px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.auth-form {
  flex: 1;
}

.auth-form :deep(.el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
  transition: all 0.3s ease;
}
.auth-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.3) inset;
  background-color: rgba(255, 255, 255, 0.05);
}
.auth-form :deep(.el-input__inner) { color: #ffffff; height: 46px; }
.auth-form :deep(.el-input__inner::placeholder) { color: #666; }
.auth-form :deep(.el-input__prefix-inner) { color: #888; }

.forgot-row {
  text-align: right;
  margin-top: -12px;
  margin-bottom: 12px;
}
.forgot-row .el-button { color: #888; font-size: 13px; }
.forgot-row .el-button:hover { color: #fff; }

.action-btn {
  width: 100%;
  height: 50px;
  font-size: 15px;
  border-radius: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  border: none !important;
  transition: all 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
  margin-top: 10px;
}
.action-btn:active { transform: scale(0.98); }

.primary-btn {
  background: linear-gradient(to right, #fff, #e4e4e4) !important;
  color: #000 !important;
}
.primary-btn:hover { box-shadow: 0 10px 20px rgba(255,255,255,0.2); }

.success-btn {
  background: transparent !important;
  border: 1px solid rgba(255,255,255,0.4) !important;
  color: #fff !important;
}
.success-btn:hover { background: rgba(255,255,255,0.1) !important; }

.danger-btn {
  background: rgba(220, 38, 38, 0.15) !important;
  border: 1px solid rgba(220, 38, 38, 0.4) !important;
  color: #fca5a5 !important;
}
.danger-btn:hover { background: rgba(220, 38, 38, 0.25) !important; }

.slide-left-enter-active, .slide-left-leave-active,
.slide-right-enter-active, .slide-right-leave-active {
  transition: transform 0.4s cubic-bezier(0.25, 0.8, 0.25, 1), opacity 0.4s ease;
  will-change: transform, opacity;
}
.slide-left-enter-from { opacity: 0; transform: translateX(40px); }
.slide-left-leave-to { opacity: 0; transform: translateX(-40px); }
.slide-right-enter-from { opacity: 0; transform: translateX(-40px); }
.slide-right-leave-to { opacity: 0; transform: translateX(40px); }

.scroll-down-hint {
  position: absolute;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #888;
  opacity: 0.8;
  z-index: 10;
}
.hint-text { font-size: 12px; font-weight: 500; letter-spacing: 2px; }
.bounce-icon {
  font-size: 20px;
  animation: bounce 2s infinite;
}
@keyframes bounce {
  0%, 20%, 50%, 80%, 100% { transform: translateY(0); }
  40% { transform: translateY(-10px); }
  60% { transform: translateY(-5px); }
}

/* --- 第二屏：特性区块 --- */
.features-section {
  height: 100vh; /* 设为满高，以备滚动捕捉 */
  padding-top: 100px;
  background: transparent;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-sizing: border-box;
}
.section-container {
  max-width: 1200px;
  margin: 0 auto;
  flex: 1; /* 让上面空间填充，底部自压 */
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.section-title {
  font-size: 36px;
  text-align: center;
  margin: 0 0 16px;
  background: linear-gradient(90deg, #fff, #aaa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.section-subtitle {
  text-align: center;
  color: #888;
  font-size: 16px;
  margin-bottom: 60px;
}
.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 30px;
}
.feature-card {
  padding: 32px;
  transition: transform 0.3s;
}
.feature-card:hover {
  transform: translateY(-8px);
}
.f-icon {
  font-size: 40px;
  margin-bottom: 20px;
}
.feature-card h3 {
  font-size: 20px;
  margin: 0 0 12px;
}
.feature-card p {
  color: #a0a0a0;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
}

/* --- 页脚 --- */
.footer {
  padding: 0 24px 24px;
}
.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.footer-brand {
  font-family: 'Syncopate', sans-serif;
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 10px;
  color: #666;
}
.footer-copy {
  color: #555;
  font-size: 12px;
  margin: 0;
}
</style>

<style>
/* 全局隐藏原生多余滚动条，保障视觉纯净 */
::-webkit-scrollbar {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
}
* {
  scrollbar-width: none;
}

/* 蒙层的毛玻璃背景需要全局生效 */
.el-overlay {
  background-color: rgba(0, 0, 0, 0.4) !important;
  backdrop-filter: blur(8px) !important;
}

.frosted-dialog {
  background: rgba(20, 24, 32, 0.65) !important;
  backdrop-filter: blur(30px) saturate(150%) !important;
  border: 1px solid rgba(255, 255, 255, 0.12) !important;
  border-radius: 20px !important;
  box-shadow: 0 30px 60px rgba(0,0,0,0.6) !important;
}

.frosted-dialog .el-dialog__header {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  margin-right: 0;
  padding-bottom: 16px;
}

.frosted-dialog .el-dialog__title {
  color: #ffffff;
  font-weight: 600;
  font-size: 18px;
}

.frosted-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #888;
}

.frosted-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: #fff;
}

.glass-steps {
  background: rgba(255, 255, 255, 0.02) !important;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.05);
}

.glass-steps .el-step__title {
  font-size: 12px !important;
  color: #666;
}

.glass-steps .el-step__title.is-finish { color: #e4e4e4; }
.glass-steps .el-step__title.is-process { color: #00ffaa; }

.frosted-dialog .el-form-item__label {
  color: #a0a0a0 !important;
}

.frosted-dialog .el-input__wrapper {
  background-color: rgba(255, 255, 255, 0.04);
  border-radius: 8px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
}

.frosted-dialog .el-input__wrapper.is-focus {
  background-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.3) inset;
}

.frosted-dialog .el-input__inner { color: #fff; }

.code-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.glass-btn-outline {
  background: transparent !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  color: #ddd !important;
  border-radius: 8px !important;
}
.glass-btn-outline:hover:not(:disabled) {
  border-color: #fff !important;
  color: #fff !important;
  background: rgba(255,255,255,0.05) !important;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 10px;
}

.glass-btn-text {
  background: transparent !important;
  border: none !important;
  color: #888 !important;
  font-weight: 500;
}

.glass-btn-text:hover { color: #fff !important; }

.inline-btn {
  margin-top: 0 !important;
  width: auto !important;
  padding: 0 24px !important;
  height: 40px !important;
  border-radius: 8px !important;
}
</style>
