import axios from 'axios';

// Relative routes are proxied by Vite; production hosting must supply equivalent routes.
export const reviewerApi = axios.create({ baseURL: '/review-api/decision' });
export const verificationApi = axios.create({ baseURL: '/review-api/verification' });
export const documentApi = axios.create({ baseURL: '/review-api/document' });
for (const client of [reviewerApi, verificationApi, documentApi]) {
  client.interceptors.request.use(config => {
    if (!config.url?.endsWith('/login')) {
      const token = localStorage.getItem('accessToken');
      if (token) config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });
}
export function requestError(error) {
  if (error.response?.status === 401) return 'Session expired. Log out and sign in again.';
  if (error.response?.status === 403) return 'Access denied by the service.';
  return error.response?.data?.error || 'Service unavailable. Please try again.';
}
