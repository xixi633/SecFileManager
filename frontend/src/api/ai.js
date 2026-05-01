const SEARCH_PREFIX = '__SEARCH__:';
const runtimeApiBase =
  import.meta.env.VITE_API_BASE_URL ||
  `${window.location.protocol}//${window.location.hostname}:8080/api`;

export function streamAiChat(messages, onChunk, onSearchCommand, onDone, onError) {
  const token = localStorage.getItem('token');
  fetch(`${runtimeApiBase}/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ messages })
  }).then(response => {
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    function read() {
      reader.read().then(({ done, value }) => {
        if (done) { onDone(); return; }
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop();

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed) continue;
          if (trimmed.startsWith('event:')) continue;
          if (trimmed.startsWith('data:')) {
            const data = trimmed.slice(5).trim();
            if (data.startsWith(SEARCH_PREFIX)) {
              try {
                const jsonStr = data.slice(SEARCH_PREFIX.length);
                const parsed = JSON.parse(jsonStr);
                onSearchCommand(parsed);
              } catch (e) {
                console.error('Failed to parse search command:', e);
              }
              continue;
            }
            if (data !== '[DONE]') {
              onChunk(data);
            }
          }
        }
        read();
      }).catch(onError);
    }
    read();
  }).catch(onError);
}
