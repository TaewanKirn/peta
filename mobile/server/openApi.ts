import fs from 'node:fs'
import type { ServerResponse } from 'node:http'
import type { Connect, Plugin } from 'vite'

/*
 * 외부 API 프록시. 키는 브라우저로 내보내지 않고 이 서버에서만 쓴다.
 *
 *   GET /api/kakao/keyword?query=&x=&y=&radius=&size=&sort=   카카오 로컬 키워드 검색
 *   GET /api/tour/<operation>?...                            한국관광공사 반려동물 동반여행 API
 *
 * 키가 없으면 503 { error: 'no-key' }, 업스트림 실패는 502. 클라이언트는 둘 다 mock으로 폴백한다.
 */

export interface ApiKeys {
  tour: string
  kakao: string
}

const TOUR_BASE = 'https://apis.data.go.kr/B551011/KorPetTourService2'
const KAKAO_KEYWORD = 'https://dapi.kakao.com/v2/local/search/keyword.json'

const TOUR_OPERATIONS = new Set([
  'areaBasedList2',
  'locationBasedList2',
  'searchKeyword2',
  'detailCommon2',
  'detailPetTour2',
])
const TOUR_PARAMS = [
  'areaCode', 'sigunguCode', 'contentTypeId', 'numOfRows', 'pageNo', 'arrange',
  'contentId', 'keyword', 'mapX', 'mapY', 'radius',
]
const KAKAO_PARAMS = ['query', 'x', 'y', 'radius', 'size', 'sort', 'page', 'category_group_code']

/** local.properties 형식(KEY=value)에서 값을 읽는다. 파일이 없으면 빈 객체. */
export function parseProperties(text: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const at = trimmed.indexOf('=')
    if (at > 0) out[trimmed.slice(0, at).trim()] = trimmed.slice(at + 1).trim()
  }
  return out
}

/** 환경변수 우선, 없으면 차량 앱의 local.properties. */
export function loadKeys(env: NodeJS.ProcessEnv, propertiesPath: string): ApiKeys {
  let props: Record<string, string> = {}
  try {
    props = parseProperties(fs.readFileSync(propertiesPath, 'utf-8'))
  } catch {
    // 파일이 없으면 환경변수만 쓴다.
  }
  return {
    tour: env.TOUR_API_KEY || props.TOUR_API_KEY || '',
    kakao: env.KAKAO_REST_KEY || props.KAKAO_REST_KEY || '',
  }
}

function send(res: ServerResponse, status: number, body: unknown) {
  res.statusCode = status
  res.setHeader('Cache-Control', 'no-store')
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(JSON.stringify(body))
}

function pick(search: URLSearchParams, allowed: string[]): URLSearchParams {
  const out = new URLSearchParams()
  for (const name of allowed) {
    const value = search.get(name)
    if (value !== null && value !== '') out.set(name, value)
  }
  return out
}

export function createOpenApiMiddleware(
  getKeys: () => ApiKeys,
  fetchImpl: typeof fetch = fetch,
): Connect.NextHandleFunction {
  return async (req, res, next) => {
    const url = new URL(req.url ?? '/', 'http://local')
    const kakao = url.pathname === '/api/kakao/keyword'
    const tourOperation = url.pathname.startsWith('/api/tour/') ? url.pathname.slice('/api/tour/'.length) : null
    if (!kakao && tourOperation === null) {
      next()
      return
    }
    if (req.method !== 'GET') {
      send(res, 405, { error: 'GET만 지원합니다' })
      return
    }
    const keys = getKeys()
    try {
      let upstream: Response
      if (kakao) {
        if (!keys.kakao) return send(res, 503, { error: 'no-key' })
        const params = pick(url.searchParams, KAKAO_PARAMS)
        if (!params.get('query')) return send(res, 400, { error: 'query가 필요합니다' })
        upstream = await fetchImpl(`${KAKAO_KEYWORD}?${params}`, {
          headers: { Authorization: `KakaoAK ${keys.kakao}` },
          signal: AbortSignal.timeout(5000),
        })
      } else {
        if (!TOUR_OPERATIONS.has(tourOperation!)) return send(res, 404, { error: '지원하지 않는 오퍼레이션입니다' })
        if (!keys.tour) return send(res, 503, { error: 'no-key' })
        const params = pick(url.searchParams, TOUR_PARAMS)
        // URLSearchParams가 Decoding 키를 한 번만 인코딩한다.
        const query = new URLSearchParams({ serviceKey: keys.tour, MobileOS: 'ETC', MobileApp: 'PETA', _type: 'json' })
        params.forEach((value, name) => query.set(name, value))
        upstream = await fetchImpl(`${TOUR_BASE}/${tourOperation}?${query}`, { signal: AbortSignal.timeout(5000) })
      }
      const text = await upstream.text()
      // 인증 오류는 _type=json이어도 XML로 온다. 본문은 그대로 돌려주지 않는다.
      if (!upstream.ok || !text.trim().startsWith('{')) return send(res, 502, { error: 'upstream' })
      res.statusCode = 200
      res.setHeader('Cache-Control', 'no-store')
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(text)
    } catch {
      send(res, 502, { error: 'upstream' })
    }
  }
}

export function openApiPlugin(propertiesPath: string): Plugin {
  const middleware = createOpenApiMiddleware(() => loadKeys(process.env, propertiesPath))
  return {
    name: 'peta-open-api',
    configureServer(server) {
      server.middlewares.use(middleware)
    },
    configurePreviewServer(server) {
      server.middlewares.use(middleware)
    },
  }
}
