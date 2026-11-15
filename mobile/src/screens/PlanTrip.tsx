import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { finalAssets, ui1 } from '../assets'
import { PetAvatar, PlaceCard, SelectionChip } from '../components/controls'
import { ConfirmDialog, Modal } from '../components/Modal'
import { EmptyBlock, ErrorBlock, LoadingBlock, ScreenFrame } from '../components/ScreenFrame'
import { formatDateRange, formatNights, withJosa } from '../domain/format'
import { petSummary, THEMES } from '../domain/trip'
import type { Place, PlaceCategory } from '../domain/types'
import { services } from '../services'
import { REGIONS } from '../services/mockData'
import { emptyTripDraft, selectParty, useApp } from '../state/AppState'
import { useAsync } from './useAsync'

const TABS: Array<{ id: PlaceCategory; label: string }> = [
  { id: 'stay', label: '숙소' },
  { id: 'activity', label: '활동' },
  { id: 'course', label: '코스' },
]

function RegionSheet({ value, onSave, onClose }: { value: string; onSave: (region: string) => void; onClose: () => void }) {
  return (
    <Modal title="어디로 떠날까요?" variant="sheet" onClose={onClose}>
      <div className="sheet-list" role="group" aria-label="지역">
        {REGIONS.map((region) => (
          <button
            key={region}
            type="button"
            className={region === value ? 'sheet-option selected' : 'sheet-option'}
            aria-pressed={region === value}
            onClick={() => onSave(region)}
          >
            {region}
          </button>
        ))}
      </div>
    </Modal>
  )
}

function DateSheet({
  start,
  end,
  onSave,
  onClose,
}: {
  start: string
  end: string
  onSave: (start: string, end: string) => void
  onClose: () => void
}) {
  const [from, setFrom] = useState(start)
  const [to, setTo] = useState(end)
  const invalid = !from || !to || to < from
  return (
    <Modal title="언제 떠날까요?" variant="sheet" onClose={onClose}>
      <label className="sheet-field">
        출발일
        <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
      </label>
      <label className="sheet-field">
        돌아오는 날
        <input type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} />
      </label>
      {invalid && <p className="field-error" role="alert">돌아오는 날은 출발일 이후로 골라 주세요.</p>}
      <button type="button" className="btn-primary-small wide" disabled={invalid} onClick={() => onSave(from, to)}>
        확인
      </button>
    </Modal>
  )
}

