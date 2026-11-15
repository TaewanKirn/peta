// @vitest-environment node
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { createOpenApiMiddleware, loadKeys, parseProperties } from './openApi'

const KAKAO_KEY = 'kakao-secret'
const TOUR_KEY = 'a+b/c=='

describe('parseProperties · loadKeys', () => {
  it('KEY=value 줄만 읽고 주석·빈 줄은 건너뛴다', () => {
    expect(parseProperties('# c\n\nsdk.dir=/x\nTOUR_API_KEY= abc==\n')).toEqual({ 'sdk.dir': '/x', TOUR_API_KEY: 'abc==' })
  })

  it('환경변수가 파일보다 우선하고, 파일이 없어도 동작한다', () => {
    const keys = loadKeys({ KAKAO_REST_KEY: 'env' } as NodeJS.ProcessEnv, '/no/such/local.properties')
    expect(keys).toEqual({ tour: '', kakao: 'env' })
  })
})

describe('/api/kakao · /api/tour 프록시', () => {
  let server: http.Server
  let base: string
  let keys = { tour: TOUR_KEY, kakao: KAKAO_KEY }
  const upstream = vi.fn<typeof fetch>()

  beforeAll(async () => {
    const middleware = createOpenApiMiddleware(() => keys, upstream as typeof fetch)
    server = http.createServer((req, res) =>
      middleware(req, res, () => {
        res.statusCode = 418
        res.end()
      }),
    )
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
  })
  afterAll(async () => {
    await new Promise((resolve) => server.close(resolve))
  })
  beforeEach(() => {
    keys = { tour: TOUR_KEY, kakao: KAKAO_KEY }
    upstream.mockReset()
  })

  it('카카오: 키를 헤더로만 보내고 허용된 파라미터만 넘긴다', async () => {
    upstream.mockResolvedValue(new Response('{"documents":[]}'))
    const res = await fetch(`${base}/api/kakao/keyword?query=${encodeURIComponent('동물병원')}&size=3&evil=1`)
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ documents: [] })
    const [url, init] = upstream.mock.calls[0]
    expect(String(url)).toContain('dapi.kakao.com/v2/local/search/keyword.json?')
    expect(String(url)).not.toContain('evil')
    expect(String(url)).not.toContain(KAKAO_KEY)
    expect((init?.headers as Record<string, string>).Authorization).toBe(`KakaoAK ${KAKAO_KEY}`)
  })

  it('관광공사: Decoding 키를 한 번만 인코딩하고 공통 파라미터를 붙인다', async () => {
    upstream.mockResolvedValue(new Response('{"response":{}}'))
    const res = await fetch(`${base}/api/tour/areaBasedList2?areaCode=31&sigunguCode=19&contentTypeId=32`)
    expect(res.status).toBe(200)
    const url = String(upstream.mock.calls[0][0])
    expect(url).toContain('KorPetTourService2/areaBasedList2?')
    expect(url).toContain('serviceKey=a%2Bb%2Fc%3D%3D')
    expect(url).toContain('MobileOS=ETC')
    expect(url).toContain('_type=json')
    expect(url).toContain('areaCode=31')
    expect(url).toContain('sigunguCode=19')
    expect(url).toContain('contentTypeId=32')
  })

  it('키가 없으면 503, 응답에 키가 섞이지 않는다', async () => {
    keys = { tour: '', kakao: '' }
    expect((await fetch(`${base}/api/kakao/keyword?query=a`)).status).toBe(503)
    expect((await fetch(`${base}/api/tour/areaBasedList2`)).status).toBe(503)
    expect(upstream).not.toHaveBeenCalled()
  })

  it('XML 오류 본문·업스트림 오류·네트워크 오류는 502로 바꾸고 본문을 내보내지 않는다', async () => {
    upstream.mockResolvedValueOnce(new Response('<OpenAPI_ServiceResponse>SERVICE_KEY_IS_NOT_REGISTERED_ERROR'))
    let res = await fetch(`${base}/api/tour/areaBasedList2`)
    expect(res.status).toBe(502)
    expect(await res.text()).not.toContain('SERVICE_KEY')
    upstream.mockResolvedValueOnce(new Response('{}', { status: 401 }))
    expect((await fetch(`${base}/api/kakao/keyword?query=a`)).status).toBe(502)
    upstream.mockRejectedValueOnce(new Error(`connect ${KAKAO_KEY}`))
    res = await fetch(`${base}/api/kakao/keyword?query=a`)
    expect(res.status).toBe(502)
    expect(await res.text()).not.toContain(KAKAO_KEY)
  })

  it('지원하지 않는 오퍼레이션·query 누락·다른 경로를 구분한다', async () => {
    expect((await fetch(`${base}/api/tour/ldongCode2`)).status).toBe(404)
    expect((await fetch(`${base}/api/kakao/keyword`)).status).toBe(400)
    expect((await fetch(`${base}/api/trip`)).status).toBe(418)
    expect((await fetch(`${base}/api/kakao/keyword?query=a`, { method: 'POST' })).status).toBe(405)
  })
})
