/*
 * 시연용 가상 수치 [가정] — 관광공사 API에 후기·평점·이용자 수가 없어 id 시드로 생성. 실제 사용자 데이터가 아님.
 *
 * 같은 place id는 새로고침·재호출에도 항상 같은 값이다(문자열 해시 → mulberry32).
 * API 응답 변환(openApiMapping.ts)과 섞지 않는다. 이 값을 채우는 곳은 services/index.ts의 withDemoStats뿐이다.
 * 실제 후기·평점 데이터가 생기면 이 파일과 호출부만 지우면 된다.
 */
import type { Place } from '../domain/types'

/** 시연용 후기 문구 풀 [가정]. 실제 사용자 후기가 아니다. */
const REVIEW_POOL = [
  '리드줄만 잘 챙기면 편하게 쉬다 올 수 있어요.',
  '직원분들이 강아지를 반겨 주셔서 마음이 편했어요.',
  '주변에 산책할 곳이 있어서 좋았어요.',
  '물그릇을 챙겨 주셔서 감동이었어요.',
  '다른 강아지들과 마주쳐도 넓어서 여유로웠어요.',
  '주차장이 가까워서 짐 옮기기 편했어요.',
  '조용해서 겁 많은 아이도 잘 쉬었어요.',
  '미리 동반 조건을 확인하고 가니 수월했어요.',
  '다음 여행에도 또 가고 싶어요.',
  '이동 중에 지친 아이가 푹 쉬었어요.',
]

/** 문자열 → 32비트 해시(FNV-1a). */
function hash(text: string): number {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/** 시드가 같으면 같은 수열을 내는 의사난수 생성기. 0 이상 1 미만. */
function mulberry32(seed: number): () => number {
  let a = seed
  return () => {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export interface DemoStats {
  rating: Place['rating']
  visitors: Place['visitors']
  reviews: Place['reviews']
}

/**
 * 시연용 가상 수치 [가정] — 관광공사 API에 후기·평점·이용자 수가 없어 id 시드로 생성. 실제 사용자 데이터가 아님.
 * 평점 3.8~5.0(소수 1자리), 후기 수 4~160, 이용자 수 3~140(99 초과는 "99+"), 후기 문구 1~2개.
 */
export function demoStats(placeId: string): DemoStats {
  const random = mulberry32(hash(placeId))
  const between = (min: number, max: number) => min + Math.floor(random() * (max - min + 1))

  const score = Math.round((3.8 + random() * 1.2) * 10) / 10
  const count = between(4, 160)
  const visitors = between(3, 140)

  const first = between(0, REVIEW_POOL.length - 1)
  const picks = [first]
  if (random() < 0.6) picks.push((first + between(1, REVIEW_POOL.length - 1)) % REVIEW_POOL.length)
  const reviews = picks.map((i) => ({
    text: REVIEW_POOL[i],
    stars: random() < 0.75 ? 5 : 4,
    label: '반려견 동반 후기',
  }))

  return { rating: { score, count }, visitors: visitors > 99 ? '99+' : String(visitors), reviews }
}

/** 시연용 가상 수치 [가정]를 채운 Place. 원본은 바꾸지 않는다. */
export function withDemoStats(place: Place): Place {
  return { ...place, ...demoStats(place.id) }
}
