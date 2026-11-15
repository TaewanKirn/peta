import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Node 25의 내장 localStorage가 jsdom 것을 가리므로 테스트에서는 메모리 저장소를 쓴다.
function memoryStorage(): Storage {
  const items = new Map<string, string>()
  return {
    get length() {
      return items.size
    },
    clear: () => items.clear(),
    getItem: (key) => items.get(key) ?? null,
    key: (index) => Array.from(items.keys())[index] ?? null,
    removeItem: (key) => void items.delete(key),
    setItem: (key, value) => void items.set(key, String(value)),
  }
}

if (typeof window !== 'undefined') {
  vi.stubGlobal('localStorage', memoryStorage())
  vi.stubGlobal('sessionStorage', memoryStorage())
}

afterEach(() => {
  vi.restoreAllMocks()
  if (typeof window === 'undefined') return
  cleanup()
  localStorage.clear()
  sessionStorage.clear()
})
