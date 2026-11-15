import { describe, expect, it } from 'vitest'
import {
  kakaoToHospitals,
  kakaoToOrigins,
  petTourChecks,
  regionOf,
  shortAddress,
  tourItems,
  tourToPlace,
} from './openApiMapping'

// 2026-11-12 실제 응답에서 일부 필드만 뗀 값
const kakao = {
  documents: [
    { id: '1163670563', place_name: '위드동물병원', category_name: '가정,생활 > 반려동물 > 동물병원',
      phone: '033-645-5575', address_name: '강원특별자치도 강릉시 홍제동 1026-5', road_address_name: '강원특별자치도 강릉시 선수촌로63번길 18-7',
      x: '128.868337843353', y: '37.7570406578513' },
    { id: '9', place_name: '강릉병원', category_name: '의료,건강 > 병원 > 종합병원', address_name: '강릉시', x: '128.9', y: '37.7' },
    { id: '8', place_name: '좌표없는동물병원', category_name: '반려동물 > 동물병원', address_name: 'x', x: '', y: 'abc' },
    { id: '7', place_name: '빈좌표동물병원', category_name: '반려동물 > 동물병원', address_name: 'x', x: '', y: '' },
  ],
}

describe('카카오 변환', () => {
  it('동물병원만 남기고 x=경도, y=위도로 읽는다. 도로명 주소를 우선한다', () => {
    expect(kakaoToHospitals(kakao)).toEqual([
      { id: 'kakao-1163670563', name: '위드동물병원', address: '강원특별자치도 강릉시 선수촌로63번길 18-7',
        lat: 37.7570406578513, lng: 128.868337843353, phone: '033-645-5575' },
    ])
  })

  it('phone이 비어 있으면 키를 만들지 않는다', () => {
    const [h] = kakaoToHospitals({ documents: [{ ...kakao.documents[0], phone: '' }] })
    expect('phone' in h).toBe(false)
  })

  it('출발지는 분류와 무관하게 좌표가 있으면 모두 쓴다', () => {
    expect(kakaoToOrigins(kakao).map((o) => o.name)).toEqual(['위드동물병원', '강릉병원'])
  })

  it('documents가 없거나 형식이 다르면 빈 목록', () => {
    expect(kakaoToHospitals({})).toEqual([])
    expect(kakaoToHospitals(null)).toEqual([])
  })
})

const stay = {
  addr1: '경기도 양평군 청운면 가현길 440-1', contentid: '3532734', contenttypeid: '32', title: '메리도그하우스',
  mapx: '127.7298401806', mapy: '37.5207260307', firstimage: '',
}
const listJson = { response: { header: { resultCode: '0000' }, body: { items: { item: [stay] } } } }

describe('관광공사 변환', () => {
  it('주소를 "경기 양평"으로 줄인다', () => {
    expect(shortAddress('경기도 양평군 청운면 가현길 440-1')).toBe('경기 양평')
    expect(shortAddress('강원특별자치도 강릉시 운정길 141')).toBe('강원 강릉')
    expect(regionOf('강원특별자치도 강릉시 운정길 141')).toBe('강릉')
  })

  it('숙소를 Place로 바꾸고, API에 없는 값은 비운다', () => {
    const [item] = tourItems<typeof stay>(listJson)
    const place = tourToPlace(item, { region: '양평', fallbackPhoto: 'fallback.png' })!
    expect(place).toMatchObject({
      id: 'tour-3532734', name: '메리도그하우스', category: 'stay', categoryLabel: '숙소', region: '양평',
      address: '경기 양평', lat: 37.5207260307, lng: 127.7298401806, photos: ['fallback.png'],
      checks: [], reviews: [], visitors: '', rating: { score: 0, count: 0 }, recommended: false,
    })
  })

  it('사진이 있으면 쓰고, 좌표·분류가 없는 행은 뺀다', () => {
    expect(tourToPlace({ ...stay, firstimage: 'http://x/y.jpg' }, { fallbackPhoto: 'f' })!.photos).toEqual(['http://x/y.jpg'])
    expect(tourToPlace({ ...stay, mapx: '' }, { fallbackPhoto: 'f' })).toBeNull()
    expect(tourToPlace({ ...stay, contenttypeid: '38' }, { fallbackPhoto: 'f' })).toBeNull() // 쇼핑
  })

  it('결과 없음("")은 빈 목록, 단일 객체는 배열로, 오류 코드는 예외', () => {
    expect(tourItems({ response: { header: { resultCode: '0000' }, body: { items: '' } } })).toEqual([])
    expect(tourItems({ response: { header: { resultCode: '0000' }, body: { items: { item: stay } } } })).toHaveLength(1)
    expect(() => tourItems({ response: { header: { resultCode: '0030' } } })).toThrow()
  })

  it('동반 조건(detailPetTour2)을 확인 항목으로 바꾸고 빈 필드는 건너뛴다', () => {
    const checks = petTourChecks({
      acmpyTypeCd: '일부구역 동반가능', acmpyNeedMtr: '목줄 착용,이동장(켄넬)사용', acmpyPsblCpam: '',
      relaFrnshPrdlst: '반려견 전용 카시트 (2개)', etcAcmpyInfo: '반려견 동반 시 리드줄 필수입니다.\n둘째 줄',
    })
    expect(checks).toEqual([
      { icon: 'paw', text: '반려견 동반: 일부구역 동반가능' },
      { icon: 'shield', text: '필수 조건: 목줄 착용,이동장(켄넬)사용' },
      { icon: 'fence', text: '비치 물품: 반려견 전용 카시트 (2개)' },
      { icon: 'shield', text: '안내: 반려견 동반 시 리드줄 필수입니다.' },
    ])
  })
})
