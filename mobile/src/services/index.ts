import type { AuthProvider, CarTrip, Hospital, Place, PlaceCategory, ReviewDraft, Species, ThemeId } from '../domain/types'
import { MOCK_BREEDS, MOCK_HOSPITALS, MOCK_ORIGINS, MOCK_PLACES, type Origin } from './mockData'
import { withDemoStats } from './demoStats'
import { getTourPlace, listTourPlaces, searchHospitals, searchOrigins } from './openApi'

/*
 * 화면은 이 파일의 services 객체만 호출한다.
 * tripSync는 실제 HTTP다. hospitals · origins · places는 개발 서버 프록시(카카오 로컬 · 한국관광공사)를 먼저 부르고,
 * 키가 없거나 호출이 실패하면 mock으로 돌아간다. 성공했지만 결과가 없으면 빈 목록 그대로 보여 준다.
 * 관광공사 장소의 후기·평점·이용자 수는 API에 없어 demoStats(시연용 가상 수치 [가정])로 채운다.
 */

export interface AuthService {
  signIn(provider: AuthProvider): Promise<{ displayName: string }>
}

export interface HospitalService {
  search(query: string): Promise<Hospital[]>
}

export interface OriginService {
  search(query: string): Promise<Origin[]>
}

export interface BreedService {
  search(species: Species, query: string): Promise<string[]>
}

export interface PlaceService {
  list(filter: { region: string; theme: ThemeId; category: PlaceCategory }): Promise<Place[]>
  get(id: string): Promise<Place | null>
}

export interface ReviewService {
  submit(review: ReviewDraft): Promise<void>
}

/** 차량 앱과의 연결 지점. 모바일 화면은 Pleos/NaviHelper를 직접 호출하지 않는다. */
export interface TripSyncService {
  save(trip: CarTrip): Promise<void>
}

const wait = (ms = 250) => new Promise((resolve) => setTimeout(resolve, ms))

export const services: {
  auth: AuthService
  hospitals: HospitalService
  origins: OriginService
  breeds: BreedService
  places: PlaceService
  reviews: ReviewService
  tripSync: TripSyncService
} = {
  auth: {
    async signIn() {
      await wait()
      return { displayName: '태완' }
    },
  },
  hospitals: {
    async search(query) {
      try {
        return await searchHospitals(query)
      } catch {
        const q = query.trim()
        return MOCK_HOSPITALS.filter((hospital) => hospital.name.includes(q))
      }
    },
  },
  origins: {
    async search(query) {
      const q = query.trim()
      if (!q) return []
      try {
        return await searchOrigins(q)
      } catch {
        return MOCK_ORIGINS.filter((o) => o.name.includes(q) || o.address.includes(q))
      }
    },
  },
  breeds: {
    async search(species, query) {
      await wait(0)
      const q = query.trim()
      return MOCK_BREEDS[species].map((item) => item.name).filter((name) => name.includes(q))
    },
  },
  places: {
    async list({ region, theme, category }) {
      try {
        return (await listTourPlaces(region, category)).map(withDemoStats)
      } catch {
        await wait()
        return MOCK_PLACES.filter(
          (place) => place.region === region && place.category === category && place.themes.includes(theme),
        )
      }
    },
    async get(id) {
      // tour- 로 시작하는 id는 관광공사 장소다. 실패하면 화면이 오류 안내와 재시도를 보여 준다.
      if (id.startsWith('tour-')) {
        const place = await getTourPlace(id)
        return place && withDemoStats(place)
      }
      await wait(0)
      return MOCK_PLACES.find((place) => place.id === id) ?? null
    },
  },
  reviews: {
    async submit() {
      await wait()
    },
  },
  tripSync: {
    async save(trip) {
      const response = await fetch('/api/trip', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(trip),
      })
      if (!response.ok) throw new Error(`trip sync failed: ${response.status}`)
    },
  },
}
