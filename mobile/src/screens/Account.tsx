import { useNavigate } from 'react-router-dom'
import { ui1 } from '../assets'
import type { AuthProvider } from '../domain/types'
import { services } from '../services'
import { useApp } from '../state/AppState'

const PROVIDERS: Array<{ id: Exclude<AuthProvider, 'email'>; label: string }> = [
  { id: 'kakao', label: '카카오로 로그인' },
  { id: 'naver', label: '네이버로 로그인' },
  { id: 'pleos', label: 'Pleos로 로그인' },
]

export function Account() {
  const navigate = useNavigate()
  const { state, update } = useApp()
  const { auth } = state
  const busy = auth.status === 'loading'

  const signIn = async (provider: AuthProvider) => {
    update((s) => ({ ...s, auth: { provider, status: 'loading', error: null, displayName: null } }))
    try {
      const { displayName } = await services.auth.signIn(provider)
      update((s) => ({ ...s, auth: { provider, status: 'success', error: null, displayName } }))
      navigate('/signup/style')
    } catch {
      update((s) => ({
        ...s,
        auth: { provider, status: 'error', error: '로그인하지 못했어요. 다시 시도해 주세요.', displayName: null },
      }))
    }
  }

  return (
    <div className="screen bare">
      <main className="screen-main account">
        <div className="account-hero">
          <img className="hero-cloud-left" src={ui1.accountCloudLeft} alt="" />
          <img className="hero-cloud-right" src={ui1.accountCloudRight} alt="" />
          <img className="hero-ground" src={ui1.accountGround} alt="" />
          <img className="hero-tree" src={ui1.accountTree} alt="" />
          <img className="hero-dog" src={ui1.accountDog} alt="" />
          <p className="hero-service">Pet Trip Service</p>
          <h1 className="hero-logo">PET:A</h1>
        </div>
        <div className="account-buttons">
          {PROVIDERS.map(({ id, label }) => (
            <button
              key={id}
              type="button"
              className={`login-button login-${id}`}
              disabled={busy}
              aria-busy={busy && auth.provider === id}
              onClick={() => signIn(id)}
            >
              {busy && auth.provider === id && <span className="spinner" aria-hidden="true" />}
              {label}
            </button>
          ))}
          {auth.status === 'error' && (
            <p className="field-error center" role="alert">
              {auth.error}
            </p>
          )}
        </div>
        <div className="account-signup">
          <p>페타가 처음이신가요?</p>
          <button type="button" className="text-link" disabled={busy} onClick={() => signIn('email')}>
            이메일로 회원가입
          </button>
        </div>
      </main>
    </div>
  )
}
