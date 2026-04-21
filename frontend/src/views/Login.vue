<template>
  <div class="landing-page dark-mode">
    <!-- Background layer -->
    <div class="ocean-background liquid-metal">
      <Beams
        :beam-width="2.7"
        :beam-height="23"
        :beam-number="21"
        light-color="#a0bcce"
        :speed="2.1"
        :noise-intensity="1.35"
        :scale="0.21"
        :rotation="38"
        className="full-screen-beams"
      />
    </div>

    <!-- Floating Navigation Bar -->
    <!-- Floating Navigation Bar -->
    <nav class="floating-navbar" :class="{ 'scrolled': isScrolled }">
      <GlassSurface
        width="100%"
        height="100%"
        :borderRadius="30"
        className="nav-glass-surface"
        :displace="0.5"
        :distortionScale="-180"
        :redOffset="0"
        :greenOffset="0"
        :blueOffset="0"
        :brightness="100"
        :opacity="0.05"
        mixBlendMode="normal"
      >
        <div class="nav-container">
          <div class="nav-left">
            <svg class="new-logo-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
              <line x1="9" y1="14" x2="15" y2="14"></line>
            </svg>
            <span class="new-brand">SecFileManager</span>
          </div>
          <div class="nav-right desktop-nav">
            <div class="nav-btn" @click="openAuthModal('admin')">管理员入口</div>
            <div class="nav-btn" @click="openAuthModal('login')">登录</div>
            <div class="nav-btn highlight-register" @click="openAuthModal('register')">注册</div>
          </div>
          <div class="mobile-nav">
            <el-icon size="24" @click="openAuthModal('login')"><User /></el-icon>
          </div>
        </div>
      </GlassSurface>
    </nav>

    <!-- Main Container -->
    <div class="fullpage-container">
      <div class="fullpage-wrapper">
        
        <!-- Hero Section (Centered) -->
        <section class="hero-center-section">
          <div class="hero-content">
            
            <h1 class="hero-title">开启专属您的<br/>私密文件库</h1>
            <p class="hero-description">
              采用 TLS 1.3 标配的 AES-256-GCM 算法。<br/>
              零信任无盲区防线，三层独立密钥架构，确保除了您之外任何人都无法窥视您的文件。
            </p>
            
            <div class="hero-actions">
              <button class="btn-primary" @click="openAuthModal('login')">立即体验</button>
              <button class="btn-ghost" @click="scrollToFeatures">了解更多</button>
            </div>
          </div>
        </section>

        
      </div>
    </div>

    <!-- Auth Modal (Glassmorphism) -->
    <el-dialog 
      v-model="authModalVisible" 
      :title="activeTab === 'login' ? '欢迎回来' : activeTab === 'register' ? '创建账号' : '系统管理'"
      width="440px" 
      class="frosted-dialog modern-auth-dialog" 
      :close-on-click-modal="true" 
      :append-to-body="true"
    >
      <SpotlightCard className="dialog-content auth-dialog-body custom-spotlight" spotlightColor="rgba(255, 255, 255, 0.15)">
        <div class="spotlight-header">
          <h2>{{ activeTab === 'login' ? '欢迎回来' : activeTab === 'register' ? '创建账号' : '系统管理' }}</h2>
          <el-icon class="close-btn" @click="authModalVisible = false"><Close /></el-icon>
        </div>
        <!-- 登录表单 -->
        <el-form v-if="activeTab === 'login'" :model="loginForm" class="auth-form modern-form" size="large" @submit.prevent>
          <p class="auth-subtitle">登录您的安全空间</p>
          <el-form-item>
            <el-input v-model="loginForm.username" placeholder="请输入用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password @keyup.enter="onLogin" />
          </el-form-item>
          <div class="forgot-row">
            <el-button link type="primary" @click="openForgot">忘记密码？</el-button>
          </div>
          <button class="btn-primary full-width" @click="onLogin">登 录</button>
        </el-form>

        <!-- 注册表单 -->
        <el-form v-else-if="activeTab === 'register'" :model="registerForm" class="auth-form modern-form" size="large" @submit.prevent>
          <p class="auth-subtitle">构筑您的专属安全边界</p>
          <el-form-item>
            <el-input v-model="registerForm.username" placeholder="设置用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="registerForm.password" type="password" placeholder="密码 (8-32位验证)" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item>
            <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item>
            <el-input v-model="registerForm.email" type="email" placeholder="邮箱地址（可选）" :prefix-icon="Message" />
          </el-form-item>
          <button class="btn-primary full-width" @click="onRegister">立 即 注 册</button>
        </el-form>

        <!-- 管理员表单 -->
        <el-form v-else-if="activeTab === 'admin'" :model="adminForm" class="auth-form modern-form" size="large" @submit.prevent>
          <p class="auth-subtitle text-danger">仅限受权系统管理员访问</p>
          <div class="admin-warning-box text-center" style="margin-bottom:15px;color:#f56c6c;font-size:12px;">
            <el-icon><Warning /></el-icon> 访问将被全局审计和监控
          </div>
          <el-form-item>
            <el-input v-model="adminForm.username" placeholder="管理员账号" :prefix-icon="UserFilled" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="adminForm.password" type="password" placeholder="安全令牌/密码" :prefix-icon="Lock" show-password @keyup.enter="onAdminLogin" />
          </el-form-item>
          <button class="btn-danger full-width" @click="onAdminLogin">授 权 登 录</button>
        </el-form>
      </SpotlightCard>
    </el-dialog>

    <!-- Forgot password dialog remains untouched -->
    <el-dialog 
      v-model="forgotVisible" 
      title="找回安全凭证" 
      width="440px" 
      class="frosted-dialog modern-auth-dialog" 
      :close-on-click-modal="false" 
      :append-to-body="true"
      @closed="onCloseForgot"
    >
      <SpotlightCard className="dialog-content custom-spotlight" spotlightColor="rgba(255, 255, 255, 0.15)">
        <div class="spotlight-header">
          <h2>找回安全凭证</h2>
          <el-icon class="close-btn" @click="onCloseForgot"><Close /></el-icon>
        </div>
        <el-steps :active="forgotStep" simple finish-status="success" class="glass-steps" style="background:transparent;padding:10px 0;">
          <el-step title="账号" />
          <el-step title="安全" />
          <el-step title="重置" />
        </el-steps>

        <div class="forgot-body modern-form" v-if="forgotStep === 1">
          <el-form :model="forgotForm" label-position="top">
            <el-form-item label="登入用户名">
              <el-input v-model="forgotForm.username" placeholder="请输入绑定的用户名" />
            </el-form-item>
            <el-form-item label="关联邮箱">
              <el-input v-model="forgotForm.email" placeholder="请输入系统注册邮箱" />
            </el-form-item>
          </el-form>
        </div>

        <div class="forgot-body modern-form" v-else-if="forgotStep === 2">
          <el-form :model="forgotForm" label-position="top">
            <el-form-item label="一次性验证码">
              <el-input v-model="forgotForm.code" placeholder="输入邮箱获取的6位数字" maxlength="6" />
            </el-form-item>
          </el-form>
          <div class="code-actions" style="margin-top:10px;">
            <button class="btn-ghost full-width" :disabled="codeCooldown > 0" @click="onSendResetCode">
              {{ codeCooldown > 0 ? `重新下发 (${codeCooldown}s)` : '获取验证码' }}
            </button>
          </div>
        </div>

        <div class="forgot-body modern-form" v-else>
          <el-form :model="forgotForm" label-position="top">
            <el-form-item label="新密码">
              <el-input v-model="forgotForm.newPassword" type="password" show-password placeholder="8-32位，须含大小写字母与数字" />
            </el-form-item>
            <el-form-item label="二次确认密码">
              <el-input v-model="forgotForm.confirmPassword" type="password" show-password placeholder="再次输入进行确认" />
            </el-form-item>
          </el-form>
        </div>
    </SpotlightCard>
      <template #footer>
        <div class="dialog-footer" style="display:flex;gap:10px;">
          <button class="btn-ghost" style="flex:1" @click="onCloseForgot">取 消</button>
          <button v-if="forgotStep === 1" class="btn-primary" style="flex:1" @click="onSendResetCode">校验身份</button>
          <button v-else-if="forgotStep === 2" class="btn-primary" style="flex:1" @click="onVerifyResetCode">验 证</button>
          <button v-else class="btn-primary" style="flex:1" @click="onConfirmReset">提 交</button>
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
import Beams from '../components/Beams.vue';
import GlassSurface from '../components/GlassSurface.vue';
import SpotlightCard from '../components/SpotlightCard.vue';

