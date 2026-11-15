import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { finalAssets, ui1 } from '../assets'
import { PetAvatar, RatingBones, SelectionChip } from '../components/controls'
import { ConfirmDialog } from '../components/Modal'
import { ErrorBlock, LoadingBlock, PrimaryButton, ScreenFrame } from '../components/ScreenFrame'
import { withJosa } from '../domain/format'
import { services } from '../services'
import { emptyReviewDraft, selectParty, useApp } from '../state/AppState'
import { useAsync } from './useAsync'

export function ReviewSave() {
  const navigate = useNavigate()
  const { state, update } = useApp()
  const { pets } = selectParty(state)
  const draft = state.reviewDraft
  const placeId = state.tripDraft.placeId
  const [ratingError, setRatingError] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(false)
  const [saved, setSaved] = useState(false)

  const { state: result, retry } = useAsync(
    () => (placeId ? services.places.get(placeId) : Promise.resolve(null)),
    placeId ?? 'none',
  )

  if (pets.length === 0) return <Navigate to="/plan/party" replace />
  const lead = pets[0]
  const set = (patch: Partial<typeof draft>) =>
    update((s) => ({ ...s, reviewDraft: { ...s.reviewDraft, ...patch } }))

  const tags = [
    '편안하게 이동했어요',
    `${withJosa(lead.name, '가', '이')} 멀미했어요`,
    '멀어요',
    '정체가 심했어요',
    '도로가 울퉁불퉁했어요',
  ]
  const toggleTag = (tag: string) =>
    set({
      drivingTags: draft.drivingTags.includes(tag)
        ? draft.drivingTags.filter((t) => t !== tag)
        : [...draft.drivingTags, tag],
    })

  const submit = async () => {
    if (draft.rating === 0) {
      setRatingError(true)
      return
    }
    setSaving(true)
    setSaveError(false)
    try {
      await services.reviews.submit({ ...draft, placeId })
      setSaved(true)
    } catch {
      setSaveError(true)
    } finally {
      setSaving(false)
    }
  }

  const dirty = draft.rating > 0 || draft.drivingTags.length > 0 || draft.comment.length > 0
  const place = result.status === 'done' ? result.data : null

  return (
    <ScreenFrame
      title="후기 남기기"
      wideFooter
      dirty={dirty}
      onDiscard={() => update((s) => ({ ...s, reviewDraft: emptyReviewDraft() }))}
      footer={
        place && (
          <div className="review-footer">
            {saveError && (
              <p className="field-error center" role="alert">
                후기를 저장하지 못했어요. 다시 시도해 주세요.
              </p>
            )}
            <PrimaryButton loading={saving} onClick={submit}>
              후기 남기기
            </PrimaryButton>
          </div>
        )
      }
    >
      <div className="page">
        <div className="review-head">
          <h2 className="page-title">
            {withJosa(lead.name, '와', '과')} 함께한 이곳,
            <br />
            어땠나요?
          </h2>
          <PetAvatar pet={lead} size={66} />
        </div>
        <hr className="divider review-top-divider" />

        {result.status === 'loading' && <LoadingBlock label="장소를 불러오고 있어요…" />}
        {result.status === 'error' && <ErrorBlock message="장소를 불러오지 못했어요." onRetry={retry} />}
        {result.status === 'done' && !place && (
          <div className="state-block">
            <p>후기를 남길 여행이 아직 없어요.</p>
            <button type="button" className="btn-secondary" onClick={() => navigate('/plan/trip')}>
              여행 계획하기
            </button>
          </div>
        )}
        {place && (
          <>
            <div className="review-place">
              <img className="review-photo" src={place.photos[0]} alt="" />
              <div>
                <em>{place.categoryLabel}</em>
                <strong>{place.name}</strong>
                <small>{place.region}</small>
              </div>
              <button
                type="button"
                className={state.favorites.includes(place.id) ? 'paw-button review-paw active' : 'paw-button review-paw'}
                aria-pressed={state.favorites.includes(place.id)}
                aria-label={`${place.name} 저장`}
                onClick={() =>
                  update((s) => ({
                    ...s,
                    favorites: s.favorites.includes(place.id)
                      ? s.favorites.filter((x) => x !== place.id)
                      : [...s.favorites, place.id],
                  }))
                }
              >
                <img src={finalAssets.pawOrange} alt="" />
              </button>
            </div>

            <RatingBones
              value={draft.rating}
              onChange={(rating) => {
                setRatingError(false)
                set({ rating })
              }}
            />
            <p className="rating-count">{draft.rating}/5점</p>
            {ratingError && <p className="field-error center" role="alert">평점을 선택해 주세요.</p>}

            <h3 className="review-question">가는 길은 어땠나요?</h3>
            <div className="review-chips first">
              {tags.slice(0, 3).map((tag) => (
                <SelectionChip key={tag} selected={draft.drivingTags.includes(tag)} onClick={() => toggleTag(tag)}>
                  {draft.drivingTags.includes(tag) && <img className="chip-check" src={ui1.chipCheck} alt="" />}
                  {tag}
                </SelectionChip>
              ))}
            </div>
            <div className="review-chips">
              {tags.slice(3).map((tag) => (
                <SelectionChip key={tag} selected={draft.drivingTags.includes(tag)} onClick={() => toggleTag(tag)}>
                  {draft.drivingTags.includes(tag) && <img className="chip-check" src={ui1.chipCheck} alt="" />}
                  {tag}
                </SelectionChip>
              ))}
            </div>
            <label className="review-question comment-label" htmlFor="review-comment">
              한줄평을 남겨주세요.
            </label>
            <input
              id="review-comment"
              className="review-comment"
              maxLength={100}
              placeholder="솔직한 후기는 다른 사용자에게 도움이 돼요."
              value={draft.comment}
              onChange={(event) => set({ comment: event.target.value })}
            />
          </>
        )}
      </div>
      {saved && (
        <ConfirmDialog
          title="후기를 남겼어요"
          body="다른 보호자에게 큰 도움이 될 거예요."
          cancelLabel="닫기"
          confirmLabel="확인"
          onCancel={() => {
            update((s) => ({ ...s, reviewDraft: emptyReviewDraft() }))
            setSaved(false)
            navigate('/plan/itinerary')
          }}
          onConfirm={() => {
            update((s) => ({ ...s, reviewDraft: emptyReviewDraft() }))
            setSaved(false)
            navigate('/plan/itinerary')
          }}
        />
      )}
    </ScreenFrame>
  )
}
