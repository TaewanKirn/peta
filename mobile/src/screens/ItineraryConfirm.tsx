import { useRef, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { finalAssets, icons } from '../assets'
import { PetAvatar, TimelineItem } from '../components/controls'
import { ConfirmDialog, Modal } from '../components/Modal'
import { ErrorBlock, LoadingBlock, PrimaryButton, ScreenFrame } from '../components/ScreenFrame'
import { formatDateRange, formatNights, withJosa } from '../domain/format'
import { buildCarTrip, petSummary, themeLabel } from '../domain/trip'
import { services } from '../services'
import { emptyTripDraft, selectParty, useApp } from '../state/AppState'
import { useAsync } from './useAsync'

function TextSheet({
  title,
  label,
  placeholder,
  initial,
  onSave,
  onClose,
}: {
  title: string
  label: string
  placeholder: string
  initial: string
  onSave: (value: string) => void
  onClose: () => void
}) {
  const [value, setValue] = useState(initial)
  return (
    <Modal title={title} variant="sheet" onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (value.trim()) onSave(value.trim())
        }}
      >
        <label className="sheet-field">
          {label}
          <input value={value} placeholder={placeholder} onChange={(event) => setValue(event.target.value)} />
        </label>
        <button type="submit" className="btn-primary-small wide" disabled={!value.trim()}>
          확인
        </button>
      </form>
    </Modal>
  )
}

