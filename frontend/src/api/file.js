import api from "./index.js";

export function checkChunk(identifier, chunkNumber) {
  return api.get("/file/chunk/check", {
    params: { identifier, chunkNumber }
  });
}

export function fetchChunkSessionStatus(identifier) {
  return api.get("/file/chunk/session/status", {
    params: { identifier }
  });
}

export function resetChunkSession(identifier) {
  return api.post("/file/chunk/session/reset", null, {
    params: { identifier }
  });
}

export function uploadChunk(formData, onUploadProgress, signal) {
  return api.post("/file/chunk/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    onUploadProgress,
    signal
  });
}

export function mergeChunks(identifier, filename, totalChunks, totalSize) {
  return api.post("/file/chunk/merge", {
    identifier,
    filename,
    totalChunks,
    totalSize
  });
}

export function fetchFileList(page = 1, size = 10, filters = {}) {
  return api.get("/file/list", {
    params: { page, size, ...filters }
  });
}

export function uploadFile(formData, description, onUploadProgress, isFolder, signal) {
  const params = {};
  if (description) params.description = description;
  if (typeof isFolder !== "undefined") params.isFolder = isFolder;
  return api.post("/file/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    params,
    timeout: 600000,
    onUploadProgress: onUploadProgress,
    signal
  });
}

export function downloadFile(fileId) {
  return api.get(`/file/download/${fileId}`, {
    responseType: "blob",
  });
}

export function downloadFolder(fileId) {
  return api.get(`/file/download-folder/${fileId}`, {
    responseType: "blob",
  });
}

export function deleteFile(fileId) {
  return api.delete(`/file/${fileId}`);
}

export function updateFileDescription(fileId, description) {
  return api.put(`/file/description/${fileId}`, null, {
    params: { description }
  });
}

export function browseFolder(fileId) {
  return api.get(`/file/browse/${fileId}`);
}

export function fetchFolderEntries(fileId) {
  return api.get(`/file/folder/${fileId}/entries`);
}

export function fetchPreviewConfig() {
  return api.get('/file/preview/config');
}

export function previewFolderEntry(fileId, path) {
  return api.get(`/file/folder/preview/${fileId}`, {
    params: { path },
    responseType: "arraybuffer"
  });
}

export function downloadFolderEntry(fileId, path) {
  return api.get(`/file/folder/download/${fileId}`, {
    params: { path },
    responseType: "blob"
  });
}

export function deleteFolderEntry(fileId, path) {
  return api.delete(`/file/folder/entry/${fileId}`, {
    params: { path }
  });
}

export function fetchRecycleList(page = 1, size = 10) {
  return api.get("/file/recycle/list", {
    params: { page, size }
  });
}

export function restoreFile(fileId) {
  return api.post(`/file/recycle/restore/${fileId}`);
}

export function deleteFilePermanently(fileId) {
  return api.delete(`/file/recycle/${fileId}`);
}
