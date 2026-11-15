import { useState, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { icons } from '../assets'
import { ConfirmDialog } from './Modal'

export function IconButton({
  icon,
  label,
  className = '',
  ...rest
}: { icon: string; label: string } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type="button" className={`icon-button ${className}`} aria-label={label} {...rest}>
      <img src={icon} alt="" />
    </button>
  )
}

interface TopNavigationProps {
  title: string
  progress?: number
  divider?: boolean
  onBack: () => void
  /** 없으면 닫기 버튼을 그리지 않는다. 가입 단계는 닫기가 없다. */
  onClose?: () => void
  trailing?: ReactNode
}

export function TopNavigation({ title, progress, divider, onBack, onClose, trailing }: TopNavigationProps) {
  return (
    <header className={divider || progress !== undefined ? 'top-nav has-divider' : 'top-nav'}>
      <IconButton className="top-nav-back" icon={icons.back} label="뒤로가기" onClick={onBack} />
      <h1 className="top-nav-title">{title}</h1>
      {onClose && <IconButton className="top-nav-close" icon={icons.close} label="닫기" onClick={onClose} />}
      {trailing && <div className="top-nav-trailing">{trailing}</div>}
      {progress !== undefined && (
        <div
          className="top-nav-progress"
          role="progressbar"
          aria-label="가입 진행률"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(progress * 100)}
        >
          <span style={{ width: `${progress * 100}%` }} />
        </div>
      )}
    </header>
  )
}

export function useFlowExit({ dirty, onDiscard, to = '/' }: { dirty: boolean; onDiscard?: () => void; to?: string }) {
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const leave = () => {
    onDiscard?.()
    navigate(to)
  }
  return {
    requestClose: () => (dirty ? setConfirming(true) : leave()),
    closeDialog: confirming ? (
      <ConfirmDialog
        title="작성을 그만둘까요?"
        body="저장하지 않은 내용은 사라져요."
        cancelLabel="계속 작성"
        confirmLabel="그만두기"
        onCancel={() => setConfirming(false)}
        onConfirm={leave}
      />
    ) : null,
  }
}

interface ScreenFrameProps {
  title?: string
  closable?: boolean
  trailing?: ReactNode
  wideFooter?: boolean
  progress?: number
  divider?: boolean
  footer?: ReactNode
  /** 닫기 시 사라지는 값이 있으면 확인 모달을 띄운다. */
  dirty?: boolean
  onDiscard?: () => void
  children: ReactNode
}

/** 상단 내비게이션 + 스크롤 영역 + 하단 고정 영역 */
export function ScreenFrame({ title = '', closable = true, trailing, wideFooter = false, progress, divider, footer, dirty = false, onDiscard, children }: ScreenFrameProps) {
  const navigate = useNavigate()
  const { requestClose, closeDialog } = useFlowExit({ dirty, onDiscard })
  return (
    <div className="screen">
      <TopNavigation
        title={title}
        progress={progress}
        divider={divider}
        trailing={trailing}
        onBack={() => navigate(-1)}
        onClose={closable ? requestClose : undefined}
      />
      <main className="screen-main">{children}</main>
      {footer && <footer className={wideFooter ? 'screen-footer wide' : 'screen-footer'}>{footer}</footer>}
      {closeDialog}
    </div>
  )
}

interface PrimaryButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  loading?: boolean
}

/** 하단 주 CTA. 처리 중에는 눌리지 않아 중복 제출을 막는다. */
export function PrimaryButton({ loading = false, disabled, children, ...rest }: PrimaryButtonProps) {
  return (
    <button type="button" className="cta" disabled={disabled || loading} aria-busy={loading} {...rest}>
      {loading && <span className="spinner" aria-hidden="true" />}
      {children}
    </button>
  )
}

export function LoadingBlock({ label }: { label: string }) {
  return (
    <div className="state-block" role="status">
      <span className="spinner spinner-dark" aria-hidden="true" />
      {label}
    </div>
  )
}

export function ErrorBlock({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state-block" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="btn-secondary" onClick={onRetry}>
          다시 시도
        </button>
      )}
    </div>
  )
}

export function EmptyBlock({ message }: { message: string }) {
  return <div className="state-block">{message}</div>
}