export function PlanTrip() {
  const navigate = useNavigate()
  const { state, update } = useApp()
  const { pets, guardians } = selectParty(state)
  const draft = state.tripDraft
  const [category, setCategory] = useState<PlaceCategory>('stay')
  const [sheet, setSheet] = useState<'region' | 'dates' | 'criteria' | null>(null)
  const [sort, setSort] = useState<'recommend' | 'rating'>('recommend')
  const sorted = (list: Place[]) => (sort === 'rating' ? [...list].sort((a, b) => b.rating.score - a.rating.score) : list)

  const { state: places, retry } = useAsync(
    () => services.places.list({ region: draft.region, theme: draft.theme, category }),
    `${draft.region}|${draft.theme}|${category}`,
  )

  if (pets.length === 0 || guardians.length === 0) return <Navigate to="/plan/party" replace />
  const lead = pets[0]
  const setDraft = (patch: Partial<typeof draft>) =>
    update((s) => ({ ...s, tripDraft: { ...s.tripDraft, ...patch } }))
  const tabLabel = TABS.find((tab) => tab.id === category)?.label ?? ''

  return (
    <ScreenFrame
      title="여행 계획"
      dirty={Boolean(draft.placeId)}
      onDiscard={() => update((s) => ({ ...s, tripDraft: emptyTripDraft() }))}
    >
      <p className="trip-sentence">
        <button type="button" onClick={() => setSheet('region')} aria-label={`지역 변경, 현재 ${draft.region}`}>
          {draft.region}
        </button>
        으로,
        <br />
        <button type="button" onClick={() => setSheet('dates')} aria-label="날짜 변경">
          {formatDateRange(draft.startDate, draft.endDate)}
        </button>{' '}
        {formatNights(draft.startDate, draft.endDate)}
        <br />
        <button type="button" onClick={() => navigate('/plan/party')} aria-label="보호자 변경">
          보호자 {guardians.length}명
        </button>
        과 떠나요
      </p>

      <div className="trip-profile">
        <PetAvatar pet={lead} size={44} />
        <div>
          <strong>{withJosa(lead.name, '와', '과')} 함께하는 여행</strong>
          <small>{petSummary(lead)}</small>
        </div>
        <button type="button" className="link-orange" onClick={() => navigate('/plan/party')}>
          변경
        </button>
      </div>

      <h2 className="trip-heading">어떤 여행을 하고 싶나요?</h2>
      <div className="chip-row trip-themes" role="radiogroup" aria-label="여행 테마">
        {THEMES.map((theme) => (
          <SelectionChip
            key={theme.id}
            role="radio"
            selected={draft.theme === theme.id}
            onClick={() => setDraft({ theme: theme.id })}
          >
            {draft.theme === theme.id && <img className="chip-check" src={ui1.chipCheck} alt="" />}
            {theme.label}
          </SelectionChip>
        ))}
      </div>

      <div className="tabs trip-tabs" role="tablist" aria-label="장소 종류">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={category === tab.id}
            className={category === tab.id ? 'tab selected' : 'tab'}
            onClick={() => setCategory(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <section className="trip-places" aria-label={`추천 ${tabLabel}`}>
        <div className="trip-places-heading">
          <button type="button" className="filter-link" onClick={() => setSheet('criteria')}>
            <img src={finalAssets.filter} alt="" />
            상세필터
          </button>
          <button
            type="button"
            className="sort-link"
            aria-label={`정렬: ${sort === 'recommend' ? '추천순' : '평점순'}. 눌러서 바꾸기`}
            onClick={() => setSort((v) => (v === 'recommend' ? 'rating' : 'recommend'))}
          >
            {sort === 'recommend' ? '추천순' : '평점순'}
            <img src={finalAssets.chevronDown} alt="" />
          </button>
        </div>
        {places.status === 'loading' && <LoadingBlock label="추천 장소를 찾고 있어요…" />}
        {places.status === 'error' && <ErrorBlock message="장소를 불러오지 못했어요." onRetry={retry} />}
        {places.status === 'done' && places.data.length === 0 && (
          <EmptyBlock message={`이 조건에 맞는 ${tabLabel}가 아직 없어요.`} />
        )}
        {places.status === 'done' &&
          sorted(places.data).map((place) => (
            <PlaceCard
              key={place.id}
              place={place}
              petName={lead.name}
              favorite={state.favorites.includes(place.id)}
              onOpen={() => navigate(`/plan/place/${place.id}`)}
              onToggleFavorite={() =>
                update((s) => ({
                  ...s,
                  favorites: s.favorites.includes(place.id)
                    ? s.favorites.filter((id) => id !== place.id)
                    : [...s.favorites, place.id],
                }))
              }
            />
          ))}
      </section>

      {sheet === 'region' && (
        <RegionSheet
          value={draft.region}
          onClose={() => setSheet(null)}
          onSave={(region) => {
            setDraft({ region })
            setSheet(null)
          }}
        />
      )}
      {sheet === 'dates' && (
        <DateSheet
          start={draft.startDate}
          end={draft.endDate}
          onClose={() => setSheet(null)}
          onSave={(startDate, endDate) => {
            setDraft({ startDate, endDate })
            setSheet(null)
          }}
        />
      )}
      {sheet === 'criteria' && (
        <ConfirmDialog
          title="상세필터"
          body={`지금은 ${lead.name}의 크기와 선택한 여행 테마로 추천해요. 상세 조건은 준비 중이에요.`}
          cancelLabel="닫기"
          confirmLabel="확인"
          onCancel={() => setSheet(null)}
          onConfirm={() => setSheet(null)}
        />
      )}
    </ScreenFrame>
  )
}
