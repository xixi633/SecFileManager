<template>
  <div class="admin-users-container">
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar-content">
        <h3 class="page-title">用户管理</h3>
        <div class="search-area">
          <el-input
            v-model="searchUsername"
            placeholder="搜索用户名"
            class="search-input"
            clearable
            @clear="loadUsers"
            @keyup.enter="loadUsers"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" :icon="Search" circle @click="loadUsers" />
          <el-button :icon="Refresh" circle @click="loadUsers" />
        </div>
      </div>
    </el-card>
      
    <el-card class="table-card" shadow="never">
      <el-table :data="users" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120">
          <template #default="{ row }">
             <div class="user-cell">
               <el-avatar :size="24" :icon="UserFilled" class="user-avatar-small" />
               <span class="username-text">{{ row.username }}</span>
             </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small" effect="light">
              {{ row.role === 'admin' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusValue(row.status) === 1 ? 'success' : 'danger'" size="small" effect="light">
              {{ getStatusValue(row.status) === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" align="center" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <div v-if="row.role !== 'admin' && row.id !== currentUserId">
              <el-tooltip content="禁用用户" placement="top" v-if="getStatusValue(row.status) === 1">
                <el-button
                  type="warning"
                  size="small"
                  :icon="Lock"
                  @click="toggleStatus(row, 0)"
                >
                  禁用
                </el-button>
              </el-tooltip>
              <el-tooltip content="解禁用户" placement="top" v-else>
                <el-button
                  type="success"
                  size="small"
                  :icon="Unlock"
                  @click="toggleStatus(row, 1)"
                >
                  解禁
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除用户" placement="top">
                <el-button
                  type="danger"
                  size="small"
                  circle
                  :icon="Delete"
                  @click="deleteUser(row)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadUsers"
          @size-change="loadUsers"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search, Refresh, Lock, Unlock, Delete, UserFilled } from '@element-plus/icons-vue';
import api from '../api/index.js';

const users = ref([]);
const loading = ref(false);
const page = ref(1);
const size = ref(10);
const total = ref(0);
const searchUsername = ref('');
const currentUserId = Number(localStorage.getItem('userId') || 0);

const getStatusValue = (status) => {
  const value = Number(status);
  return Number.isNaN(value) ? 0 : value;
};

const loadUsers = async () => {
  loading.value = true;
  try {
    const params = { page: page.value, size: size.value };
    if (searchUsername.value) {
      params.username = searchUsername.value;
    }
    const res = await api.get('/admin/user/list', { params });
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || '加载用户列表失败');
      users.value = [];
      total.value = 0;
      return;
    }
    const data = payload.data;
    users.value = (data.records || []).map((item) => ({
      ...item,
      status: getStatusValue(item.status)
    }));
    total.value = data.total;
  } catch (error) {
    ElMessage.error('加载用户列表失败');
  } finally {
    loading.value = false;
  }
};

const toggleStatus = async (user, nextStatus) => {
  if (user.id === currentUserId) {
    ElMessage.warning('不能禁用自己的账号');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定${nextStatus === 1 ? '解禁' : '禁用'}用户 ${user.username} 吗？`,
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    );
    
    const res = await api.put(`/admin/user/${user.id}/status`, null, {
      params: { status: nextStatus }
    });
    const payload = res?.data;
    if (!payload || payload.code !== 200) {
      ElMessage.error(payload?.message || '操作失败');
      return;
    }
    ElMessage.success('操作成功');
    loadUsers();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败');
    }
  }
};

const deleteUser = async (user) => {
  if (user.id === currentUserId) {
    ElMessage.warning('不能删除自己的账号');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定删除用户 ${user.username} 吗？此操作将同时删除该用户的所有文件！`,
      '警告',
      { type: 'error', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    );
    
    await api.delete(`/admin/user/${user.id}`);
    ElMessage.success('删除成功');
    loadUsers();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};

onMounted(() => {
  loadUsers();
});
</script>

<style scoped>
.admin-users-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}
.toolbar-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.toolbar-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.search-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.search-input {
  width: 250px;
}
.table-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  flex: 1;
  display: flex;
  flex-direction: column;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-avatar-small {
  background: #f0f2f5; 
  color: #909399;
}
.username-text {
  font-weight: 500;
  color: #606266;
}
.status-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.status-dot.active {
  background-color: #67C23A;
}
.status-dot.inactive {
  background-color: #F56C6C;
}
.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
