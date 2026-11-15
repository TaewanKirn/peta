import { useEffect, useId, useRef, type ReactNode } from 'react'

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), select, textarea, a[href], [tabindex="0"]'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
  variant?: 'dialog' | 'sheet'
}

/** 포커스를 가두고 Esc로 닫히는 모달. 닫히면 이전 요소로 포커스를 돌려준다. */
export function Modal({ title, onClose, children, variant = 'dialog' }: ModalProps) {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    panel.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus()
    return () => previous?.focus?.()
  }, [])

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onClose()
      return
    }
    if (event.key !== 'Tab' || !panel.current) return
    const items = Array.from(panel.current.querySelectorAll<HTMLElement>(FOCUSABLE))
    if (items.length === 0) return
    const first = items[0]
    const last = items[items.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div className={`modal-backdrop modal-${variant}`} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={panel}
        className="modal-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={onKeyDown}
      >
        <h2 id={titleId} className="modal-title">
          {title}
        </h2>
        {children}
      </div>
    </div>
  )
}

interface ConfirmProps {
  title: string
  body?: string
  confirmLabel: string
  cancelLabel: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ title, body, confirmLabel, cancelLabel, onConfirm, onCancel }: ConfirmProps) {
  return (
    <Modal title={title} onClose={onCancel}>
      {body && <p className="modal-body">{body}</p>}
      <div className="modal-actions">
        <button type="button" className="btn-secondary" onClick={onCancel}>
          {cancelLabel}
        </button>
        <button type="button" className="btn-primary-small" onClick={onConfirm}>
          {confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
