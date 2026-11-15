import { useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ui1 } from '../assets'
import { Autocomplete } from '../components/Autocomplete'
import { OptionGroup, PetAvatar } from '../components/controls'
import { PrimaryButton, ScreenFrame } from '../components/ScreenFrame'
import { maskBirthday, parseBirthday } from '../domain/format'
import type { Pet, PetDraft, Species } from '../domain/types'
import { services } from '../services'
import { emptyPetDraft, useApp } from '../state/AppState'

export type PetFieldErrors = Partial<Record<'species' | 'name' | 'breed' | 'birthday', string>>

export function validatePetBasics(draft: PetDraft, today: Date = new Date()): PetFieldErrors {
  const errors: PetFieldErrors = {}
  if (!draft.name.trim()) errors.name = '이름을 입력해 주세요.'
  const birthday = parseBirthday(draft.birthday)
  if (!draft.birthday.trim()) errors.birthday = '생년월일을 입력해 주세요.'
  else if (!birthday) errors.birthday = '2020.01.15 형식으로 입력해 주세요.'
  else if (new Date(birthday) > today) errors.birthday = '오늘 이전 날짜를 입력해 주세요.'
  if (!draft.species) errors.species = '반려동물 종류를 선택해 주세요.'
  if (!draft.breed.trim()) errors.breed = '종류를 입력해 주세요.'
  return errors
}

/** 사진을 줄여 저장소를 넘기지 않게 한다. */
function resizePhoto(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      const side = Math.min(image.width, image.height)
      const canvas = document.createElement('canvas')
      canvas.width = 320
      canvas.height = 320
      canvas
        .getContext('2d')
        ?.drawImage(image, (image.width - side) / 2, (image.height - side) / 2, side, side, 0, 0, 320, 320)
      URL.revokeObjectURL(url)
      resolve(canvas.toDataURL('image/jpeg', 0.85))
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('image load failed'))
    }
    image.src = url
  })
}

function draftFromPet(pet: Pet): PetDraft {
  const [year, month, day] = pet.birthday.split('-')
  return {
    editingId: pet.id,
    photo: pet.photo,
    species: pet.species,
    name: pet.name,
    breed: pet.breed,
    birthday: `${year}.${month}.${day}`,
    restIntervalMinutes: pet.restIntervalMinutes,
    hospital: pet.hospital,
  }
}

export function PetProfile1() {
  const navigate = useNavigate()
  const { state, update } = useApp()
  const draft = state.petDraft
  const [photoError, setPhotoError] = useState<string | null>(null)
  const [touchedBirthday, setTouchedBirthday] = useState(false)
  const errors = validatePetBasics(draft)
  const others = state.pets.filter((pet) => pet.id !== draft.editingId)

  const set = (patch: Partial<PetDraft>) => update((s) => ({ ...s, petDraft: { ...s.petDraft, ...patch } }))

  const onPhoto = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    try {
      setPhotoError(null)
      set({ photo: await resizePhoto(file) })
    } catch {
      setPhotoError('사진을 불러오지 못했어요. 다른 사진을 골라 주세요.')
    }
  }

  const showBirthdayError =
    Boolean(errors.birthday) && draft.birthday.length > 0 && (touchedBirthday || draft.birthday.length === 10)

  return (
    <ScreenFrame
      title="반려동물 등록"
      closable={false}
      progress={0.503}
      wideFooter
      footer={
        <PrimaryButton disabled={Object.keys(errors).length > 0} onClick={() => navigate('/signup/habit')}>
          여행 정보 작성하기
        </PrimaryButton>
      }
    >
      <h2 className="step-title">{'반려동물의\n프로필을 적어주세요'}</h2>

      <div className="pet-tabs">
        <button
          type="button"
          className="pet-tab add"
          onClick={() => {
            setPhotoError(null)
            update((s) => ({ ...s, petDraft: emptyPetDraft() }))
          }}
        >
          <span className="pet-tab-add">
            <img src={ui1.plusBig} alt="" />
          </span>
          <span className="name">추가</span>
        </button>
        <div className="pet-tab current">
          <span className="pet-tab-photo">
            <PetAvatar pet={{ photo: draft.photo, species: draft.species ?? 'dog', name: draft.name }} size={80} crop={false} />
            <label className="camera-badge">
              <img src={ui1.camera} alt="" />
              <input className="visually-hidden" type="file" accept="image/*" aria-label="사진 추가" onChange={onPhoto} />
            </label>
          </span>
          <span className="name">{draft.name || '새 친구'}</span>
        </div>
        {others.map((pet) => (
          <button
            key={pet.id}
            type="button"
            className="pet-tab"
            onClick={() => update((s) => ({ ...s, petDraft: draftFromPet(pet) }))}
          >
            <span className="pet-tab-photo">
              <PetAvatar pet={pet} size={80} crop={false} />
            </span>
            <span className="name">{pet.name}</span>
          </button>
        ))}
      </div>
      {photoError && <p className="field-error center" role="alert">{photoError}</p>}

      <form className="pet-form" noValidate onSubmit={(event) => event.preventDefault()}>
        <div className="line-field">
          <label htmlFor="pet-name">이름</label>
          <div className="line-input">
            <input id="pet-name" value={draft.name} onChange={(event) => set({ name: event.target.value })} />
            {draft.name && (
              <button type="button" className="line-icon" aria-label="이름 지우기" onClick={() => set({ name: '' })}>
                <span className="clear-x" aria-hidden="true">×</span>
              </button>
            )}
          </div>
        </div>

        <div className="line-field">
          <label htmlFor="pet-birthday">생년월일</label>
          <div className="line-input">
            <input
              id="pet-birthday"
              inputMode="numeric"
              placeholder="2020.01.15"
              value={draft.birthday}
              aria-invalid={showBirthdayError}
              onBlur={() => setTouchedBirthday(true)}
              onChange={(event) => set({ birthday: maskBirthday(event.target.value) })}
            />
            <span className="line-icon" aria-hidden="true">
              <img src={ui1.dropdown} alt="" />
            </span>
          </div>
          {showBirthdayError && <p className="field-error" role="alert">{errors.birthday}</p>}
        </div>

        <OptionGroup<Species>
          label="반려동물"
          value={draft.species}
          options={[
            { value: 'dog', label: '강아지' },
            { value: 'cat', label: '고양이' },
          ]}
          onChange={(species) => set({ species, breed: species === draft.species ? draft.breed : '' })}
        />

        <Autocomplete<string>
          label="종류"
          value={draft.breed}
          disabled={!draft.species}
          placeholder={draft.species ? undefined : '반려동물을 먼저 골라주세요'}
          search={(query) => services.breeds.search(draft.species ?? 'dog', query)}
          optionLabel={(name) => name}
          optionKey={(name) => name}
          onInput={(breed) => set({ breed })}
          onSelect={(breed) => set({ breed })}
          emptyText="목록에 없어도 그대로 입력할 수 있어요."
          errorText="목록을 불러오지 못했어요."
        />
      </form>
    </ScreenFrame>
  )
}