export function ItineraryConfirm() {
  const navigate = useNavigate()
  const { state, update } = useApp()
  const { pets, guardians } = selectParty(state)
  const draft = state.tripDraft
  const [day, setDay] = useState<1 | 2>(1)
  const [sheet, setSheet] = useState<'item' | null>(null)
  const [errors, setErrors] = useState<{ origin?: string; place?: string }>({})
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(false)
  const [saved, setSaved] = useState(false)
  const originRow = useRef<HTMLButtonElement>(null)
  const placeRow = useRef<HTMLButtonElement>(null)

  const { state: placeResult, retry } = useAsync(
    () => (draft.placeId ? services.places.get(draft.placeId) : Promise.resolve(null)),
    draft.placeId ?? 'none',
  )

  if (pets.length === 0 || guardians.length === 0) return <Navigate to="/plan/party" replace />
  const lead = pets[0]
  const place = placeResult.status === 'done' ? placeResult.data : null
  const dayItems = draft.items.filter((item) => item.day === day)
  const setDraft = (patch: Partial<typeof draft>) =>
    update((s) => ({ ...s, tripDraft: { ...s.tripDraft, ...patch } }))

  const save = async () => {
    if (saving) return
    const found = {
      origin: draft.origin ? undefined : '출발 위치를 설정해 주세요',
      place: place ? undefined : '일정에 넣을 장소를 골라 주세요',
    }
    setErrors(found)
    if (found.origin || found.place) {
      setDay(1)
      // 탭 전환이 그려진 다음 비어 있는 행으로 포커스를 옮긴다.
      setTimeout(() => (found.origin ? originRow : placeRow).current?.focus())
      return
    }
    setSaving(true)
    setSaveError(false)
    try {
      await services.tripSync.save(buildCarTrip({ draft, pets, guardians, place: place! }))
      setSaved(true)
    } catch {
      setSaveError(true)
    } finally {
      setSaving(false)
    }
  }

  return (
    <ScreenFrame
      title="여행 일정"
      wideFooter
      dirty={!saved}
      onDiscard={() => update((s) => ({ ...s, tripDraft: emptyTripDraft() }))}
      footer={
        <>
          {saveError && (
            <p className="field-error center" role="alert">
              저장하지 못했어요. 연결을 확인하고 다시 시도해 주세요.
            </p>
          )}
          <p className="footer-note">차량에서도 여행 계획을 볼 수 있어요.</p>
          <PrimaryButton loading={saving} onClick={save}>
            여행 계획 저장하기
          </PrimaryButton>
        </>
      }
    >
      <div className="page">
        <h2 className="page-title itinerary-title">
          {withJosa(lead.name, '와', '과')} 함께하는 {draft.region} 여행
        </h2>
        <p className="itinerary-date">
          <img src={icons.calendar} alt="" />
          {formatDateRange(draft.startDate, draft.endDate)} · {formatNights(draft.startDate, draft.endDate)}
        </p>
        <p className="itinerary-theme">
          <img src={finalAssets.leaf} alt="" />
          여행 테마 <strong>{themeLabel(draft.theme)}</strong>
        </p>
        <div className="itinerary-party">
          <PetAvatar pet={lead} size={42} />
          <div>
            <strong>
              {lead.name} · {petSummary(lead)}
            </strong>
            <small>보호자 {guardians.length}명과 함께</small>
          </div>
          <button type="button" className="link-orange" onClick={() => navigate('/plan/party')}>
            변경
          </button>
        </div>

        <div className="tabs itinerary-tabs" role="tablist" aria-label="일차">
          {([1, 2] as const).map((n) => (
            <button
              key={n}
              type="button"
              role="tab"
              aria-selected={day === n}
              className={day === n ? 'tab selected' : 'tab'}
              onClick={() => setDay(n)}
            >
              {n}일차
            </button>
          ))}
        </div>

        <ol className="timeline">
          {day === 1 && (
            <TimelineItem dot={icons.dotStart}>
              <button ref={originRow} type="button" className="timeline-row" onClick={() => navigate('/plan/origin')}>
                <span>
                  <strong>출발지</strong>
                  <small className={errors.origin && !draft.origin ? 'error-text' : undefined}>
                    {draft.origin || errors.origin || '출발 위치를 설정해 주세요'}
                  </small>
                </span>
                <img src={icons.chevronRight} alt="" />
              </button>
            </TimelineItem>
          )}
          {day === 1 && (
            <TimelineItem dot={icons.dotFilled} variant="place">
              {placeResult.status === 'loading' && <LoadingBlock label="장소를 불러오고 있어요…" />}
              {placeResult.status === 'error' && <ErrorBlock message="장소를 불러오지 못했어요." onRetry={retry} />}
              {placeResult.status === 'done' && (
                <button
                  ref={placeRow}
                  type="button"
                  className="timeline-row"
                  onClick={() => navigate(place ? `/plan/place/${place.id}` : '/plan/trip')}
                >
                  {place ? (
                    <span className="timeline-place">
                      <img src={place.photos[0]} alt="" />
                      <span>
                        <em>{place.categoryLabel}</em>
                        <strong>{place.name}</strong>
                        <small className="added">
                          <img src={icons.checkOrange} alt="" />
                          <b>일정에 추가됨</b> · 예약 전
                        </small>
                      </span>
                    </span>
                  ) : (
                    <span>
                      <strong>숙소</strong>
                      <small className={errors.place ? 'error-text' : undefined}>
                        {errors.place ?? '일정에 넣을 장소를 골라 주세요'}
                      </small>
                    </span>
                  )}
                  <img src={icons.chevronRight} alt="" />
                </button>
              )}
            </TimelineItem>
          )}
          {dayItems.map((item) => (
            <TimelineItem key={item.id} dot={icons.dotFilled}>
              <div className="timeline-row plain">
                <span>
                  <strong>{item.name}</strong>
                </span>
                <button
                  type="button"
                  className="link-muted"
                  aria-label={`${item.name} 삭제`}
                  onClick={() => setDraft({ items: draft.items.filter((other) => other.id !== item.id) })}
                >
                  삭제
                </button>
              </div>
            </TimelineItem>
          ))}
          <TimelineItem dot={icons.dotAdd} variant="last">
            <button type="button" className="timeline-add" onClick={() => setSheet('item')}>
              <img src={icons.plusOrange} alt="" />
              경유지 · 활동 추가
            </button>
          </TimelineItem>
        </ol>
        {day === 2 && dayItems.length === 0 && <p className="field-note">2일차 일정이 아직 없어요.</p>}

      </div>

      {sheet === 'item' && (
        <TextSheet
          title="경유지 · 활동 추가"
          label="이름"
          placeholder="예: 강변 산책"
          initial=""
          onClose={() => setSheet(null)}
          onSave={(name) => {
            setDraft({ items: [...draft.items, { id: `item-${Date.now()}`, day, name }] })
            setSheet(null)
          }}
        />
      )}
      {saved && place && (
        <ConfirmDialog
          title="여행을 저장했어요"
          body={`차량 앱에서 ${withJosa(lead.name, '와', '과')} 떠나는 ${place.name} 여행을 불러올 수 있어요.`}
          cancelLabel="확인"
          confirmLabel="후기 남기기"
          onCancel={() => setSaved(false)}
          onConfirm={() => navigate('/review')}
        />
      )}
    </ScreenFrame>
  )
}
