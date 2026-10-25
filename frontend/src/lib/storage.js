// Per-device memory, such as Browse's search and filters. Storage can be blocked or full, as in some private windows,
// and then reading gives the fallback and saving does nothing, so the app works the same without it.

const prefix = 'dot-tracker.'

export function load(key, fallback) {
  try {
    const raw = window.localStorage.getItem(prefix + key)
    return raw === null ? fallback : JSON.parse(raw)
  } catch {
    return fallback
  }
}

export function save(key, value) {
  try {
    window.localStorage.setItem(prefix + key, JSON.stringify(value))
  } catch {
    // Nothing to do, the value just isn't remembered
  }
}
