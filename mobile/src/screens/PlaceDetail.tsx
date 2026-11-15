import { useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { icons } from '../assets'
import { ConfirmDialog } from '../components/Modal'
import { ErrorBlock, IconButton, LoadingBlock, PrimaryButton, useFlowExit } from '../components/ScreenFrame'
import { withJosa } from '../domain/format'
import { services } from '../services'
import { emptyTripDraft, selectParty, useApp } from '../state/AppState'
import { useAsync } from './useAsync'

const CHECK_ICONS = { paw: icons.iconPaw, fence: icons.iconFence, shield: icons.iconShield }

export function PlaceDetail() {
  const navigate = useNavigate()
  const { id = '' } = useParams()
  const { state, update } = useApp()
  const { pets } = selectParty(state)
  const { state: result, retry } = useAsync(() => services.places.get(id), id)
  const [photoIndex, setPhotoIndex] = useState(0)
  const [notice, setNotice] = useState<'reviews' | 'booking' | null>(null)
  const { requestClose, closeDialog } = useFlowExit({
    dirty: Boolean(state.tripDraft.placeId),
    onDiscard: () => update((s) => ({ ...s, tripDraft: emptyTripDraft() })),
  })

  if (pets.length === 0) return <Navigate to="/plan/party" replace />
  const petName = pets[0].name

  const heroButtons = (
    <>
      <IconButton className="hero-button hero-back" icon={icons.back} label="뒤로가기" onClick={() => navigate(-1)} />
      <IconButton className="hero-button hero-close" icon={icons.close} label="닫기" onClick={requestClose} />
    </>
  )

  if (result.status !== 'done' || !result.data) {
    return (
      <div className="screen">
        <div className="detail-fallback">{heroButtons}</div>
        <main className="screen-main">
          {result.status === 'loading' && <LoadingBlock label="장소 정보를 불러오고 있어요…" />}
          {result.status === 'error' && <ErrorBlock message="장소 정보를 불러오지 못했어요." onRetry={retry} />}
          {result.status === 'done' && <ErrorBlock message="찾을 수 없는 장소예요." />}
        </main>
        {closeDialog}
      </div>
    )
  }

  const place = result.data
  const lastPhoto = place.photos.length - 1
  const addToTrip = () => {
    update((s) => ({ ...s, tripDraft: { ...s.tripDraft, placeId: place.id } }))
    navigate('/plan/itinerary')
  }

  return (
    <div className="screen detail">
      <main className="screen-main">
        <div className="detail-hero">
          <img src={place.photos[photoIndex]} alt={`${place.name} 사진 ${photoIndex + 1}`} />
          {heroButtons}
          {photoIndex > 0 && (
            <button
              type="button"
              className="hero-button hero-prev"
              aria-label="이전 사진"
              onClick={() => setPhotoIndex((i) => i - 1)}
            >
              <img src={icons.chevronLeft} alt="" />
            </button>
          )}
          {photoIndex < lastPhoto && (
            <button
              type="button"
              className={photoIndex === 0 ? 'hero-button hero-prev' : 'hero-button hero-next'}
              aria-label="다음 사진"
              onClick={() => setPhotoIndex((i) => i + 1)}
            >
              <img src={icons.chevronLeft} alt="" />
            </button>
          )}
        </div>
        <article className="detail-sheet">
          <p className="detail-eyebrow">
            <img src={icons.face} alt="" />
            {withJosa(petName, '를', '을')} 위한 추천
          </p>
          <h1 className="detail-name">{place.name}</h1>
          <p className="detail-region">
            <img src={icons.pin} alt="" />
            {place.address}
          </p>
          <button
            type="button"
            className={state.favorites.includes(place.id) ? 'paw-button detail-paw active' : 'paw-button detail-paw'}
            aria-pressed={state.favorites.includes(place.id)}
            aria-label={`${place.name} 찜하기`}
            onClick={() =>
              update((s) => ({
                ...s,
                favorites: s.favorites.includes(place.id)
                  ? s.favorites.filter((x) => x !== place.id)
                  : [...s.favorites, place.id],
              }))
            }
          >
            <img src={icons.pawButton} alt="" />
          </button>

          {place.checks.length > 0 && (
            <>
              <h2 className="detail-heading">가기 전 확인해요!</h2>
              <ul className="detail-checks">
                {place.checks.map((check) => (
                  <li key={check.text}>
                    <span>
                      <img src={CHECK_ICONS[check.icon]} alt="" />
                    </span>
                    {check.text}
                  </li>
                ))}
              </ul>
            </>
          )}
          {place.reviews.length > 0 && (
            <>
              <hr className="divider" />
              <div className="detail-review-heading">
                <h2>다녀온 후기</h2>
                <button type="button" className="link-orange-small" onClick={() => setNotice('reviews')}>
                  더보기
                  <img src={icons.chevronOrange} alt="" />
                </button>
              </div>
              {place.reviews.map((review) => (
                <blockquote key={review.text} className="detail-quote">
                  <span aria-hidden="true">“</span>
                  <div>
                    <p>{review.text}</p>
                    <footer>
                      <span className="stars" role="img" aria-label={`별점 ${review.stars}점`}>
                        {Array.from({ length: review.stars }, (_, i) => (
                          <img key={i} src={icons.iconStar} alt="" />
                        ))}
                      </span>
                      {review.label}
                    </footer>
                  </div>
                </blockquote>
              ))}
            </>
          )}
        </article>
      </main>
      <footer className="screen-footer wide detail-footer">
        <div className="detail-booking">
          <span>
            <img src={icons.iconInfo} alt="" />
            객실 예약은 별도로 진행해요
          </span>
          <button type="button" className="link-orange-small" onClick={() => setNotice('booking')}>
            예약 안내
            <img src={icons.chevronOrange} alt="" />
          </button>
        </div>
        <PrimaryButton onClick={addToTrip}>{place.categoryLabel}로 추가하기</PrimaryButton>
      </footer>
      {notice && (
        <ConfirmDialog
          title={notice === 'booking' ? '예약 안내' : '반려견과 다녀온 후기'}
          body={
            notice === 'booking'
              ? '객실 예약은 숙소에 직접 문의해 주세요. 페타에서는 일정만 저장해요.'
              : '후기 전체 보기는 준비 중이에요.'
          }
          cancelLabel="닫기"
          confirmLabel="확인"
          onCancel={() => setNotice(null)}
          onConfirm={() => setNotice(null)}
        />
      )}
      {closeDialog}
    </div>
  )
}
