import { accessToken } from './auth';
import { gatewayRequest } from './api';

export async function request(path, method = 'GET', data) {
  let response;
  try {
    response = await gatewayRequest({
      path,
      method,
      body: data === undefined ? '' : JSON.stringify(data),
      getToken: accessToken,
    });
  } catch (error) {
    throw new Error(
      error.name === 'TimeoutError' || error instanceof TypeError
        ? 'We could not reach the service. Please try again shortly.'
        : error.message
    );
  }
  if (!response.ok) {
    const messages = {
      400: 'The request could not be completed. Check your details. If paying, a successful payment may already exist.',
      409: 'This action conflicts with the current order state or cart. Refresh and try again.',
      401: 'Your session is no longer valid. Please sign in again.',
      403: 'You do not have access to this order or action.',
      404: 'We could not find this order.',
      502: 'A required service is unavailable. Please try again shortly.',
      503: 'A required service is unavailable. Please try again shortly.',
    };
    const error = new Error(messages[response.status] || 'Something went wrong. Please try again.');
    error.status = response.status;
    throw error;
  }
  return response.data;
}
export const money = (value) =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value || 0));
export const date = (value) =>
  value
    ? new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
    : '—';
export const statuses = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'FAILED'];
export const label = (status) =>
  status ? status.charAt(0) + status.slice(1).toLowerCase() : 'Unknown';
export const newest = (orders) =>
  [...orders].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt) || b.id - a.id);
