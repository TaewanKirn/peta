import { useRef } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ui1 } from '../assets'
import { Autocomplete } from '../components/Autocomplete'
import { PrimaryButton, ScreenFrame } from '../components/ScreenFrame'
import { RestIntervalPicker } from '../components/WheelPicker'
import { parseBirthday, withJosa } from '../domain/format'
import type { Hospital, Pet } from '../domain/types'
import { services } from '../services'
import { sizeOfBreed } from '../services/mockData'
import { emptyPetDraft, useApp } from '../state/AppState'
import { validatePetBasics } from './PetProfile1'

/** 이름은 강조하고 조사만 따로 붙인다. */
function NameWith({ name }: { name: string }) {
  return (
    <>
      <em>{name}</em>
      {withJosa(name, '와', '과').slice(name.length)}
    </>
  )
}

export function PetProfile2() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const question = params.get('q') === '2' ? 2 : 1
  const { state, update } = useApp()
  const draft = state.petDraft
  const saved = useRef(false)
  const setDraft = (patch: Partial<typeof draft>) =>
    update((s) => ({ ...s, petDraft: { ...s.petDraft, ...patch } }))

  // 앞 단계를 거치지 않고 들어오면 돌려보낸다. 저장 직후에는 입력값이 비워진 채 다음 화면으로 넘어가는 중이다.
  if (Object.keys(validatePetBasics(draft)).length > 0) {
    return saved.current ? null : <Navigate to="/signup/pet" replace />
  }

  const save = () => {
    saved.current = true
    const pet: Pet = {
      id: draft.editingId ?? `pet-${Date.now()}`,
      photo: draft.photo,
      species: draft.species!,
      name: draft.name.trim(),
      breed: draft.breed.trim(),
      sizeLabel: sizeOfBreed(draft.species!, draft.breed),
      birthday: parseBirthday(draft.birthday)!,
      restIntervalMinutes: draft.restIntervalMinutes,
      hospital: draft.hospital,
    }
    update((s) => ({
      ...s,
      pets: [...s.pets.filter((p) => p.id !== pet.id), pet],
      party: { ...s.party, petIds: [...s.party.petIds.filter((id) => id !== pet.id), pet.id] },
      petDraft: emptyPetDraft(),
    }))
    navigate('/plan/party', { replace: true })
  }

  return (
    <ScreenFrame
      title="반려동물 등록"
      closable={false}
      progress={0.78}
      wideFooter
      footer={
        question === 1 ? (
          <PrimaryButton
            disabled={draft.restIntervalMinutes <= 0}
            onClick={() => navigate('/signup/habit?q=2')}
          >
            다음
          </PrimaryButton>
        ) : (
          // PO 확인: 원본은 병원을 고르기 전까지 비활성이라 단골 병원이 없는 사용자가 막힌다.
          <PrimaryButton disabled={!draft.hospital} onClick={save}>
            등록 완료
          </PrimaryButton>
        )
      }
    >
      <h2 className="step-title">
        <NameWith name={draft.name} />
        {' 여행할 때\n참고할 정보들을\n알려주세요'}
      </h2>
      <img className="road" src={ui1.habitRoad} alt="" />

      {question === 1 ? (
        <>
          <h3 className="habit-q">
            <strong>Q1.</strong>
            <NameWith name={draft.name} /> 차로 이동할 때
            <br />
            얼마나 자주 쉬나요?
          </h3>
          <RestIntervalPicker
            minutes={draft.restIntervalMinutes}
            onChange={(restIntervalMinutes) => setDraft({ restIntervalMinutes })}
          />
        </>
      ) : (
        <>
          <h3 className="habit-q">
            <strong>Q2.</strong>
            <NameWith name={draft.name} /> 평소 다니는
            <br />
            동물병원은 어디인가요?
          </h3>
          <div className="hospital-field">
            <HospitalField
              hospital={draft.hospital}
              onChange={(hospital) => setDraft({ hospital })}
            />
          </div>
        </>
      )}
    </ScreenFrame>
  )
}

function HospitalField({ hospital, onChange }: { hospital: Hospital | null; onChange: (h: Hospital | null) => void }) {
  const { state, update } = useApp()
  const query = state.hospitalQuery
  return (
    <Autocomplete<Hospital>
      label="동물병원"
      hideLabel
      placeholder="동물병원 이름을 입력해주세요"
      value={query}
      selectedLabel={hospital ? `${hospital.name}\n(${hospital.address})` : undefined}
      search={(q) => services.hospitals.search(q)}
      optionLabel={(option) => `${option.name} (${option.address})`}
      optionKey={(option) => option.id}
      onInput={(value) => update((s) => ({ ...s, hospitalQuery: value }))}
      onSelect={(option) => onChange(option)}
      onClearSelection={() => {
        update((s) => ({ ...s, hospitalQuery: '' }))
        onChange(null)
      }}
      emptyText="검색 결과가 없어요."
      errorText="병원을 찾지 못했어요. 다시 입력해 주세요."
    />
  )
}
