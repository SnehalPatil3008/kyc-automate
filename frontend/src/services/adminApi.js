import axios from "axios";

export const adminAuthApi = axios.create({
  baseURL: "/admin-api/auth",
});

export const adminAuditApi = axios.create({
  baseURL: "/admin-api/audit",
});

adminAuditApi.interceptors.request.use((config) => {
  const token = sessionStorage.getItem("accessToken");

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

export function adminRequestError(error) {
  if (error.response?.status === 401) {
    return "Your admin session expired. Sign in again.";
  }

  if (error.response?.status === 403) {
    return "You do not have permission to access this admin resource.";
  }

  return error.response?.data?.error
    || error.response?.data?.message
    || "The audit service is unavailable. Please try again.";
}
