import axios from "axios";
import { ElMessage } from "element-plus";

const runtimeApiBase =
  import.meta.env.VITE_API_BASE_URL ||
  `${window.location.protocol}//${window.location.hostname}:8080/api`;

const api = axios.create({
  baseURL: runtimeApiBase,
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
    if (error?.code === "ERR_CANCELED" || error?.name === "CanceledError") {
      return Promise.reject(error);
    }
    const status = error?.response?.status;
    const errorCode = error?.response?.data?.errorCode;
    const serverMessage = error?.response?.data?.message;
    const errorCodeMessageMap = {
      UPLOAD_SESSION_EXPIRED: "上传会话已过期，请重新上传",
      UPLOAD_CHUNK_OUT_OF_ORDER: "分片顺序异常，请重试上传",
      UPLOAD_NOT_FINISHED: "分片尚未上传完成，请稍后再试",
      PREVIEW_DECRYPT_FAILED: "预览解密失败，请下载后查看",
      FILE_HASH_MISMATCH: "文件完整性校验失败，请重新上传",
    };
    const message =
      (errorCode && errorCodeMessageMap[errorCode]) ||
      serverMessage ||
      error?.message ||
      "请求失败";
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
