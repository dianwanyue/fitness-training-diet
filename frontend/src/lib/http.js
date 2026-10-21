import axios from "axios";

// Reserved for backend integration. The local repository never silently uses this client.
export const api = axios.create({
  baseURL: import.meta.env?.VITE_API_BASE_URL || "/api",
  timeout: 15000,
  headers: { Accept: "application/json" },
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      error.message =
        "后端会话未授权或已过期（401），请重新登录；本地演示账户不提供后端认证";
    } else if (error.code === "ECONNABORTED") {
      error.message = "接口请求超时，请检查网络或后端服务后重试";
    } else if (!error.response) {
      error.message = "无法连接后端服务，请检查网络及 VITE_API_BASE_URL 配置";
    }
    // Keep the original error/status for callers; never turn an HTTP failure into demo data.
    return Promise.reject(error);
  },
);
