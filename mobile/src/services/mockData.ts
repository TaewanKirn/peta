import { finalAssets, photos } from '../assets'
import type { Hospital, Place, Species } from '../domain/types'

// 임시 데이터. 주소·좌표·병원은 실제 장소가 아니라 시연용 값이다.
export const MOCK_PLACES: Place[] = [
  {
    id: 'forest-stay',
    name: '숲속 스테이',
    category: 'stay',
    categoryLabel: '숙소',
    region: '양평',
    address: '경기 양평',
    lat: 37.492,
    lng: 127.49,
    themes: ['nature', 'walk'],
    tags: ['대형견 동반', '울타리 운동장'],
    photos: [photos.placeForest, photos.placeRiver],
    checks: [
      { icon: 'paw', text: '대형견 동반 가능' },
      { icon: 'fence', text: '울타리 운동장 · 반려견 샤워실' },
      { icon: 'shield', text: '입장 전 접종 증명 확인 필요' },
    ],
    reviews: [
      { text: '운동장이 넓어 함께 뛰어놀기 좋았어요.', stars: 5, label: '대형견 동반 후기' },
      { text: '몽이가 너무 신나게 놀더라구요.', stars: 5, label: '대형견 동반 후기' },
    ],
    visitors: '99+',
    rating: { score: 4.9, count: 137 },
    activity: '{pet} 울타리 운동장에서 신나게 뛰어놀기',
    nearbyInfo: '반려견 샤워실은 별관 1층에 있어요.',
    recommended: true,
  },
  {
    // 시안 두 번째 카드. 주소·좌표는 시연용 임의 값이다.
    id: 'cozy-mountain',
    name: '코지마운틴',
    category: 'stay',
    categoryLabel: '숙소',
    region: '양평',
    address: '경기 양평',
    lat: 37.5215,
    lng: 127.5304,
    themes: ['nature', 'activity'],
    tags: ['대형견 동반', '울타리 운동장'],
    photos: [finalAssets.placeCozy, photos.placeForest],
    checks: [
      { icon: 'paw', text: '대형견 동반 가능' },
      { icon: 'fence', text: '울타리 운동장 · 반려견 샤워실' },
      { icon: 'shield', text: '입장 전 접종 증명 확인 필요' },
    ],
    reviews: [{ text: '강이 보이는 마당에서 쉬기 좋았어요.', stars: 5, label: '대형견 동반 후기' }],
    visitors: '99+',
    rating: { score: 4.8, count: 96 },
    activity: '{pet} 강변 마당에서 뛰어놀기',
    nearbyInfo: '산책로 입구는 주차장 옆에 있어요.',
    recommended: true,
  },
]

export const MOCK_HOSPITALS: Hospital[] = [
  { id: 'h-1', name: '멍멍닥터 진료소', address: '서울특별시 마포구 12-1', lat: 37.5563, lng: 126.9236 },
  { id: 'h-2', name: '멍멍냥냥 병원', address: '서울특별시 노원구 1-23', lat: 37.6542, lng: 127.0568 },
  { id: 'h-3', name: '멍멍우리 병원', address: '경기도 화천 10-4', lat: 38.1061, lng: 127.7082 },
  { id: 'h-4', name: '양평 포레동물병원', address: '경기도 양평군 (샘플)', lat: 37.4915, lng: 127.4875 },
]

/** 품종과 크기. 크기 입력이 사라져 품종에서 정한다. 값은 임시 기준이다. */
export const MOCK_BREEDS: Record<Species, Array<{ name: string; sizeLabel: string }>> = {
  dog: [
    { name: '골든 리트리버', sizeLabel: '대형견' },
    { name: '골든 두들', sizeLabel: '대형견' },
    { name: '화이트 골든 리트리버', sizeLabel: '대형견' },
    { name: '래브라도 리트리버', sizeLabel: '대형견' },
    { name: '시베리안 허스키', sizeLabel: '대형견' },
    { name: '진돗개', sizeLabel: '중형견' },
    { name: '웰시 코기', sizeLabel: '중형견' },
    { name: '시바견', sizeLabel: '중형견' },
    { name: '비글', sizeLabel: '중형견' },
    { name: '푸들', sizeLabel: '소형견' },
    { name: '말티즈', sizeLabel: '소형견' },
    { name: '말티푸', sizeLabel: '소형견' },
    { name: '포메라니안', sizeLabel: '소형견' },
    { name: '치와와', sizeLabel: '소형견' },
    { name: '비숑 프리제', sizeLabel: '소형견' },
  ],
  cat: [
    { name: '코리안 숏헤어', sizeLabel: '반려묘' },
    { name: '러시안 블루', sizeLabel: '반려묘' },
    { name: '페르시안', sizeLabel: '반려묘' },
    { name: '스코티시 폴드', sizeLabel: '반려묘' },
    { name: '먼치킨', sizeLabel: '반려묘' },
    { name: '랙돌', sizeLabel: '반려묘' },
  ],
}

/** 목록에 없는 품종도 직접 입력할 수 있어 기본값을 둔다. */
export function sizeOfBreed(species: Species, breed: string): string {
  if (species === 'cat') return '반려묘'
  return MOCK_BREEDS.dog.find((item) => item.name === breed.trim())?.sizeLabel ?? '반려견'
}

export const REGIONS = ['양평', '가평', '강릉']

export interface Origin {
  id: string
  name: string
  address: string
  /** 카카오 검색 결과일 때만 있다. */
  lat?: number
  lng?: number
}

/** 출발지 검색 샘플 (지도 이미지는 시청역 주변) */
export const MOCK_ORIGINS: Origin[] = [
  { id: 'o-1', name: '시청역 1호선', address: '서울 중구 세종대로 지하 101' },
  { id: 'o-2', name: '시청역 2호선', address: '서울 중구 을지로 지하 23' },
  { id: 'o-3', name: '홍대입구역 2호선', address: '서울 마포구 양화로 지하 160' },
  { id: 'o-4', name: '강남역 2호선', address: '서울 강남구 강남대로 지하 396' },
  { id: 'o-5', name: '서울역', address: '서울 용산구 한강대로 405' },
]
