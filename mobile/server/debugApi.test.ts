// @vitest-environment node
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { createDebugMiddleware } from './debugApi'

describe('/api/debug', () => {
  let server: http.Server
  let base: string
  let now = 1_000

  beforeAll(async () => {
    const middleware = createDebugMiddleware(() => now)
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

  const post = (path: string, body: unknown) =>
    fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })

  it('차량 상태가 오기 전에는 비어 있다', async () => {
    const res = await fetch(`${base}/api/debug/status`)
    expect(await res.json()).toEqual({ car: null, carAgeMs: null, command: null })
  })

  it('명령을 올리면 차량 sync 응답과 상태에 실린다', async () => {
    const first = await (await post('/api/debug/command', { action: 'C05' })).json()
    expect(first).toMatchObject({ id: 1, action: 'C05', ageMs: 0 })

    now += 300
    const sync = await (await post('/api/debug/sync', { screen: 'C05_DRIVING' })).json()
    expect(sync.command).toMatchObject({ id: 1, action: 'C05', ageMs: 300 })

    now += 200
    const status = await (await fetch(`${base}/api/debug/status`)).json()
    expect(status.car).toEqual({ screen: 'C05_DRIVING' })
    expect(status.carAgeMs).toBe(200)

    const second = await (await post('/api/debug/command', { action: 'gear:drive' })).json()
    expect(second.id).toBe(2)
  })

  it('이상한 action은 받지 않는다', async () => {
    expect((await post('/api/debug/command', { action: '' })).status).toBe(400)
    expect((await post('/api/debug/command', { action: 'a b' })).status).toBe(400)
    expect((await post('/api/debug/command', {})).status).toBe(400)
  })

  it('/admin 은 /admin/ 으로 보낸다', async () => {
    const res = await fetch(`${base}/admin`, { redirect: 'manual' })
    expect(res.status).toBe(302)
    expect(res.headers.get('location')).toBe('/admin/')
  })

  it('다른 경로는 다음 미들웨어로 넘긴다', async () => {
    expect((await fetch(`${base}/api/trip`)).status).toBe(418)
  })
})
