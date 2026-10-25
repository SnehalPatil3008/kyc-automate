import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem("accessToken");

const isPublicAuthRequest =
  config.url?.includes("/api/auth/login") ||
  config.url?.includes("/api/auth/register");

if (token && !isPublicAuthRequest) {
  config.headers.Authorization = `Bearer ${token}`;
}

  return config;
});

export default api;
