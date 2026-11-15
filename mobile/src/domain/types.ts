export type AuthProvider = 'kakao' | 'naver' | 'pleos' | 'email'

export interface AuthState {
  provider: AuthProvider | null
  status: 'idle' | 'loading' | 'success' | 'error'
  error: string | null
  displayName: string | null
}

export type Species = 'dog' | 'cat'

export interface Hospital {
  id: string
  name: string
  address: string
  lat: number
  lng: number
  /** 카카오 검색 결과에 있을 때만. 예: 033-645-5575 */
  phone?: string
}

/** 입력 중인 반려동물. 검증을 통과하면 Pet으로 저장한다. */
export interface PetDraft {
  editingId: string | null
  photo: string | null
  species: Species | null
  name: string
  breed: string
  /** 화면 표기 그대로: 2020.01.15 */
  birthday: string
  restIntervalMinutes: number
  hospital: Hospital | null
}

export interface Pet {
  id: string
  photo: string | null
  species: Species
  name: string
  breed: string
  /** 소형견·중형견·대형견·반려묘. 품종에서 정한다. */
  sizeLabel: string
  /** YYYY-MM-DD */
  birthday: string
  restIntervalMinutes: number
  hospital: Hospital | null
}

export interface Guardian {
  id: string
  name: string
  avatar: 'me' | 'companion'
}

export type ThemeId = 'nature' | 'walk' | 'activity'
export type PlaceCategory = 'stay' | 'activity' | 'course'

export interface Place {
  id: string
  name: string
  category: PlaceCategory
  categoryLabel: string
  /** 여행 지역 검색 키: 양평 */
  region: string
  /** 장소 표기: 경기 양평 */
  address: string
  /** 임의 좌표. 실제 장소 API 연동 전까지 내비 경로 요청에 쓴다. */
  lat: number
  lng: number
  themes: ThemeId[]
  /** 카드 설명: 대형견 동반, 울타리 운동장 */
  tags: string[]
  photos: string[]
  checks: Array<{ icon: 'paw' | 'fence' | 'shield'; text: string }>
  reviews: Array<{ text: string; stars: number; label: string }>
  /** 페타 이용자 수 표시 (99+) */
  visitors: string
  rating: { score: number; count: number }
  /** 차량 C07·C08 안내 문구 */
  activity: string
  nearbyInfo: string
  recommended: boolean
}

export interface ItineraryItem {
  id: string
  day: 1 | 2
  name: string
}

export interface TripDraft {
  region: string
  /** YYYY-MM-DD */
  startDate: string
  endDate: string
  theme: ThemeId
  placeId: string | null
  origin: string
  items: ItineraryItem[]
}

export interface ReviewDraft {
  placeId: string | null
  rating: number
  drivingTags: string[]
  comment: string
}

/**
 * 모바일이 저장하고 차량 앱이 읽는 여행 데이터.
 * 차량 앱 `remote/TripJson.kt`와 같은 형식을 유지해야 한다.
 */
export interface CarTrip {
  id: string
  title: string
  dateLabel: string
  guardianCount: number
  origin: string
  theme: string
  pets: Array<{
    name: string
    species: Species
    breed: string
    age: number
    sizeLabel: string
    restIntervalMinutes: number
    regularHospital: { id: string; name: string; address: string; lat: number; lng: number; phone?: string } | null
  }>
  destination: {
    place: { id: string; name: string; address: string; lat: number; lng: number }
    activity: string
    nearbyInfo: string
  }
  savedAt: string
}
