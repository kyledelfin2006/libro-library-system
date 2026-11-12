import { request } from './client';
import type { LoginRequest, PasswordChangeRequest, SignupRequest, TokenResponse, UserResponse } from './types';

export const login = (universityId: string, password: string) =>
  request<TokenResponse>('POST', '/app/auth/login', { body: { universityId, password } satisfies LoginRequest, authenticated: false });

export const signup = (body: SignupRequest) =>
  request<UserResponse>('POST', '/app/users/signup', { body, authenticated: false });

export const fetchMe = () => request<UserResponse>('GET', '/app/users/me');

export const changePassword = (universityId: string, currentPassword: string, newPassword: string) =>
  request<unknown>('PUT', `/app/users/${encodeURIComponent(universityId)}/password`, {
    body: { currentPassword, newPassword } satisfies PasswordChangeRequest,
  });
