<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <div class="logo-icon">
          <el-icon :size="40" color="#409EFF"><Files /></el-icon>
        </div>
        <h2>安全文件管理系统</h2>
        <p class="subtitle">Secure File Management System</p>
      </div>

      <el-card class="login-card" shadow="hover">
        <el-tabs v-model="activeTab" class="custom-tabs" stretch>
          <el-tab-pane label="用户登录" name="login">
            <el-form :model="loginForm" class="login-form" size="large" @submit.prevent>
              <el-form-item>
                <el-input 
                  v-model="loginForm.username" 
                  placeholder="请输入用户名" 
                  :prefix-icon="User"
                />
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  show-password
                  @keyup.enter="onLogin"
                />
              </el-form-item>
              <div class="forgot-row">
                <el-button link type="primary" @click="openForgot">忘记密码？</el-button>
              </div>
              <div class="form-actions">
                <el-button 
                  type="primary" 
                  :loading="loading" 
                  class="action-btn"
                  @click="onLogin"
                >
                  登 录
                </el-button>
              </div>
            </el-form>
          </el-tab-pane>
          
          <el-tab-pane label="注册账号" name="register">
            <el-form :model="registerForm" class="login-form" size="large" @submit.prevent>
              <el-form-item>
                <el-input 
                  v-model="registerForm.username" 
                  placeholder="设置用户名" 
                  :prefix-icon="User"
                />
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="设置密码（8-32位，含大小写字母+数字）"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  placeholder="确认密码"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-form-item>
                <el-input 
                  v-model="registerForm.email" 
                  type="email" 
                  placeholder="邮箱地址（可选）"
                  :prefix-icon="Message"
                />
              </el-form-item>
              <div class="form-actions">
                <el-button 
                  type="success" 
                  :loading="loading" 
                  class="action-btn"
                  @click="onRegister"
                >
                  注 册
                </el-button>
              </div>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="管理员" name="admin">
            <div class="admin-warning">
              <el-icon><Warning /></el-icon>
              <span>仅限系统管理员访问</span>
            </div>
            <el-form :model="adminForm" class="login-form" size="large" @submit.prevent>
              <el-form-item>
                <el-input 
                  v-model="adminForm.username" 
                  placeholder="管理员账号" 
                  :prefix-icon="UserFilled"
                />
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="adminForm.password"
                  type="password"
                  placeholder="管理员密码"
                  :prefix-icon="Lock"
                  show-password
                  @keyup.enter="onAdminLogin"
                />
              </el-form-item>
              <div class="form-actions">
                <el-button 
                  type="danger" 
                  :loading="loading" 
                  class="action-btn"
                  @click="onAdminLogin"
                >
                  管理员登录
                </el-button>
              </div>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <div class="login-footer">
        <p>© 2024 Security File Manager. All Rights Reserved.</p>
      </div>
    </div>

    <el-dialog v-model="forgotVisible" title="找回密码" width="420px" :close-on-click-modal="false" @closed="onCloseForgot">
      <el-steps :active="forgotStep" simple finish-status="success">
        <el-step title="邮箱校验" />
        <el-step title="验证码" />
        <el-step title="重置密码" />
      </el-steps>

      <div class="forgot-body" v-if="forgotStep === 1">
        <el-form :model="forgotForm" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="forgotForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="forgotForm.email" placeholder="请输入注册邮箱" />
          </el-form-item>
        </el-form>
      </div>

      <div class="forgot-body" v-else-if="forgotStep === 2">
        <el-form :model="forgotForm" label-position="top">
          <el-form-item label="验证码">
            <el-input v-model="forgotForm.code" placeholder="请输入6位验证码" maxlength="6" />
          </el-form-item>
        </el-form>
        <div class="code-actions">
          <el-button :disabled="codeCooldown > 0" @click="onSendResetCode">
            {{ codeCooldown > 0 ? `重新发送(${codeCooldown}s)` : '重新发送验证码' }}
          </el-button>
        </div>
      </div>

      <div class="forgot-body" v-else>
        <el-form :model="forgotForm" label-position="top">
          <el-form-item label="新密码">
            <el-input v-model="forgotForm.newPassword" type="password" show-password placeholder="8-32位，含大小写字母+数字" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="forgotForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="onCloseForgot">取消</el-button>
        <el-button v-if="forgotStep === 1" type="primary" :loading="forgotLoading" @click="onSendResetCode">发送验证码</el-button>
        <el-button v-else-if="forgotStep === 2" type="primary" :loading="forgotLoading" @click="onVerifyResetCode">验证</el-button>
        <el-button v-else type="primary" :loading="forgotLoading" @click="onConfirmReset">重置密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #1f4037 0%, #99f2c8 100%);
  background-size: 400% 400%;
  animation: gradientBG 15s ease infinite;
}

