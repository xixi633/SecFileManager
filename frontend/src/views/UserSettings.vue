<template>
  <div class="settings-container">
    <el-card class="card" shadow="never">
      <template #header>
        <div class="card-header">
          <h3 class="card-title">个人资料</h3>
        </div>
      </template>

      <div class="profile-section">
        <div class="avatar-block">
          <el-avatar :size="80" :src="avatarObjectUrl" :icon="UserFilled" class="avatar" />
          <el-upload
            :show-file-list="false"
            :http-request="handleAvatarUpload"
            :before-upload="beforeAvatarUpload"
          >
            <el-button type="primary" :loading="avatarUploading">上传头像</el-button>
          </el-upload>
          <div class="hint">支持 jpg/png/gif/webp，大小不超过 2MB</div>
        </div>

        <el-form :model="profileForm" label-width="100px" class="profile-form">
          <el-form-item label="用户名">
            <el-input v-model="profileForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="用户ID">
            <el-input :model-value="userId" disabled />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveProfile">保存资料</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <el-card class="card" shadow="never">
      <template #header>
        <div class="card-header">
          <h3 class="card-title">修改密码</h3>
        </div>
      </template>

      <el-form :model="passwordForm" label-width="120px" class="password-form">
        <el-form-item label="原密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="savePassword">更新密码</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="card" shadow="never">
      <template #header>
        <h3 class="card-title">存储路径说明</h3>
      </template>

      <el-alert
        title="关于文件存储路径"
        type="info"
        :closable="false"
        class="storage-alert"
      >
        <div class="storage-content">
          <p><strong>上传文件保存路径：</strong></p>
          <ul>
            <li>由服务器端配置决定</li>
            <li>当前路径：<code>{{ currentStoragePath }}</code></li>
            <li>需要修改请联系系统管理员</li>
          </ul>

          <p><strong>下载文件保存路径：</strong></p>
          <ul>
            <li>可选择浏览器可访问的本地目录（需授权）</li>
            <li>不支持的浏览器将使用默认下载路径</li>
          </ul>
          <div class="download-dir">
            <el-tag type="info" v-if="downloadDirName">已设置目录：{{ downloadDirName }}</el-tag>
            <el-tag type="warning" v-else>未设置目录</el-tag>
          </div>
          <div class="download-actions">
            <el-button type="primary" @click="chooseDownloadDir" :disabled="!fsSupported">选择保存目录</el-button>
            <el-button @click="clearDownloadDir" :disabled="!downloadDirName">清除目录</el-button>
          </div>
          <div class="download-hint">
            {{ fsSupported ? '目录权限仅用于写入下载文件' : '当前浏览器不支持目录写入' }}
          </div>
        </div>
      </el-alert>
    </el-card>

    <el-card class="card" shadow="never">
      <template #header>
        <h3 class="card-title">账号管理</h3>
      </template>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">
          {{ username }}
        </el-descriptions-item>
        <el-descriptions-item label="用户ID">
          {{ userId }}
        </el-descriptions-item>
        <el-descriptions-item label="账号状态">
          <el-tag type="success">正常</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <div>
        <h4>账号注销</h4>
        <el-alert
          title="警告：注销账号是不可逆操作"
          type="warning"
          :closable="false"
          class="delete-alert"
        >
          <ul>
            <li>您的账号将被永久删除</li>
            <li>所有已上传的文件将被永久删除</li>
            <li>此操作无法撤销或恢复</li>
          </ul>
        </el-alert>

        <el-button type="danger" @click="confirmDeleteAccount">
          注销账号
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UserFilled } from '@element-plus/icons-vue';
import api from '../api/index.js';
import {
  isFileSystemAccessSupported,
  saveDirectoryHandle,
  getDirectoryHandle,
  clearDirectoryHandle,
  verifyPermission
} from '../utils/fileSystem.js';

const router = useRouter();
const username = ref(localStorage.getItem('username') || '用户');
const userId = ref(localStorage.getItem('userId') || '-');
const currentStoragePath = ref('D:\\projects\\SecFileManager\\storage');
const downloadDirName = ref('');
const fsSupported = isFileSystemAccessSupported();

