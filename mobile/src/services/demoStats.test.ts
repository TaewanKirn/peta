import { describe, expect, it } from 'vitest'
import { demoStats, withDemoStats } from './demoStats'
import { tourToPlace } from './openApiMapping'

describe('demoStats (시연용 가상 수치 [가정])', () => {
  it('같은 id면 몇 번을 불러도 같은 값이다', () => {
    expect(demoStats('tour-3532734')).toEqual(demoStats('tour-3532734'))
    expect(JSON.stringify(demoStats('tour-623279'))).toBe(JSON.stringify(demoStats('tour-623279')))
  })

  it('id가 다르면 값이 달라진다', () => {
    const scores = new Set(Array.from({ length: 40 }, (_, i) => JSON.stringify(demoStats(`tour-${1000 + i}`))))
    expect(scores.size).toBeGreaterThan(30)
  })

  it('값 범위: 평점 3.8~5.0(소수 1자리), 후기 4~160, 이용자 3~99+, 후기 문구 1~2개', () => {
    for (let i = 0; i < 500; i++) {
      const s = demoStats(`tour-${i}`)
      expect(s.rating.score).toBeGreaterThanOrEqual(3.8)
      expect(s.rating.score).toBeLessThanOrEqual(5)
      expect(Number.isInteger(s.rating.score * 10)).toBe(true)
      expect(s.rating.count).toBeGreaterThanOrEqual(4)
      expect(s.rating.count).toBeLessThanOrEqual(160)
      expect(s.visitors === '99+' || (Number(s.visitors) >= 3 && Number(s.visitors) <= 99)).toBe(true)
      expect(s.reviews.length).toBeGreaterThanOrEqual(1)
      expect(s.reviews.length).toBeLessThanOrEqual(2)
      expect(new Set(s.reviews.map((r) => r.text)).size).toBe(s.reviews.length)
      s.reviews.forEach((r) => expect([4, 5]).toContain(r.stars))
    }
  })

  it('withDemoStats는 원본 Place를 바꾸지 않고 수치만 채운다', () => {
    const base = tourToPlace(
      { contentid: '1', contenttypeid: '32', title: '숙소', mapx: '127.1', mapy: '37.1', addr1: '경기도 양평군' },
      { fallbackPhoto: 'f' },
    )!
    expect(base.reviews).toEqual([])
    const filled = withDemoStats(base)
    expect(filled.name).toBe('숙소')
    expect(filled.reviews.length).toBeGreaterThan(0)
    expect(filled.rating.count).toBeGreaterThan(0)
    expect(base.rating.count).toBe(0)
  })
})