@keyframes gradientBG {
  0% { background-position: 0% 50% }
  50% { background-position: 100% 50% }
  100% { background-position: 0% 50% }
}

.login-box {
  width: 100%;
  max-width: 440px;
  padding: 20px;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
  color: white;
  text-shadow: 0 2px 4px rgba(0,0,0,0.2);
}

.logo-icon {
  background: white;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 15px;
  box-shadow: 0 4px 15px rgba(0,0,0,0.1);
}

.login-header h2 {
  font-size: 28px;
  margin: 0 0 5px;
  font-weight: 600;
  letter-spacing: 1px;
}

.subtitle {
  margin: 0;
  opacity: 0.9;
  font-size: 14px;
  font-weight: 300;
}

.login-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 10px 30px rgba(0,0,0,0.15) !important;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
}

.custom-tabs :deep(.el-tabs__header) {
  margin-bottom: 25px;
}

.custom-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  height: 45px;
}

.login-form {
  padding: 10px 20px;
}

.form-actions {
  margin-top: 30px;
}

.forgot-row {
  display: flex;
  justify-content: flex-end;
  margin-top: -8px;
  margin-bottom: 6px;
}

.action-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 6px;
  letter-spacing: 2px;
}

.admin-warning {
  background: #fdf6ec;
  color: #e6a23c;
  padding: 10px;
  border-radius: 4px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  justify-content: center;
}

.login-footer {
  text-align: center;
  margin-top: 25px;
  color: rgba(255,255,255,0.8);
  font-size: 12px;
}

.forgot-body {
  margin-top: 16px;
}

.code-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}
</style>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { User, Lock, Message, UserFilled, Files, Warning } from '@element-plus/icons-vue';
import { login, register, requestPasswordResetCode, verifyPasswordResetCode, confirmPasswordReset } from "../api/auth.js";

const router = useRouter();
const loading = ref(false);
const activeTab = ref("login");
const forgotVisible = ref(false);
const forgotStep = ref(1);
const forgotLoading = ref(false);
const codeCooldown = ref(0);
let codeTimer = null;

const loginForm = ref({
  username: "",
  password: "",
});

const adminForm = ref({
  username: "",
  password: "",
});

const registerForm = ref({
  username: "",
  password: "",
  confirmPassword: "",
  email: "",
});

const forgotForm = ref({
  username: "",
  email: "",
  code: "",
  newPassword: "",
  confirmPassword: "",
});

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
    const token = data?.token;
    const role = data?.role;
    if (!token) {
      ElMessage.error("登录失败：未获取到 token");
      return;
    }
    localStorage.setItem("token", token);
    localStorage.setItem("role", role || "user");
    localStorage.setItem("username", data?.username || "");
    localStorage.setItem("userId", data?.userId ?? "");
    
    router.push("/files");
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false;
  }
};

const onAdminLogin = async () => {
  if (!adminForm.value.username || !adminForm.value.password) {
    ElMessage.warning("请输入用户名和密码");
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
    const token = data?.token;
    const role = data?.role;
    if (!token) {
      ElMessage.error("登录失败：未获取到 token");
      return;
    }
    
    // 检查是否是管理员
    if (role !== "admin") {
      ElMessage.error("非管理员账号，请使用普通登录");
      return;
    }
    
    localStorage.setItem("token", token);
    localStorage.setItem("role", role);
    localStorage.setItem("username", data?.username || "");
    localStorage.setItem("userId", data?.userId ?? "");
    router.push("/admin");
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false;
  }
};

