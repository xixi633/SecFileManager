import { ref, onMounted, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import api from '../api/index.js';

export function useUser(userType = 'user') {
  const router = useRouter();
  const displayName = ref('');
  const avatarUrl = ref('');

  function getDisplayName() {
    const storedName = localStorage.getItem('nickname') || localStorage.getItem('username');
    if (storedName) return storedName;
    return userType === 'admin' ? 'admin' : '用户';
  }

  function getAvatarUrl() {
    const version = localStorage.getItem('avatarVersion') || String(Date.now());
    const token = localStorage.getItem('token') || '';
    const suffix = version ? `v=${version}` : '';
    const tokenPart = token ? `token=${encodeURIComponent(token)}` : '';
    const query = [suffix, tokenPart].filter(Boolean).join('&');
    const apiBase = (api.defaults.baseURL || '/api').replace(/\/$/, '');
    return query ? `${apiBase}/user/avatar?${query}` : `${apiBase}/user/avatar`;
  }

  const updateProfileState = () => {
    displayName.value = getDisplayName();
    avatarUrl.value = getAvatarUrl();
  };

  const refreshProfile = async () => {
    try {
      const res = await api.get('/user/profile');
      const payload = res?.data;
      if (!payload || payload.code !== 200) {
        return;
      }
      const data = payload.data;
      if (data?.username) {
        localStorage.setItem('username', data.username);
      }
      if (data?.nickname) {
        localStorage.setItem('nickname', data.nickname);
      }
      if (data?.avatarUrl) {
        localStorage.setItem('avatarVersion', String(Date.now()));
      }
      updateProfileState();
    } catch (e) {
      console.warn('Profile refresh failed:', e);
    }
  };

  const logout = () => {
    localStorage.clear();
    router.push('/login');
  };

  const onProfileUpdatedEvent = () => {
    updateProfileState();
  };

  // Initial load
  updateProfileState();

  onMounted(() => {
    window.addEventListener('profile-updated', onProfileUpdatedEvent);
    refreshProfile();
  });

  onBeforeUnmount(() => {
    window.removeEventListener('profile-updated', onProfileUpdatedEvent);
  });

  return {
    displayName,
    avatarUrl,
    logout,
    refreshProfile
  };
}
