import type { Hospital, Place, PlaceCategory } from '../domain/types'
import type { Origin } from './mockData'

/*
 * 카카오 로컬·한국관광공사 응답을 화면 모델로 바꾼다. 네트워크 없이 테스트할 수 있게 순수 함수로 둔다.
 * API에 없는 값(후기·평점·이용자 수·테마)은 만들지 않고 비운다.
 */

// ---------------- 카카오 로컬 ----------------

export interface KakaoDocument {
  id: string
  place_name: string
  category_name?: string
  address_name?: string
  road_address_name?: string
  phone?: string
  x: string // 경도
  y: string // 위도
}

/** 빈 문자열은 Number('')가 0이라 좌표로 오인되므로 NaN으로 본다. */
const coord = (value?: string) => (value?.trim() ? Number(value) : NaN)

const addressOf = (d: KakaoDocument) => d.road_address_name || d.address_name || ''

function kakaoDocuments(json: unknown): KakaoDocument[] {
  const docs = (json as { documents?: unknown })?.documents
  return Array.isArray(docs) ? (docs as KakaoDocument[]) : []
}

/** category_name이 "…> 반려동물 > 동물병원"인 곳만 병원으로 본다. 좌표가 숫자가 아니면 뺀다. */
export function kakaoToHospitals(json: unknown): Hospital[] {
  return kakaoDocuments(json).flatMap((d) => {
    const lat = coord(d.y)
    const lng = coord(d.x)
    if (!d.category_name?.includes('동물병원') || !Number.isFinite(lat) || !Number.isFinite(lng)) return []
    const phone = d.phone?.trim()
    return [{ id: `kakao-${d.id}`, name: d.place_name, address: addressOf(d), lat, lng, ...(phone ? { phone } : {}) }]
  })
}

export function kakaoToOrigins(json: unknown): Origin[] {
  return kakaoDocuments(json).flatMap((d) => {
    const lat = coord(d.y)
    const lng = coord(d.x)
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return []
    return [{ id: `kakao-${d.id}`, name: d.place_name, address: addressOf(d), lat, lng }]
  })
}

// ---------------- 한국관광공사 반려동물 동반여행 ----------------

/** 화면의 지역 이름 → areaCode(시도) · sigunguCode(시군구). areaCode2 응답으로 확인한 값(2026-11-12). */
export const REGION_CODES: Record<string, { areaCode: string; sigunguCode: string }> = {
  양평: { areaCode: '31', sigunguCode: '19' },
  가평: { areaCode: '31', sigunguCode: '1' },
  강릉: { areaCode: '32', sigunguCode: '1' },
}

/** 탭 → contentTypeId. 숙박 32, 관광지 12 · 문화시설 14 · 레포츠 28 · 음식점 39, 여행코스 25. */
export const CATEGORY_TYPES: Record<PlaceCategory, string[]> = {
  stay: ['32'],
  activity: ['12', '14', '28', '39'],
  course: ['25'],
}

const CATEGORY_LABEL: Record<PlaceCategory, string> = { stay: '숙소', activity: '활동', course: '코스' }
const ACTIVITY_TEXT: Record<PlaceCategory, string> = {
  stay: '{pet} 편하게 쉬어가기',
  activity: '{pet} 함께 둘러보기',
  course: '{pet} 함께 걷기',
}

export function categoryOfType(contentTypeId: string): PlaceCategory | null {
  const found = (Object.keys(CATEGORY_TYPES) as PlaceCategory[]).find((c) => CATEGORY_TYPES[c].includes(contentTypeId))
  return found ?? null
}

export interface TourItem {
  contentid?: string
  contenttypeid?: string
  title?: string
  addr1?: string
  mapx?: string
  mapy?: string
  firstimage?: string
  overview?: string
}

export interface PetTourInfo {
  acmpyTypeCd?: string
  acmpyPsblCpam?: string
  acmpyNeedMtr?: string
  relaPosesFclty?: string
  relaFrnshPrdlst?: string
  etcAcmpyInfo?: string
}

/** response.body.items.item[]. 결과가 없으면 items가 빈 문자열이다. 오류 코드면 예외. */
export function tourItems<T>(json: unknown): T[] {
  const response = (json as { response?: { header?: { resultCode?: string }; body?: { items?: unknown } } })?.response
  if (response?.header?.resultCode !== '0000') throw new Error('tour api error')
  const items = response.body?.items
  const item = typeof items === 'object' && items !== null ? (items as { item?: unknown }).item : undefined
  if (Array.isArray(item)) return item as T[]
  return item ? [item as T] : []
}

/** "경기도 양평군 청운면 …" → "경기 양평". 시도는 앞 두 글자, 시군구는 시·군 접미를 뗀다. */
export function shortAddress(addr1: string): string {
  const [sido = '', sigungu = ''] = addr1.trim().split(/\s+/)
  const city = sigungu.replace(/(시|군)$/, '')
  return [sido.slice(0, 2), city].filter(Boolean).join(' ')
}

/** 시군구 이름(양평군·강릉시)을 화면 지역 이름(양평·강릉)으로. 목록에 없으면 시군구 그대로. */
export function regionOf(addr1: string): string {
  return shortAddress(addr1).split(' ')[1] ?? ''
}

export function tourToPlace(
  item: TourItem,
  options: { region?: string; fallbackPhoto: string; petTour?: PetTourInfo | null },
): Place | null {
  const lat = coord(item.mapy)
  const lng = coord(item.mapx)
  const category = categoryOfType(item.contenttypeid ?? '')
  if (!item.contentid || !item.title || !category || !Number.isFinite(lat) || !Number.isFinite(lng)) return null
  const addr1 = item.addr1 ?? ''
  const info = options.petTour ?? null
  return {
    id: `tour-${item.contentid}`,
    name: item.title.trim(),
    category,
    categoryLabel: CATEGORY_LABEL[category],
    region: options.region ?? regionOf(addr1),
    address: shortAddress(addr1) || addr1,
    lat,
    lng,
    themes: ['nature', 'walk', 'activity'], // API에 테마가 없어 테마로는 거르지 않는다.
    tags: [],
    photos: [item.firstimage || options.fallbackPhoto],
    checks: info ? petTourChecks(info) : [],
    reviews: [],
    visitors: '',
    rating: { score: 0, count: 0 },
    activity: ACTIVITY_TEXT[category],
    nearbyInfo: info ? firstLine(info.etcAcmpyInfo) : '',
    recommended: false,
  }
}

const firstLine = (text?: string) => (text ?? '').split(/\r?\n/).map((s) => s.trim()).find(Boolean) ?? ''

/** detailPetTour2 → 「가기 전 확인해요!」 항목. 비어 있는 필드는 항목을 만들지 않는다. */
export function petTourChecks(info: PetTourInfo): Place['checks'] {
  const rows: Array<[Place['checks'][number]['icon'], string, string | undefined]> = [
    ['paw', '반려견 동반', info.acmpyTypeCd],
    ['paw', '동반 가능 동물', info.acmpyPsblCpam],
    ['shield', '필수 조건', info.acmpyNeedMtr],
    ['fence', '갖춰진 시설', info.relaPosesFclty],
    ['fence', '비치 물품', info.relaFrnshPrdlst],
    ['shield', '안내', firstLine(info.etcAcmpyInfo)],
  ]
  return rows.flatMap(([icon, label, value]) => (value?.trim() ? [{ icon, text: `${label}: ${value.trim()}` }] : []))
}
