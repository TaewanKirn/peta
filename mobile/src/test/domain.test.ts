import { describe, expect, it } from 'vitest'
import { ageFrom, formatDateRange, formatNights, maskBirthday, parseBirthday, withJosa } from '../domain/format'
import { buildCarTrip } from '../domain/trip'
import type { Pet } from '../domain/types'
import { MOCK_PLACES, sizeOfBreed } from '../services/mockData'
import { emptyTripDraft } from '../state/AppState'

describe('format', () => {
  it('받침에 맞는 조사를 고른다', () => {
    expect(withJosa('보리', '와', '과')).toBe('보리와')
    expect(withJosa('단풍', '와', '과')).toBe('단풍과')
    expect(withJosa('Bori', '와', '과')).toBe('Bori와')
  })

  it('생일 입력을 마스킹하고 검증한다', () => {
    expect(maskBirthday('20200115')).toBe('2020.01.15')
    expect(parseBirthday('2020.01.15')).toBe('2020-01-15')
    expect(parseBirthday('2023.02.30')).toBeNull()
    expect(parseBirthday('2023')).toBeNull()
  })

  it('나이는 생일이 지나야 오른다', () => {
    expect(ageFrom('2023-11-03', new Date(2026, 10, 3))).toBe(3)
    expect(ageFrom('2023-11-03', new Date(2026, 10, 2))).toBe(2)
  })

  it('품종으로 크기를 정한다', () => {
    expect(sizeOfBreed('dog', '골든 리트리버')).toBe('대형견')
    expect(sizeOfBreed('dog', '말티즈')).toBe('소형견')
    expect(sizeOfBreed('dog', '우리집 믹스')).toBe('반려견')
    expect(sizeOfBreed('cat', '코리안 숏헤어')).toBe('반려묘')
  })

  it('날짜를 Figma 표기로 만든다', () => {
    expect(formatDateRange('2026-11-18', '2026-11-19')).toBe('11.18 – 11.19')
    expect(formatNights('2026-11-18', '2026-11-19')).toBe('1박 2일')
    expect(formatNights('2026-11-18', '2026-11-18')).toBe('당일')
  })
})

describe('buildCarTrip', () => {
  const pet: Pet = {
    id: 'p1',
    photo: 'data:image/jpeg;base64,AAAA',
    species: 'dog',
    name: '단풍',
    breed: '말티즈',
    sizeLabel: '소형견',
    birthday: '2023-11-03',
    restIntervalMinutes: 45,
    hospital: null,
  }

  it('차량 앱이 읽는 값을 채우고 사진은 보내지 않는다', () => {
    const trip = buildCarTrip({
      draft: { ...emptyTripDraft(), origin: '서울' },
      pets: [pet],
      guardians: [{ id: 'me', name: '나', avatar: 'me' }],
      place: MOCK_PLACES[1],
      now: new Date(2026, 10, 4),
    })

    expect(trip.title).toBe('단풍과 양평 여행')
    expect(trip.dateLabel).toBe('11.18 – 11.19 · 1박 2일')
    expect(trip.guardianCount).toBe(1)
    expect(trip.pets[0]).toMatchObject({ name: '단풍', age: 3, sizeLabel: '소형견', restIntervalMinutes: 45 })
    expect(trip.destination.place).toMatchObject({ id: 'cozy-mountain', name: '코지마운틴', address: '경기 양평' })
    expect(trip.destination.activity).toBe('단풍이랑 강변 마당에서 뛰어놀기')
    expect(JSON.stringify(trip)).not.toContain('base64')
  })

  it('병원 전화번호는 있을 때만 regularHospital에 담는다', () => {
    const hospital = { id: 'kakao-1', name: '위드동물병원', address: '강릉', lat: 37.7, lng: 128.9 }
    const build = (h: typeof hospital & { phone?: string }) =>
      buildCarTrip({
        draft: { ...emptyTripDraft(), origin: '서울' },
        pets: [{ ...pet, hospital: h }],
        guardians: [{ id: 'me', name: '나', avatar: 'me' }],
        place: MOCK_PLACES[1],
        now: new Date(2026, 10, 4),
      }).pets[0].regularHospital
    expect(build({ ...hospital, phone: '033-645-5575' })).toEqual({ ...hospital, phone: '033-645-5575' })
    expect(build(hospital)).toEqual(hospital)
    expect('phone' in build(hospital)!).toBe(false)
  })
})
