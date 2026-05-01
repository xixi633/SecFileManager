export const TYPE_CATEGORY_OPTIONS = [
  { label: "全部", value: "all" },
  { label: "目录", value: "folder" },
  { label: "图片", value: "image" },
  { label: "视频", value: "video" },
  { label: "音频", value: "audio" },
  { label: "文本", value: "text" },
  { label: "代码", value: "code" },
  { label: "压缩包", value: "archive" },
  { label: "文档", value: "document" },
  { label: "其他", value: "other" },
];

const CATEGORY_LABEL_MAP = {
  folder: "目录",
  image: "图片",
  video: "视频",
  text: "文本",
  code: "代码",
  audio: "音频",
  archive: "压缩包",
  document: "文档",
  other: "其他",
};

const IMAGE_EXTS = new Set(["jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"]);
const VIDEO_EXTS = new Set(["mp4", "avi", "mov", "wmv", "mkv", "webm", "m4v"]);
const AUDIO_EXTS = new Set(["mp3", "wav", "flac", "aac", "ogg", "m4a"]);
const TEXT_EXTS = new Set(["txt", "md", "rtf", "log", "ini", "conf", "yaml", "yml", "json", "xml"]);
const CODE_EXTS = new Set(["java", "js", "ts", "jsx", "tsx", "py", "go", "c", "cpp", "h", "hpp", "cs", "php", "rb", "rs", "kt", "swift", "vue", "html", "css", "scss", "sql", "sh", "bat", "ps1"]);
const ARCHIVE_EXTS = new Set(["zip", "rar", "7z", "tar", "gz", "bz2", "xz"]);
const DOCUMENT_EXTS = new Set(["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv"]);

const normalizeExt = (filename = "") => {
  const normalizedName = String(filename).toLowerCase();
  const idx = normalizedName.lastIndexOf(".");
  if (idx < 0 || idx === normalizedName.length - 1) {
    return "";
  }
  return normalizedName.slice(idx + 1);
};

export const getFileTypeCategory = (row = {}) => {
  if (row.isDirectory === true || row.isFolder === 1) {
    return "folder";
  }

  const mime = String(row.fileType || "").toLowerCase();
  if (mime.startsWith("image/")) return "image";
  if (mime.startsWith("video/")) return "video";
  if (mime.startsWith("audio/")) return "audio";
  if (mime.startsWith("text/")) return "text";

  const ext = normalizeExt(row.originalFilename);
  if (IMAGE_EXTS.has(ext)) return "image";
  if (VIDEO_EXTS.has(ext)) return "video";
  if (AUDIO_EXTS.has(ext)) return "audio";
  if (CODE_EXTS.has(ext)) return "code";
  if (TEXT_EXTS.has(ext)) return "text";
  if (ARCHIVE_EXTS.has(ext)) return "archive";
  if (DOCUMENT_EXTS.has(ext)) return "document";

  if (
    mime.includes("pdf") ||
    mime.includes("msword") ||
    mime.includes("presentation") ||
    mime.includes("spreadsheet") ||
    mime.includes("excel")
  ) {
    return "document";
  }

  if (
    mime.includes("zip") ||
    mime.includes("rar") ||
    mime.includes("7z") ||
    mime.includes("tar") ||
    mime.includes("gzip")
  ) {
    return "archive";
  }

  return "other";
};

export const getFileTypeLabel = (row = {}) => {
  const category = getFileTypeCategory(row);
  return CATEGORY_LABEL_MAP[category] || CATEGORY_LABEL_MAP.other;
};
