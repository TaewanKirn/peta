import { useEffect, useId, useRef, useState } from 'react'
import { ui1 } from '../assets'

/** 입력값과 겹치는 부분을 굵게 표시한다. */
function Highlight({ text, query }: { text: string; query: string }) {
  const q = query.trim()
  const at = q ? text.indexOf(q) : -1
  if (at < 0) return <>{text}</>
  return (
    <>
      {text.slice(0, at)}
      <b>{q}</b>
      {text.slice(at + q.length)}
    </>
  )
}

interface AutocompleteProps<T> {
  label: string
  placeholder?: string
  value: string
  /** 선택이 끝났으면 입력칸 대신 이 내용을 보여준다. */
  selectedLabel?: string
  search: (query: string) => Promise<T[]>
  /** 값이 비어 있을 때도 목록을 펼칠 수 있게 한다. */
  optionLabel: (option: T) => string
  optionKey: (option: T) => string
  onInput: (value: string) => void
  onSelect: (option: T) => void
  onClearSelection?: () => void
  emptyText: string
  errorText: string
  disabled?: boolean
  /** Figma에서 라벨이 보이지 않는 필드. 스크린 리더용으로만 남긴다. */
  hideLabel?: boolean
}

/** 밑줄 입력 + 자동완성 목록 (종류, 동물병원) */
export function Autocomplete<T>({
  label,
  placeholder,
  value,
  selectedLabel,
  search,
  optionLabel,
  optionKey,
  onInput,
  onSelect,
  onClearSelection,
  emptyText,
  errorText,
  disabled,
  hideLabel,
}: AutocompleteProps<T>) {
  const inputId = useId()
  const listId = useId()
  const box = useRef<HTMLDivElement>(null)
  const input = useRef<HTMLInputElement>(null)
  const [open, setOpen] = useState(false)
  const [result, setResult] = useState<{ status: 'idle' | 'error' } | { status: 'done'; options: T[] }>({ status: 'idle' })

  useEffect(() => {
    if (!open) return
    let active = true
    const timer = setTimeout(() => {
      search(value)
        .then((options) => active && setResult({ status: 'done', options }))
        .catch(() => active && setResult({ status: 'error' }))
    }, 120)
    return () => {
      active = false
      clearTimeout(timer)
    }
    // search는 value와 open 변화에만 다시 호출한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, open])

  useEffect(() => {
    if (!open) return
    const onDown = (event: MouseEvent) => {
      if (!box.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])

  return (
    <div className="line-field" ref={box}>
      <label htmlFor={inputId} className={hideLabel ? 'visually-hidden' : undefined}>
        {label}
      </label>
      <div className="line-input">
        {selectedLabel ? (
          <button
            type="button"
            id={inputId}
            className="line-value"
            aria-label={`${label}: ${selectedLabel}. 다시 선택`}
            onClick={() => {
              onClearSelection?.()
              setOpen(true)
              setTimeout(() => input.current?.focus())
            }}
          >
            {selectedLabel.split('\n').map((line) => (
              <span key={line} style={{ display: 'block' }}>
                {line}
              </span>
            ))}
          </button>
        ) : (
          <input
            ref={input}
            id={inputId}
            role="combobox"
            aria-expanded={open}
            aria-controls={listId}
            autoComplete="off"
            placeholder={placeholder}
            value={value}
            disabled={disabled}
            onFocus={() => setOpen(true)}
            onChange={(event) => {
              onInput(event.target.value)
              setOpen(true)
            }}
            onKeyDown={(event) => {
              if (event.key === 'Escape') setOpen(false)
            }}
          />
        )}
        <button
          type="button"
          className="line-icon"
          aria-label={`${label} 목록 ${open ? '닫기' : '열기'}`}
          disabled={disabled}
          onClick={() => setOpen((v) => !v)}
        >
          <img src={ui1.dropdown} alt="" />
        </button>
      </div>
      {open && (
        <div className="suggest" id={listId} role="listbox" aria-label={`${label} 검색 결과`}>
          {result.status === 'error' && <p className="suggest-note" role="alert">{errorText}</p>}
          {result.status === 'done' && result.options.length === 0 && <p className="suggest-note">{emptyText}</p>}
          {result.status === 'done' &&
            result.options.map((option) => (
              <button
                key={optionKey(option)}
                type="button"
                role="option"
                aria-selected={false}
                onClick={() => {
                  onSelect(option)
                  setOpen(false)
                }}
              >
                <Highlight text={optionLabel(option)} query={value} />
              </button>
            ))}
        </div>
      )}
    </div>
  )
}
