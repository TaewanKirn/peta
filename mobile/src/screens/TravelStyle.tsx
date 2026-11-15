import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ui1 } from '../assets'
import { PrimaryButton, ScreenFrame } from '../components/ScreenFrame'
import { useApp } from '../state/AppState'

// 산책·차박·바캉스 설명은 Figma 원문이고, 나머지 셋은 원본에 없어 임시 문구다 (PO 확인 필요).
// 1박 여행러는 새 일러스트가 없어 이전 디자인 이미지를 쓴다.
const STYLES = [
  { id: 'walk', label: '산책 마스터', image: ui1.styleWalk, description: '거의 매일 함께 산책 나가기' },
  { id: 'photo', label: '인생샷 발굴', image: ui1.stylePhoto, description: '여행마다 인생샷 남기기' },
  { id: 'overnight', label: '1박 여행러', image: ui1.styleOvernight, description: '하룻밤 푹 쉬고 돌아오기' },
  { id: 'seasons', label: '사계절 투어러', image: ui1.styleSeasons, description: '계절마다 새로운 풍경 보러 가기' },
  { id: 'camping', label: '차박 마스터', image: ui1.styleCamping, description: '불멍 때리면서 차박 낭만 즐기기' },
  { id: 'vacation', label: '바캉스 매니아', image: ui1.styleVacation, description: '여행갈 때 바다는 꼭 가기' },
]

const SWIPE_DISTANCE = 40

export function TravelStyle() {
  const navigate = useNavigate()
  const { state, update } = useApp()
  const selected = state.travelStyles
  const [index, setIndex] = useState(0)
  const dragX = useRef<number | null>(null)
  const name = state.auth.displayName ?? '회원'

  const at = (offset: number) => STYLES[(index + offset + STYLES.length) % STYLES.length]
  const current = at(0)
  const isOn = selected.includes(current.id)
  const move = (delta: number) => setIndex((i) => (i + delta + STYLES.length) % STYLES.length)

  const toggle = (id: string) =>
    update((s) => ({
      ...s,
      travelStyles: s.travelStyles.includes(id) ? s.travelStyles.filter((x) => x !== id) : [...s.travelStyles, id],
    }))

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowLeft') move(-1)
    if (event.key === 'ArrowRight') move(1)
  }
  const onPointerDown = (event: PointerEvent) => {
    dragX.current = event.clientX
  }
  const onPointerUp = (event: PointerEvent) => {
    if (dragX.current === null) return
    const moved = event.clientX - dragX.current
    dragX.current = null
    if (Math.abs(moved) >= SWIPE_DISTANCE) move(moved < 0 ? 1 : -1)
  }

  const next = () => navigate('/signup/pet')

  return (
    <ScreenFrame
      closable={false}
      progress={0.315}
      trailing={
        selected.length === 0 ? (
          <button type="button" className="text-skip" onClick={next}>
            건너뛰기
          </button>
        ) : undefined
      }
      footer={
        <div className="footer-row">
          <button type="button" className="cta-outline" onClick={next}>
            건너뛰기
          </button>
          <PrimaryButton disabled={selected.length === 0} onClick={next}>
            {selected.length === 0 ? '선택' : `${selected.length}개 선택`}
          </PrimaryButton>
        </div>
      }
      wideFooter
    >
      <h2 className="step-title">{`${name}님의\n여행 취향을 골라주세요`}</h2>
      <div className="style-head" aria-live="polite">
        <h3 className={isOn ? 'on' : undefined}>{current.label}</h3>
        <p>{current.description}</p>
      </div>
      <section
        className="style-stage"
        aria-label="여행 취향 캐러셀"
        onKeyDown={onKeyDown}
        onPointerDown={onPointerDown}
        onPointerUp={onPointerUp}
      >
        {STYLES.map((style, i) => {
          // 현재 항목 기준 상대 위치(-2~3). 원은 그대로 두고 위치 클래스만 바꿔 CSS로 미끄러지게 움직인다.
          let rel = (i - index + STYLES.length) % STYLES.length
          if (rel > 3) rel -= STYLES.length
          const pos = rel < 0 ? `m${-rel}` : String(rel)
          const on = selected.includes(style.id)
          const visible = Math.abs(rel) <= 1
          const label = rel === 0 ? `${style.label} 선택` : rel === -1 ? `이전: ${style.label}` : `다음: ${style.label}`
          return (
            <button
              key={style.id}
              type="button"
              className={`style-circle pos-${pos}${rel === 0 && on ? ' on' : ''}`}
              aria-hidden={!visible}
              tabIndex={visible ? 0 : -1}
              aria-pressed={rel === 0 ? on : undefined}
              aria-label={label}
              onClick={() => (rel === 0 ? toggle(style.id) : move(rel))}
            >
              <img src={style.image} alt="" draggable={false} />
              <span className={rel === 0 && on ? 'style-badge show' : 'style-badge'} aria-hidden="true">
                <img src={ui1.badgeCheck} alt="" />
              </span>
            </button>
          )
        })}
      </section>
      <div className="style-chips">
        {STYLES.map((style, i) => {
          const on = selected.includes(style.id)
          return (
            <button
              key={style.id}
              type="button"
              className={on ? 'style-chip on' : 'style-chip'}
              aria-pressed={on}
              onClick={() => {
                setIndex(i)
                toggle(style.id)
              }}
            >
              {on && <img src={ui1.chipCheck} alt="" />}
              {style.label}
            </button>
          )
        })}
      </div>
    </ScreenFrame>
  )
}
