import api from "./index.js";

export function login(payload) {
  return api.post("/user/login", payload);
}

export function register(payload) {
  return api.post("/user/register", payload);
}

export function requestPasswordResetCode(payload) {
  return api.post("/user/password/reset/request", payload);
}

export function verifyPasswordResetCode(payload) {
  return api.post("/user/password/reset/verify", payload);
}

export function confirmPasswordReset(payload) {
  return api.post("/user/password/reset/confirm", payload);
}
