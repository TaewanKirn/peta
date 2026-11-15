import { photos } from '../assets'
import type { Hospital, Place, PlaceCategory } from '../domain/types'
import type { Origin } from './mockData'
import {
  CATEGORY_TYPES,
  kakaoToHospitals,
  kakaoToOrigins,
  REGION_CODES,
  tourItems,
  tourToPlace,
  type PetTourInfo,
  type TourItem,
} from './openApiMapping'

/*
 * 개발 서버 프록시(/api/kakao, /api/tour) 호출. 실패(키 없음·네트워크·업스트림 오류)는 예외로 던지고,
 * 호출하는 services가 mock으로 폴백한다.
 */

async function getJson(path: string, params: Record<string, string | number>): Promise<unknown> {
  const query = new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)]))
  const response = await fetch(`${path}?${query}`)
  if (!response.ok) throw new Error(`${path} ${response.status}`)
  return response.json()
}

const tour = (operation: string, params: Record<string, string | number>) =>
  getJson(`/api/tour/${operation}`, params)

export async function searchHospitals(query: string): Promise<Hospital[]> {
  const q = query.trim()
  return kakaoToHospitals(await getJson('/api/kakao/keyword', { query: q ? `${q} 동물병원` : '동물병원', size: 15 }))
}

export async function searchOrigins(query: string): Promise<Origin[]> {
  return kakaoToOrigins(await getJson('/api/kakao/keyword', { query: query.trim(), size: 10 }))
}

/** 지역·탭에 맞는 장소. 지역 코드를 모르면 예외(→ mock). */
export async function listTourPlaces(region: string, category: PlaceCategory): Promise<Place[]> {
  const codes = REGION_CODES[region]
  if (!codes) throw new Error(`unknown region ${region}`)
  const results = await Promise.all(
    CATEGORY_TYPES[category].map(async (type) =>
      tourItems<TourItem>(await tour('areaBasedList2', { ...codes, contentTypeId: type, numOfRows: 30, arrange: 'C' })),
    ),
  )
  return results
    .flat()
    .flatMap((item) => tourToPlace(item, { region, fallbackPhoto: photos.placeForest }) ?? [])
}

/** 상세: 공통 정보 + 반려동물 동반 조건. 동반 조건이 없어도 장소는 돌려준다. */
export async function getTourPlace(id: string): Promise<Place | null> {
  const contentId = id.replace(/^tour-/, '')
  const [common, pet] = await Promise.all([
    tour('detailCommon2', { contentId }),
    tour('detailPetTour2', { contentId }).catch(() => null),
  ])
  const item = tourItems<TourItem>(common)[0]
  if (!item) return null
  const petTour = pet ? (tourItems<PetTourInfo>(pet)[0] ?? null) : null
  return tourToPlace(item, { fallbackPhoto: photos.placeForest, petTour })
}