const profileForm = ref({
  username: '',
  nickname: '',
  email: ''
});

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const avatarObjectUrl = ref('');
const avatarUploading = ref(false);

const refreshDownloadDir = async () => {
  if (!fsSupported) return;
  const handle = await getDirectoryHandle();
  if (!handle) {
    downloadDirName.value = '';
    return;
  }
  const ok = await verifyPermission(handle, 'readwrite');
  downloadDirName.value = ok ? (handle.name || '已授权目录') : '';
};

const chooseDownloadDir = async () => {
  if (!fsSupported) {
    ElMessage.warning('当前浏览器不支持本地目录写入');
    return;
  }
  try {
    const handle = await window.showDirectoryPicker();
    const ok = await verifyPermission(handle, 'readwrite');
    if (!ok) {
      ElMessage.error('未获得目录写入权限');
      return;
    }
    await saveDirectoryHandle(handle);
    downloadDirName.value = handle.name || '已授权目录';
    ElMessage.success('保存目录已设置');
  } catch (e) {
    // 用户取消
  }
};

const clearDownloadDir = async () => {
  await clearDirectoryHandle();
  downloadDirName.value = '';
  ElMessage.success('已清除保存目录');
};

const loadProfile = async () => {
  try {
    const res = await api.get('/user/profile');
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || '加载资料失败');
      return;
    }
    const data = payload.data;
    profileForm.value.username = data.username || '';
    profileForm.value.nickname = data.nickname || '';
    profileForm.value.email = data.email || '';
    if (data.username) {
      localStorage.setItem('username', data.username);
      username.value = data.username;
    }
    if (data.avatarUrl) {
      localStorage.setItem('avatarVersion', String(Date.now()));
    }
    window.dispatchEvent(new Event('profile-updated'));
    if (data.nickname) {
      localStorage.setItem('nickname', data.nickname);
    }
    await loadAvatar();
  } catch (e) {
    // 错误已由拦截器提示
  }
};

const loadAvatar = async () => {
  if (avatarObjectUrl.value) {
    URL.revokeObjectURL(avatarObjectUrl.value);
    avatarObjectUrl.value = '';
  }
  try {
    const res = await api.get('/user/avatar', { responseType: 'blob' });
    const blob = res?.data;
    if (!blob || blob.size === 0) return;
    avatarObjectUrl.value = URL.createObjectURL(blob);
  } catch (e) {
    // 没有头像时忽略
  }
};

const beforeAvatarUpload = (file) => {
  const isImage = file.type.startsWith('image/');
  if (!isImage) {
    ElMessage.warning('只能上传图片文件');
    return false;
  }
  const isLt2M = file.size / 1024 / 1024 < 2;
  if (!isLt2M) {
    ElMessage.warning('头像大小不能超过2MB');
    return false;
  }
  return true;
};

const handleAvatarUpload = async (options) => {
  avatarUploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', options.file);
    const res = await api.post('/user/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || '头像更新失败');
      return;
    }
    ElMessage.success('头像已更新');
    localStorage.setItem('avatarVersion', String(Date.now()));
    window.dispatchEvent(new Event('profile-updated'));
    await loadAvatar();
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    avatarUploading.value = false;
  }
};

