const DB_NAME = 'sfm-file-system';
const STORE_NAME = 'handles';
const KEY = 'downloadDir';

/**
 * Open IndexedDB for storing file handles.
 * @returns {Promise<IDBDatabase>}
 */
const openDb = () => new Promise((resolve, reject) => {
  const request = indexedDB.open(DB_NAME, 1);
  request.onupgradeneeded = () => {
    const db = request.result;
    if (!db.objectStoreNames.contains(STORE_NAME)) {
      db.createObjectStore(STORE_NAME);
    }
  };
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error);
});

export const saveDirectoryHandle = async (handle) => {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put(handle, KEY);
    tx.oncomplete = () => resolve(true);
    tx.onerror = () => reject(tx.error);
  });
};

export const getDirectoryHandle = async () => {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly');
    const request = tx.objectStore(STORE_NAME).get(KEY);
    request.onsuccess = () => resolve(request.result || null);
    request.onerror = () => reject(request.error);
  });
};

export const clearDirectoryHandle = async () => {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).delete(KEY);
    tx.oncomplete = () => resolve(true);
    tx.onerror = () => reject(tx.error);
  });
};

export const verifyPermission = async (handle, mode = 'readwrite') => {
  if (!handle) return false;
  const opts = { mode };
  if (handle.queryPermission) {
    const status = await handle.queryPermission(opts);
    if (status === 'granted') return true;
  }
  if (handle.requestPermission) {
    const status = await handle.requestPermission(opts);
    return status === 'granted';
  }
  return false;
};

export const writeBlobToDirectory = async (dirHandle, fileName, blob) => {
  const fileHandle = await dirHandle.getFileHandle(fileName, { create: true });
  const writable = await fileHandle.createWritable();
  await writable.write(blob);
  await writable.close();
};

export const isFileSystemAccessSupported = () => {
  return typeof window !== 'undefined' && 'showDirectoryPicker' in window;
};
