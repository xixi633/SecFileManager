import axios from "axios";
import { ElMessage } from "element-plus";

const api = axios.create({
  baseURL: "http://localhost:8080/api",
  timeout: 300000, // 5分钟超时，用于大文件上传
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const message =
      error?.response?.data?.message || error?.message || "请求失败";
    if (status === 401) {
      ElMessage.error("登录已过期，请重新登录");
      localStorage.clear();
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
      return Promise.reject(error);
    }
    ElMessage.error(message);
    return Promise.reject(error);
  }
);

export default api;