const router = useRouter();

// UI States
const isScrolled = ref(false);
const activeTab = ref("login");
const authModalVisible = ref(false);
const openAuthModal = (tabName) => { activeTab.value = tabName; authModalVisible.value = true; };
const scrollToFeatures = () => { window.open("/about", "_blank"); };
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

<style>
html, body {
  margin: 0 !important;
  padding: 0 !important;
  width: 100%;
  height: 100%;
  overflow: hidden !important;
  background-color: #000000 !important;
  color: #ffffff;
}

#app {
  margin: 0 !important;
  padding: 0 !important;
  overflow: hidden !important;
  height: 100% !important;
  width: 100% !important;
}
</style>

<style scoped>
/* Base Dark Theme & Typography */
.landing-page {
  position: relative;
  isolation: isolate;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  color: #f8fafc;
  overflow: hidden;
  height: 100vh;
}

/* Background */
.ocean-background {
  position: fixed;
  inset: 0;
  z-index: 0;
  background: #000000;
  overflow: hidden;
  pointer-events: none;
}

.full-screen-beams {
  z-index: 1 !important;
  pointer-events: none;
}

/* Floating Navigation Bar */
.floating-navbar {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  width: 90%;
  max-width: 1000px;
  height: 60px;
  border: none; background: transparent; backdrop-filter: none;
  border-radius: 30px;
  z-index: 100;
  display: flex;
  align-items: center;
  transition: all 0.3s ease;
}

