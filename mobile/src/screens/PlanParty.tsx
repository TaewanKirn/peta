import { useNavigate } from 'react-router-dom'
import { icons, photos } from '../assets'
import { CheckBadge, PetAvatar } from '../components/controls'
import { PrimaryButton, ScreenFrame } from '../components/ScreenFrame'
import { petSummary } from '../domain/trip'
import { emptyPetDraft, selectParty, useApp } from '../state/AppState'

export function PlanParty() {
  const navigate = useNavigate()
  const { state, update } = useApp()
  const { pets, guardians } = selectParty(state)

  const togglePet = (id: string) =>
    update((s) => ({
      ...s,
      party: {
        ...s.party,
        petIds: s.party.petIds.includes(id) ? s.party.petIds.filter((x) => x !== id) : [...s.party.petIds, id],
      },
    }))

  const toggleGuardian = (id: string) =>
    update((s) => ({
      ...s,
      party: {
        ...s.party,
        guardianIds: s.party.guardianIds.includes(id)
          ? s.party.guardianIds.filter((x) => x !== id)
          : [...s.party.guardianIds, id],
      },
    }))

  const addPet = () => {
    update((s) => ({ ...s, petDraft: emptyPetDraft() }))
    navigate('/signup/pet')
  }

  const addGuardian = () =>
    update((s) => {
      const order = s.guardians.length
      const id = `guardian-${order + 1}`
      return {
        ...s,
        guardians: [...s.guardians, { id, name: `동행 보호자 ${order}`, avatar: 'companion' }],
        party: { ...s.party, guardianIds: [...s.party.guardianIds, id] },
      }
    })

  const petLabel =
    pets.length === 0
      ? '0마리'
      : pets.length === 1
        ? `${pets[0].name} (1마리)`
        : `${pets[0].name} 외 ${pets.length - 1}마리`

  return (
    <ScreenFrame
      title="여행 계획"
      wideFooter
      footer={
        <PrimaryButton disabled={pets.length === 0 || guardians.length === 0} onClick={() => navigate('/plan/trip')}>
          선택 완료
        </PrimaryButton>
      }
    >
      <div className="page">
        <h2 className="page-title">
          누구와 함께
          <br />
          떠날까요?
        </h2>
        <p className="page-sub">함께 갈 반려동물과 보호자를 선택해 주세요.</p>

        <div className="section-heading party-section-first">
          <h3>나의 반려동물</h3>
        </div>
        <ul className="party-pets">
          {state.pets.map((pet) => {
            const selected = state.party.petIds.includes(pet.id)
            return (
              <li key={pet.id}>
                <button
                  type="button"
                  className={selected ? 'party-pet selected' : 'party-pet'}
                  aria-pressed={selected}
                  onClick={() => togglePet(pet.id)}
                >
                  <span className="party-pet-photo">
                    <PetAvatar pet={pet} size={80} crop={false} />
                    {selected && <CheckBadge />}
                  </span>
                  <strong>{pet.name}</strong>
                  <small>{petSummary(pet)}</small>
                </button>
              </li>
            )
          })}
          <li>
            <button type="button" className="party-pet" onClick={addPet}>
              <span className="party-pet-photo">
                <span className="add-circle">
                  <img src={icons.plusGray} alt="" />
                </span>
              </span>
              <span className="add-label">추가</span>
            </button>
          </li>
        </ul>
        {state.pets.length === 0 && <p className="field-note">등록한 반려동물이 없어요. 추가를 눌러 등록해 주세요.</p>}

        <hr className="divider party-divider" />

        <div className="section-heading">
          <h3>보호자</h3>
        </div>
        <ul className="party-guardians">
          {state.guardians.map((guardian) => {
            const selected = state.party.guardianIds.includes(guardian.id)
            return (
              <li key={guardian.id}>
                <button
                  type="button"
                  className={selected ? 'guardian-card selected' : 'guardian-card'}
                  aria-pressed={selected}
                  onClick={() => toggleGuardian(guardian.id)}
                >
                  <span className="guardian-inner">
                    <img
                      className={guardian.avatar === 'me' ? 'zoom' : undefined}
                      src={guardian.avatar === 'me' ? photos.guardianMe : photos.guardianCompanion}
                      alt=""
                    />
                    {guardian.name}
                  </span>
                  {selected && <CheckBadge />}
                </button>
              </li>
            )
          })}
          <li>
            <button type="button" className="guardian-card add" onClick={addGuardian}>
              <img src={icons.plusGray} alt="" />
              <span className="add-label">추가</span>
            </button>
          </li>
        </ul>

        <dl className="party-summary">
          <div>
            <dt>함께하는 보호자</dt>
            <dd>{guardians.length}명</dd>
          </div>
          <div>
            <dt>함께하는 반려동물</dt>
            <dd>{petLabel}</dd>
          </div>
        </dl>
      </div>
    </ScreenFrame>
  )
}
