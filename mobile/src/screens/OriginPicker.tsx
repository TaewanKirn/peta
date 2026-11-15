import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { finalAssets } from '../assets'
import { PrimaryButton, ScreenFrame } from '../components/ScreenFrame'
import { services } from '../services'
import type { Origin } from '../services/mockData'
import { useApp } from '../state/AppState'

/** 출발지 설정 (M03 여행 일정 2·3). 지도는 시안 이미지이고, 검색은 카카오 로컬(실패하면 샘플 목록)에서 찾는다. */
export function OriginPicker() {
  const navigate = useNavigate()
  const { state, update } = useApp()
  const saved = state.tripDraft.origin
  const [query, setQuery] = useState(saved)
  // 저장된 출발지는 이름만 있어 주소 없이 보여 준다. 새로 검색해서 고르면 주소가 붙는다.
  const [picked, setPicked] = useState<Origin | null>(saved ? { id: 'saved', name: saved, address: '' } : null)
  const [results, setResults] = useState<Origin[] | null>(null)
  const q = query.trim()

  useEffect(() => {
    if (!q || q === picked?.name) {
      setResults(null)
      return
    }
    let active = true
    setResults(null)
    // 입력이 멈춘 뒤 검색한다. 늦게 온 이전 응답은 버린다.
    const timer = setTimeout(() => {
      services.origins.search(q).then((found) => active && setResults(found))
    }, 250)
    return () => {
      active = false
      clearTimeout(timer)
    }
  }, [q, picked?.name])

  const confirm = () => {
    if (!picked) return
    update((s) => ({ ...s, tripDraft: { ...s.tripDraft, origin: picked.name } }))
    navigate(-1)
  }

  return (
    <ScreenFrame
      title="출발지 설정"
      wideFooter
      footer={
        <PrimaryButton disabled={!picked} onClick={confirm}>
          출발지 설정하기
        </PrimaryButton>
      }
    >
      <div className="origin">
        <img className="origin-map" src={finalAssets.originMap} alt="출발지 주변 지도" />
        <div className="origin-search">
          <img src={finalAssets.search} alt="" />
          <label className="visually-hidden" htmlFor="origin-query">
            출발지 검색
          </label>
          <input
            id="origin-query"
            type="search"
            autoComplete="off"
            placeholder="장소 · 주소 검색"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setPicked(null)
            }}
          />
          <button type="button" className="origin-mic" aria-label="음성으로 검색" onClick={() => setQuery('시청역')}>
            <img src={finalAssets.mic} alt="" />
          </button>
        </div>
        {q && !picked && (
          <ul className="origin-results" aria-label="출발지 검색 결과">
            {results === null && <li className="search-note">찾고 있어요…</li>}
            {results?.length === 0 && <li className="search-note">검색 결과가 없어요.</li>}
            {results?.map((o) => (
              <li key={o.id}>
                <button
                  type="button"
                  onClick={() => {
                    setPicked(o)
                    setQuery(o.name)
                  }}
                >
                  <strong>{o.name}</strong>
                  <span>{o.address}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
        {picked && (
          <section className="origin-card" aria-live="polite">
            <h2>{picked.name}</h2>
            <p>
              {picked.address && <img src={finalAssets.pin} alt="" />}
              {picked.address}
            </p>
            <span className="origin-paw" aria-hidden="true">
              <img src={finalAssets.pawOrange} alt="" />
            </span>
          </section>
        )}
      </div>
    </ScreenFrame>
  )
}