.floating-navbar.scrolled {
  background: rgba(15, 23, 42, 0.7);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
}

.nav-container {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.new-logo-icon {
  width: 26px;
  height: 26px;
  color: #ffffff;
  transition: transform 0.3s ease;
}
.new-logo-icon:hover {
  transform: scale(1.05);
}

.new-brand {
  font-size: 1.3rem;
  font-weight: 700;
  letter-spacing: -0.2px;
  color: #ffffff;
}

.desktop-nav {
  display: flex;
  align-items: center;
  gap: 24px;
}

.nav-btn {
  font-size: 0.95rem;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 6px 12px;
  border-radius: 16px;
}

.nav-btn:hover {
  color: #fff;
}

.nav-btn:last-child {
  background: #ffffff;
  color: #0f172a;
  font-weight: 600;
}
.nav-btn:last-child:hover {
  background: #e2e8f0;
}

.mobile-nav {
  display: none;
  cursor: pointer;
}

@media (max-width: 768px) {
  .desktop-nav { display: none; }
  .mobile-nav { display: flex; }
  .floating-navbar { width: 95%; border-radius: 20px; }
}

/* Main Layout Setup */
.fullpage-container {
  height: 100vh;
  overflow: hidden;
  position: relative;
  z-index: 2;
}

/* Hero Section */
.hero-center-section {
  height: 100vh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  padding: 0 20px;
  position: relative;
  z-index: 10;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255,255,255,0.1);
  padding: 4px 16px 4px 6px;
  border-radius: 30px;
  font-size: 0.85rem;
  color: #cbd5e1;
  margin-bottom: 30px;
}

.badge-tag {
  background: #e2e8f0;
  color: #0f172a;
  padding: 2px 10px;
  border-radius: 20px;
  font-weight: 700;
  font-size: 0.75rem;
}

.hero-title {
  font-size: 4.2rem;
  font-weight: 200;
  line-height: 1.4;
  margin-bottom: 40px;
  letter-spacing: 8px;
  font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  text-shadow: 0 4px 30px rgba(255,255,255,0.15);
}

.hero-description {
  font-size: 1.15rem;
  color: #e2e8f0;
  max-width: 680px;
  margin: 0 auto 60px auto;
  line-height: 2.0;
  font-weight: 300;
  letter-spacing: 2px;
  font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  opacity: 0.9;
}

/* Action Buttons */
.hero-actions {
  display: flex;
  gap: 32px;
  justify-content: center;
}

.btn-primary {
  background: #e2e8f0;
  color: #0f172a;
  border: none;
  font-size: 1rem;
  font-weight: 600;
  padding: 16px 36px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  min-width: 140px;
}
.btn-primary:hover {
  background: #ffffff;
  transform: translateY(-2px);
  box-shadow: 0 10px 20px rgba(0,0,0,0.2);
}