const onRegister = async () => {
  const { username, password, confirmPassword, email } = registerForm.value;
  
  if (!username || !password) {
    ElMessage.warning("请输入用户名和密码");
    return;
  }
  
  if (password !== confirmPassword) {
    ElMessage.warning("两次输入的密码不一致");
    return;
  }
  
  if (password.length < 8 || password.length > 32) {
    ElMessage.warning("密码长度应在8-32位之间");
    return;
  }

  const passwordRule = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/;
  if (!passwordRule.test(password)) {
    ElMessage.warning("密码必须包含大写字母、小写字母和数字");
    return;
  }
  
  loading.value = true;
  try {
    const res = await register({ username, password, email });
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || "注册失败");
      return;
    }
    ElMessage.success("注册成功，请登录");
    activeTab.value = "login";
    loginForm.value.username = username;
    loginForm.value.password = "";
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false;
  }
};

const openForgot = () => {
  forgotVisible.value = true;
  forgotStep.value = 1;
};

const onCloseForgot = () => {
  forgotVisible.value = false;
  forgotStep.value = 1;
  forgotForm.value = { username: "", email: "", code: "", newPassword: "", confirmPassword: "" };
  if (codeTimer) {
    clearInterval(codeTimer);
    codeTimer = null;
  }
  codeCooldown.value = 0;
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
  if (!username || !email) {
    ElMessage.warning("请输入用户名和邮箱");
    return;
  }
  const emailRule = /^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/;
  if (!emailRule.test(email)) {
    ElMessage.warning("邮箱格式不正确");
    return;
  }
  forgotLoading.value = true;
  try {
    const res = await requestPasswordResetCode({ username, email });
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || "发送失败");
      return;
    }
    ElMessage.success("验证码已发送");
    forgotStep.value = 2;
    startCooldown(60);
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    forgotLoading.value = false;
  }
};

const onVerifyResetCode = async () => {
  const username = forgotForm.value.username?.trim();
  const email = forgotForm.value.email?.trim();
  const code = forgotForm.value.code?.trim();
  if (!username || !email || !code) {
    ElMessage.warning("请输入邮箱和验证码");
    return;
  }
  if (!/^\d{6}$/.test(code)) {
    ElMessage.warning("验证码格式不正确");
    return;
  }
  forgotLoading.value = true;
  try {
    const res = await verifyPasswordResetCode({ username, email, code });
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || "验证失败");
      return;
    }
    ElMessage.success("验证通过，请设置新密码");
    forgotStep.value = 3;
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    forgotLoading.value = false;
  }
};

const onConfirmReset = async () => {
  const username = forgotForm.value.username?.trim();
  const email = forgotForm.value.email?.trim();
  const code = forgotForm.value.code?.trim();
  const newPassword = forgotForm.value.newPassword || '';
  const confirmPassword = forgotForm.value.confirmPassword || '';
  if (!newPassword || !confirmPassword) {
    ElMessage.warning("请输入新密码");
    return;
  }
  if (newPassword !== confirmPassword) {
    ElMessage.warning("两次输入的密码不一致");
    return;
  }
  if (newPassword.length < 8 || newPassword.length > 32) {
    ElMessage.warning("密码长度应在8-32位之间");
    return;
  }
  const passwordRule = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/;
  if (!passwordRule.test(newPassword)) {
    ElMessage.warning("密码必须包含大写字母、小写字母和数字");
    return;
  }
  forgotLoading.value = true;
  try {
    const res = await confirmPasswordReset({ username, email, code, newPassword });
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || "重置失败");
      return;
    }
    ElMessage.success("密码已更新，请使用新密码登录");
    onCloseForgot();
    activeTab.value = "login";
    loginForm.value.username = username;
    loginForm.value.password = "";
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    forgotLoading.value = false;
  }
};
</script>
