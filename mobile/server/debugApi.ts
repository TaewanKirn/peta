import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Connect, Plugin } from 'vite'

const MAX_BODY_BYTES = 16 * 1024
/** 화면 이동 명령 이름: C05, C10S, gear:drive, event:waypoint … */
const ACTION_PATTERN = /^[A-Za-z0-9:_-]{1,40}$/

export interface DebugCommand {
  id: number
  action: string
  at: number
}

/**
 * 플로우 점검 페이지(/admin)와 차량 앱을 잇는 개발용 중계. 메모리에만 두고 dev 서버를 끄면 사라진다.
 *
 *   POST /api/debug/command  관리 페이지 → 명령 하나 올리기 { action }
 *   POST /api/debug/sync     차량 앱이 1초마다 지금 상태를 보내고 마지막 명령을 받아 간다
 *   GET  /api/debug/status   관리 페이지가 차량 앱 상태를 읽는다
 */
export function createDebugMiddleware(clock: () => number = Date.now): Connect.NextHandleFunction {
  let nextId = 1
  let command: DebugCommand | null = null
  let carStatus: Record<string, unknown> | null = null
  let carSeenAt: number | null = null

  const commandView = () => (command ? { ...command, ageMs: clock() - command.at } : null)

  return async (req, res, next) => {
    const url = req.url?.split('?')[0]
    // /admin 으로 들어와도 관리 페이지(admin/index.html)가 열리게 한다
    if (url === '/admin') {
      res.statusCode = 302
      res.setHeader('Location', '/admin/')
      res.end()
      return
    }
    if (!url?.startsWith('/api/debug/')) {
      next()
      return
    }
    try {
      if (url === '/api/debug/command' && req.method === 'POST') {
        const body = JSON.parse(await readBody(req)) as { action?: unknown }
        if (typeof body.action !== 'string' || !ACTION_PATTERN.test(body.action)) {
          send(res, 400, { error: 'action이 잘못되었습니다' })
          return
        }
        command = { id: nextId++, action: body.action, at: clock() }
        send(res, 200, commandView())
        return
      }
      if (url === '/api/debug/sync' && req.method === 'POST') {
        const raw = await readBody(req)
        carStatus = raw ? (JSON.parse(raw) as Record<string, unknown>) : {}
        carSeenAt = clock()
        send(res, 200, { command: commandView() })
        return
      }
      if (url === '/api/debug/status' && req.method === 'GET') {
        send(res, 200, {
          car: carStatus,
          carAgeMs: carSeenAt === null ? null : clock() - carSeenAt,
          command: commandView(),
        })
        return
      }
      send(res, 404, { error: '없는 경로입니다' })
    } catch (error) {
      if (error instanceof SyntaxError) send(res, 400, { error: 'JSON 형식이 아닙니다' })
      else send(res, 500, { error: '명령을 처리하지 못했습니다' })
    }
  }
}

function send(res: ServerResponse, status: number, body: unknown) {
  res.statusCode = status
  res.setHeader('Cache-Control', 'no-store')
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

export function debugApiPlugin(): Plugin {
  const middleware = createDebugMiddleware()
  return {
    name: 'peta-debug-api',
    configureServer(server) {
      server.middlewares.use(middleware)
    },
  }
}