.btn-ghost {
  background: transparent;
  color: #cbd5e1;
  border: 1px solid rgba(255, 255, 255, 0.15);
  font-size: 1rem;
  font-weight: 500;
  padding: 16px 36px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  min-width: 140px;
}
.btn-ghost:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255,255,255,0.3);
  color: #ffffff;
}

.btn-danger {
  background: #dc2626;
  color: #ffffff;
  border: none;
  font-size: 1rem;
  font-weight: 600;
  padding: 16px 36px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.btn-danger:hover {
  background: #ef4444;
}

.full-width {
  width: 100%;
}

/* Features Section (below fold) */
.features-section {
  min-height: 100vh;
  padding: 100px 20px 40px;
  background: #000000;
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

/* Footer */
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

/* Modal / Dialog Refinements */










.auth-subtitle {
  color: #94a3b8;
  margin-bottom: 24px;
  font-size: 0.95rem;
  font-weight: 300;
  letter-spacing: 1px;
}

/* Form Styles Override */


.custom-spotlight {
  /* Restore background and padding so it looks like the card */
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  border-radius: 24px !important;
  background-color: #111 !important;
  padding: 30px !important;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5) !important;
}
.spotlight-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.spotlight-header h2 {
  font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  font-weight: 200;
  letter-spacing: 4px;
  margin: 0;
  color: #ffffff;
  font-size: 1.75rem;
  position: relative;
  z-index: 10;
}
.spotlight-header .close-btn {
  cursor: pointer;
  color: #94a3b8;
  font-size: 1.2rem;
  transition: color 0.3s;
  position: relative;
  z-index: 10;
}
.spotlight-header .close-btn:hover {
  color: #f8fafc;
}


.modern-form :deep(.el-input__wrapper) {

  background: rgba(0, 0, 0, 0.5) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.05) inset !important;
  border-radius: 12px;
}

.modern-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.2) inset !important;
  background: rgba(30, 41, 59, 0.8) !important;
}

.modern-form :deep(.el-input__inner) {
  color: #f8fafc !important;
  height: 44px;
}

.modern-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.forgot-row {
  display: flex;
  justify-content: flex-end;
  margin-top: -10px;
  margin-bottom: 20px;
}

@media (max-width: 768px) {
  .hero-title { font-size: 2.8rem; }
  .features-grid { grid-template-columns: 1fr; }
}



/* Modal Background Blur Effect */

/* transition removed to prevent lag */

.highlight-register {
  background: #ffffff;
  color: #0f172a !important;
  font-weight: 600;
  box-shadow: 0 4px 15px rgba(255,255,255,0.2) !important;
}
.highlight-register:hover {
  background: #e2e8f0;
}

</style>

<style>
body {
  margin: 0 !important;
  padding: 0 !important;
  overflow: hidden !important;
  background: #000000 !important;
}

.el-overlay {
  background-color: rgba(0, 0, 0, 0.6) !important;
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
}

.modern-auth-dialog * {
  font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif !important;
}

.modern-auth-dialog .el-input__inner,
.modern-auth-dialog .el-form-item__label,
.modern-auth-dialog .el-step__title,
.modern-auth-dialog .el-step__description {
  font-weight: 300 !important;
  letter-spacing: 1.5px !important;
}

.modern-auth-dialog a,
.modern-auth-dialog .auth-subtitle {
  font-weight: 300 !important;
}

.modern-auth-dialog .el-input__inner {
  font-size: 1.05rem !important;
}

.modern-auth-dialog button,
.modern-auth-dialog .btn-primary,
.modern-auth-dialog .btn-danger,
.modern-auth-dialog .btn-ghost {
  font-weight: 300 !important;
  letter-spacing: 4px !important;
  font-size: 1.1rem !important;
}

.modern-auth-dialog {
  --el-dialog-bg-color: transparent !important;
  --el-bg-color: transparent !important;
  --el-dialog-box-shadow: none !important;
  --el-dialog-padding-primary: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  border: none !important;
}
.modern-auth-dialog .el-dialog__header {
  display: none !important; /* Hide default header to embed everything inside the SpotlightCard */
}
.modern-auth-dialog .el-dialog__body {
  padding: 0 !important;
}
</style>
