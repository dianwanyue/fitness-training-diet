import axios from "axios";

export function apiError(message, details = {}) {
  return Object.assign(new Error(message), details);
}

export function unwrapResponse(response) {
  const body = response.data;
  if (!body || typeof body.code !== "number" || !Object.hasOwn(body, "data")) {
    throw apiError("后端响应格式不正确，请核对 API 地址（应指向 /api/v1）", { status: response.status });
  }
  if (body.code !== 0) {
    throw apiError(body.message || "后端操作失败", {
      code: body.code,
      status: response.status,
      traceId: body.traceId,
    });
  }
  return body.data;
}

export function createApi(baseURL = import.meta.env?.VITE_API_BASE_URL || "/api/v1") {
  const client = axios.create({
    baseURL,
    timeout: 15000,
    headers: { Accept: "application/json" },
  });
  client.interceptors.response.use(unwrapResponse, (cause) => {
    const body = cause.response?.data;
    let message = body?.message;
    if (!message) {
      if (cause.response?.status === 401) message = "登录已失效，请重新登录";
      else if (cause.response?.status === 403) message = "没有权限执行此操作";
      else if (cause.code === "ECONNABORTED") message = "接口请求超时；写入结果可能已生效，请刷新确认后再重试";
      else if (!cause.response) message = "无法连接后端服务，请检查网络及 API 配置；未切换为演示数据";
      else message = `后端请求失败（${cause.response.status}）`;
    }
    return Promise.reject(apiError(message, {
      status: cause.response?.status,
      code: body?.code,
      traceId: body?.traceId,
      cause,
    }));
  });
  return client;
}

export const api = createApi();