const saveProfile = async () => {
  const newUsername = profileForm.value.username?.trim() || '';
  const nickname = profileForm.value.nickname?.trim() || '';
  const email = profileForm.value.email?.trim() || '';
  const emailRule = /^$|^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/;
  if (email && !emailRule.test(email)) {
    ElMessage.warning('邮箱格式不正确');
    return;
  }
  if (newUsername.length < 3 || newUsername.length > 20) {
    ElMessage.warning('用户名长度应在3-20个字符之间');
    return;
  }
  if (!/^[a-zA-Z0-9_]+$/.test(newUsername)) {
    ElMessage.warning('用户名只能包含字母、数字和下划线');
    return;
  }
  if (nickname.length > 50) {
    ElMessage.warning('昵称长度不能超过50个字符');
    return;
  }
  const currentUsername = username.value || '';
  let confirmPassword = '';
  if (newUsername !== currentUsername) {
    try {
      const result = await ElMessageBox.prompt(
        '请输入当前密码以确认修改用户名',
        '确认密码',
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          inputType: 'password',
          inputPlaceholder: '请输入当前密码',
          inputPattern: /\S+/,
          inputErrorMessage: '密码不能为空'
        }
      );
      confirmPassword = result.value || '';
    } catch (error) {
      return;
    }
  }
  try {
    const res = await api.put('/user/profile', {
      username: newUsername,
      nickname,
      email,
      confirmPassword
    });
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || '更新失败');
      return;
    }
    ElMessage.success('资料已更新');
    if (newUsername) {
      localStorage.setItem('username', newUsername);
      username.value = newUsername;
    }
    if (nickname) {
      localStorage.setItem('nickname', nickname);
    } else {
      localStorage.removeItem('nickname');
    }
    window.dispatchEvent(new Event('profile-updated'));
  } catch (e) {
    // 错误已由拦截器提示
  }
};

const savePassword = async () => {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value;
  if (!oldPassword || !newPassword || !confirmPassword) {
    ElMessage.warning('请完整填写密码信息');
    return;
  }
  if (newPassword !== confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致');
    return;
  }
  if (newPassword.length < 8 || newPassword.length > 32) {
    ElMessage.warning('密码长度应在8-32位之间');
    return;
  }
  const passwordRule = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/;
  if (!passwordRule.test(newPassword)) {
    ElMessage.warning('密码必须包含大写字母、小写字母和数字');
    return;
  }
  try {
    const res = await api.put('/user/password', { oldPassword, newPassword });
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || '密码更新失败');
      return;
    }
    ElMessage.success('密码已更新，请重新登录');
    localStorage.clear();
    router.push('/login');
  } catch (e) {
    // 错误已由拦截器提示
  }
};

const confirmDeleteAccount = async () => {
  try {
    await ElMessageBox.confirm(
      '您确定要注销账号吗？此操作将永久删除您的账号和所有文件，且无法恢复。',
      '确认注销账号',
      {
        confirmButtonText: '确定注销',
        cancelButtonText: '取消',
        type: 'error',
        confirmButtonClass: 'el-button--danger'
      }
    );

    await ElMessageBox.prompt(
      '请输入您的用户名以确认注销',
      '二次确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: new RegExp(`^${username.value}$`),
        inputErrorMessage: '用户名不正确'
      }
    );

    await deleteAccount();
  } catch (error) {
    // 用户取消操作
  }
};

const deleteAccount = async () => {
  try {
    const res = await api.delete('/user/account');
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || '账号注销失败');
      return;
    }
    ElMessage.success('账号已注销');
    localStorage.clear();
    router.push('/login');
  } catch (error) {
    ElMessage.error('账号注销失败');
  }
};

onMounted(() => {
  refreshDownloadDir();
  loadProfile();
});

onBeforeUnmount(() => {
  if (avatarObjectUrl.value) {
    URL.revokeObjectURL(avatarObjectUrl.value);
  }
});
</script>

<style scoped>
.settings-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.profile-section {
  display: flex;
  gap: 32px;
  align-items: flex-start;
  flex-wrap: wrap;
}
.avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  min-width: 160px;
}
.avatar {
  background-color: #f0f2f5;
}
.hint {
  font-size: 12px;
  color: #909399;
}
.profile-form {
  flex: 1;
  min-width: 280px;
}
.password-form {
  max-width: 520px;
}
.storage-alert {
  margin-bottom: 0;
}
.storage-content ul {
  margin: 6px 0 12px;
  padding-left: 20px;
}
.storage-content code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 3px;
  color: #e74c3c;
  font-family: 'Courier New', monospace;
}
.download-dir {
  margin-top: 10px;
}
.download-actions {
  margin-top: 10px;
  display: flex;
  gap: 10px;
}
.download-hint {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}
.delete-alert {
  margin-bottom: 15px;
}
.delete-alert ul {
  margin: 10px 0;
  padding-left: 20px;
}
</style>
