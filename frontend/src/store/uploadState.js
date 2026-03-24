import { ref } from 'vue';

/**
 * Global state for file upload tracking.
 * Used to share upload progress across components (e.g., FileList and global indicator).
 */

export const uploadingFile = ref(false);
export const uploadPercentage = ref(0);
export const uploadStatus = ref('');
export const uploadSpeed = ref('');
export const uploadProcessing = ref(false);
export const uploadStartTime = ref(0);
