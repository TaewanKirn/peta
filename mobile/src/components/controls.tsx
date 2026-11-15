import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { finalAssets, icons, photos, ui1 } from '../assets'
import { withJosa } from '../domain/format'
import type { Pet, Place } from '../domain/types'

/** 체크 표시가 있는 단일 선택 버튼 (M02) */
export function OptionGroup<T extends string | boolean>({
  label,
  value,
  options,
  onChange,
  error,
  disabled,
}: {
  label: string
  value: T | null
  options: Array<{ value: T; label: string }>
  onChange: (value: T) => void
  error?: string
  disabled?: boolean
}) {
  const labelId = useId()
  return (
    <div className="option-group">
      <span id={labelId} className="field-caption">
        {label}
      </span>
      <div role="radiogroup" aria-labelledby={labelId} className="option-row">
        {options.map((option) => {
          const selected = option.value === value
          return (
            <button
              key={String(option.value)}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={disabled}
              className={selected ? 'option selected' : 'option'}
              onClick={() => onChange(option.value)}
            >
              <img src={selected ? ui1.optionOn : ui1.optionOff} alt="" />
              {option.label}
            </button>
          )
        })}
      </div>
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

/** 둥근 선택 칩 (M03 테마, M04 태그) */
export function SelectionChip({
  selected,
  onClick,
  children,
  role,
}: {
  selected: boolean
  onClick: () => void
  children: ReactNode
  role?: 'radio'
}) {
  return (
    <button
      type="button"
      className={selected ? 'chip selected' : 'chip'}
      onClick={onClick}
      {...(role === 'radio' ? { role, 'aria-checked': selected } : { 'aria-pressed': selected })}
    >
      {children}
    </button>
  )
}

export function petPhoto(pet: Pick<Pet, 'photo' | 'species'>) {
  return pet.photo ?? (pet.species === 'cat' ? photos.catPhoto : photos.dogPhoto)
}

/**
 * crop: Figma 기본 사진은 얼굴을 크게 잘라 쓴다. 사용자가 올린 사진은 그대로 원형에 맞춘다.
 */
export function PetAvatar({
  pet,
  size,
  crop = true,
}: {
  pet: Pick<Pet, 'photo' | 'species' | 'name'>
  size: number
  crop?: boolean
}) {
  const alt = pet.name ? `${pet.name} 사진` : '반려동물 사진'
  if (crop && !pet.photo && pet.species === 'dog') {
    return (
      <span className="pet-avatar-crop" style={{ width: size, height: size }}>
        <img src={petPhoto(pet)} alt={alt} />
      </span>
    )
  }
  return <img className="pet-avatar" style={{ width: size, height: size }} src={petPhoto(pet)} alt={alt} />
}

export function CheckBadge() {
  return (
    <span className="check-badge" aria-hidden="true">
      <img src={icons.checkWhite} alt="" />
    </span>
  )
}

export function PlaceCard({
  place,
  petName,
  favorite,
  onOpen,
  onToggleFavorite,
}: {
  place: Place
  petName: string
  favorite: boolean
  onOpen: () => void
  onToggleFavorite: () => void
}) {
  return (
    <article className="place-card">
      <div className="place-card-photo">
        <img src={place.photos[0]} alt="" />
        <button
          type="button"
          className={favorite ? 'paw-button active' : 'paw-button'}
          aria-pressed={favorite}
          aria-label={`${place.name} 찜하기`}
          onClick={onToggleFavorite}
        >
          <img src={icons.pawButton} alt="" />
        </button>
      </div>
      <button type="button" className="place-card-body" onClick={onOpen}>
        {place.recommended && (
          <span className="place-badge">
            <img src={finalAssets.puppy} alt="" />
            {withJosa(petName, '를', '을')} 위한 추천
          </span>
        )}
        <strong>{place.name}</strong>
        <span className="place-meta">
          <img src={icons.pin} alt="" />
          {[place.address, ...place.tags].join('  ·  ')}
        </span>
        {(place.visitors || place.rating.count > 0) && (
          <span className="place-rating">
            {place.visitors && (
              <>
                <img src={finalAssets.pawOrange} alt="" />
                <span aria-label={`페타 이용자 ${place.visitors}명`}>{place.visitors}</span>
              </>
            )}
            {place.rating.count > 0 && (
              <>
                <img src={finalAssets.boneOrange} alt="" />
                <span aria-label={`평점 ${place.rating.score}, 후기 ${place.rating.count}개`}>
                  {place.rating.score} ({place.rating.count})
                </span>
              </>
            )}
          </span>
        )}
      </button>
    </article>
  )
}

/** 일정 타임라인 한 줄 */
export function TimelineItem({
  dot,
  variant,
  children,
}: {
  dot: string
  variant?: 'place' | 'last'
  children: ReactNode
}) {
  return (
    <li className={variant ? `timeline-item ${variant}` : 'timeline-item'}>
      <span className="timeline-dot">
        <img src={dot} alt="" />
      </span>
      <div className="timeline-content">{children}</div>
    </li>
  )
}

/** 뼈다귀 평점: 라디오 그룹처럼 화살표 키로 움직인다. */
export function RatingBones({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  const refs = useRef<Array<HTMLButtonElement | null>>([])
  const move = (next: number) => {
    const clamped = Math.min(5, Math.max(1, next))
    onChange(clamped)
    refs.current[clamped - 1]?.focus()
  }
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
      event.preventDefault()
      move(value + 1)
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
      event.preventDefault()
      move(value - 1)
    }
  }
  return (
    <div className="rating" role="radiogroup" aria-label="평점" onKeyDown={onKeyDown}>
      {[1, 2, 3, 4, 5].map((score) => (
        <button
          key={score}
          ref={(element) => {
            refs.current[score - 1] = element
          }}
          type="button"
          role="radio"
          aria-checked={value === score}
          aria-label={`${score}점`}
          tabIndex={value === score || (value === 0 && score === 1) ? 0 : -1}
          onClick={() => onChange(score)}
        >
          <img src={score <= value ? icons.boneOn : icons.boneOff} alt="" />
        </button>
      ))}
    </div>
  )
}
