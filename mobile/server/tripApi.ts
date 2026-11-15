import fs from 'node:fs/promises'
import path from 'node:path'
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Connect, Plugin } from 'vite'

const MAX_BODY_BYTES = 64 * 1024

/** 차량 앱이 읽는 값이 빠졌는지 검사하고, 문제가 있으면 오류 문구를 돌려준다. */
export function validateTrip(trip: unknown): string | null {
  if (typeof trip !== 'object' || trip === null) return '여행 데이터가 객체가 아닙니다'
  const t = trip as Record<string, any>
  if (typeof t.id !== 'string' || !t.id) return 'id가 없습니다'
  if (typeof t.title !== 'string' || !t.title) return 'title이 없습니다'
  if (typeof t.dateLabel !== 'string' || !t.dateLabel) return 'dateLabel이 없습니다'
  if (!Number.isInteger(t.guardianCount) || t.guardianCount < 1) return 'guardianCount가 잘못되었습니다'
  if (!Array.isArray(t.pets) || t.pets.length === 0) return 'pets가 비어 있습니다'
  for (const pet of t.pets) {
    if (typeof pet?.name !== 'string' || !pet.name) return '반려동물 이름이 없습니다'
    if (!Number.isInteger(pet.age) || pet.age < 0) return '반려동물 나이가 잘못되었습니다'
    if (!Number.isInteger(pet.restIntervalMinutes) || pet.restIntervalMinutes <= 0) {
      return '휴식 주기가 잘못되었습니다'
    }
  }
  const place = t.destination?.place
  if (typeof place?.name !== 'string' || !place.name) return '목적지 이름이 없습니다'
  if (typeof place.lat !== 'number' || typeof place.lng !== 'number') return '목적지 좌표가 없습니다'
  return null
}

function send(res: ServerResponse, status: number, body?: unknown) {
  res.statusCode = status
  res.setHeader('Cache-Control', 'no-store')
  if (body === undefined) {
    res.end()
    return
  }
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(JSON.stringify(body))
}

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks: Buffer[] = []
    req.on('data', (chunk: Buffer) => {
      size += chunk.length
      if (size > MAX_BODY_BYTES) {
        reject(new Error('too large'))
        req.destroy()
        return
      }
      chunks.push(chunk)
    })
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')))
    req.on('error', reject)
  })
}

/**
 * 모바일 → 차량 연동용 저장소. 시연용이라 파일 하나에 마지막 여행만 둔다.
 * Firebase 등 실제 서버로 옮길 때는 같은 경로와 JSON 형식만 유지하면 된다.
 *
 *   PUT    /api/trip  모바일 "이 여행지 저장하기"
 *   GET    /api/trip  차량 앱이 3초마다 조회 (없으면 404)
 *   DELETE /api/trip  시연 초기화
 */
export function createTripMiddleware(filePath: string): Connect.NextHandleFunction {
  return async (req, res, next) => {
    if (req.url?.split('?')[0] !== '/api/trip') {
      next()
      return
    }
    try {
      if (req.method === 'GET') {
        const raw = await fs.readFile(filePath, 'utf-8').catch(() => null)
        if (raw === null) send(res, 404, { error: '저장된 여행이 없습니다' })
        else send(res, 200, JSON.parse(raw))
        return
      }
      if (req.method === 'PUT') {
        if (!req.headers['content-type']?.includes('application/json')) {
          send(res, 415, { error: 'application/json만 받습니다' })
          return
        }
        const trip = JSON.parse(await readBody(req))
        const problem = validateTrip(trip)
        if (problem) {
          send(res, 400, { error: problem })
          return
        }
        await fs.mkdir(path.dirname(filePath), { recursive: true })
        await fs.writeFile(filePath, JSON.stringify(trip, null, 2), 'utf-8')
        send(res, 200, trip)
        return
      }
      if (req.method === 'DELETE') {
        await fs.rm(filePath, { force: true })
        send(res, 204)
        return
      }
      send(res, 405, { error: '지원하지 않는 메서드입니다' })
    } catch (error) {
      if (error instanceof SyntaxError) send(res, 400, { error: 'JSON 형식이 아닙니다' })
      else send(res, 500, { error: '여행을 처리하지 못했습니다' })
    }
  }
}

export function tripApiPlugin(filePath: string): Plugin {
  const middleware = createTripMiddleware(filePath)
  return {
    name: 'peta-trip-api',
    configureServer(server) {
      server.middlewares.use(middleware)
    },
    configurePreviewServer(server) {
      server.middlewares.use(middleware)
    },
  }
}
