import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { AuthState, Guardian, Pet, PetDraft, ReviewDraft, TripDraft } from '../domain/types'

export interface AppState {
  auth: AuthState
  travelStyles: string[]
  petDraft: PetDraft
  /** 동물병원 검색어. 뒤로 갔다 와도 남긴다. */
  hospitalQuery: string
  pets: Pet[]
  guardians: Guardian[]
  party: { petIds: string[]; guardianIds: string[] }
  tripDraft: TripDraft
  reviewDraft: ReviewDraft
  favorites: string[]
}

export const emptyPetDraft = (): PetDraft => ({
  editingId: null,
  photo: null,
  species: null,
  name: '',
  breed: '',
  birthday: '',
  restIntervalMinutes: 75,
  hospital: null,
})

export const emptyTripDraft = (): TripDraft => ({
  region: '양평',
  startDate: '2026-11-18',
  endDate: '2026-11-19',
  theme: 'nature',
  placeId: null,
  origin: '',
  items: [],
})

export const emptyReviewDraft = (): ReviewDraft => ({ placeId: null, rating: 0, drivingTags: [], comment: '' })

export const initialState = (): AppState => ({
  auth: { provider: null, status: 'idle', error: null, displayName: null },
  travelStyles: [],
  petDraft: emptyPetDraft(),
  hospitalQuery: '',
  pets: [],
  guardians: [
    { id: 'me', name: '나', avatar: 'me' },
    { id: 'companion', name: '동행 보호자', avatar: 'companion' },
  ],
  party: { petIds: [], guardianIds: ['me', 'companion'] },
  tripDraft: emptyTripDraft(),
  reviewDraft: emptyReviewDraft(),
  favorites: [],
})

export const STORAGE_KEY = 'peta.app.v2'

function load(): AppState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? { ...initialState(), ...JSON.parse(raw) } : initialState()
  } catch {
    return initialState()
  }
}

interface AppContextValue {
  state: AppState
  update: (change: (state: AppState) => AppState) => void
}

const AppContext = createContext<AppContextValue | null>(null)

export function AppStateProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AppState>(load)
  const update = useCallback((change: (state: AppState) => AppState) => setState(change), [])

  // 새로고침해도 편집값을 유지한다. 서버 연동 전까지 등록한 반려동물의 저장소이기도 하다.
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    } catch {
      // 저장 공간이 없어도 현재 세션은 계속 쓸 수 있다.
    }
  }, [state])

  const value = useMemo(() => ({ state, update }), [state, update])
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

export function useApp(): AppContextValue {
  const value = useContext(AppContext)
  if (!value) throw new Error('useApp must be used inside AppStateProvider')
  return value
}

/** 여행에 함께 가는 반려동물과 보호자 */
export function selectParty(state: AppState) {
  return {
    pets: state.party.petIds.flatMap((id) => state.pets.filter((pet) => pet.id === id)),
    guardians: state.party.guardianIds.flatMap((id) => state.guardians.filter((guardian) => guardian.id === id)),
  }
}
