// @vitest-environment node
import fs from 'node:fs/promises'
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import os from 'node:os'
import path from 'node:path'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { createTripMiddleware } from './tripApi'

const trip = {
  id: 'trip-1',
  title: '설이와 양평 여행',
  dateLabel: '11.18 – 11.19 · 1박 2일',
  guardianCount: 2,
  pets: [{ name: '설이', age: 2, restIntervalMinutes: 30 }],
  destination: { place: { name: '강변 스테이', lat: 37.5, lng: 127.5 } },
}

describe('/api/trip', () => {
  let dir: string
  let file: string
  let server: http.Server
  let url: string

  beforeAll(async () => {
    dir = await fs.mkdtemp(path.join(os.tmpdir(), 'peta-trip-'))
    file = path.join(dir, 'nested', 'trip.json')
    const middleware = createTripMiddleware(file)
    server = http.createServer((req, res) =>
      middleware(req, res, () => {
        res.statusCode = 418
        res.end()
      }),
    )
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
    url = `http://127.0.0.1:${(server.address() as AddressInfo).port}/api/trip`
  })

  afterAll(async () => {
    await new Promise((resolve) => server.close(resolve))
    await fs.rm(dir, { recursive: true, force: true })
  })

  beforeEach(() => fs.rm(file, { force: true }))

  const put = (body: string, contentType = 'application/json') =>
    fetch(url, { method: 'PUT', headers: { 'Content-Type': contentType }, body })

  it('저장 전에는 404', async () => {
    expect((await fetch(url)).status).toBe(404)
  })

  it('저장한 여행을 그대로 돌려준다', async () => {
    expect((await put(JSON.stringify(trip))).status).toBe(200)
    const response = await fetch(url)
    expect(response.status).toBe(200)
    expect(await response.json()).toEqual(trip)
  })

  it('병원 전화번호(phone)가 있어도, 없어도 저장하고 그대로 돌려준다', async () => {
    const hospital = { id: 'kakao-1', name: '위드동물병원', address: '강릉', lat: 37.7, lng: 128.9 }
    const withPhone = { ...trip, pets: [{ ...trip.pets[0], regularHospital: { ...hospital, phone: '033-645-5575' } }] }
    expect((await put(JSON.stringify(withPhone))).status).toBe(200)
    expect(await (await fetch(url)).json()).toEqual(withPhone)
    const withoutPhone = { ...trip, pets: [{ ...trip.pets[0], regularHospital: hospital }] }
    expect((await put(JSON.stringify(withoutPhone))).status).toBe(200)
    expect((await (await fetch(url)).json()).pets[0].regularHospital).toEqual(hospital)
  })

  it('마지막으로 저장한 여행으로 덮어쓴다', async () => {
    await put(JSON.stringify(trip))
    await put(JSON.stringify({ ...trip, title: '보리와 가평 여행' }))
    expect((await (await fetch(url)).json()).title).toBe('보리와 가평 여행')
  })

  it('차량 앱이 읽지 못할 값은 저장하지 않는다', async () => {
    expect((await put(JSON.stringify({ ...trip, pets: [] }))).status).toBe(400)
    expect((await put(JSON.stringify({ ...trip, destination: { place: { name: 'x' } } }))).status).toBe(400)
    expect((await put('{broken')).status).toBe(400)
    expect((await fetch(url)).status).toBe(404)
  })

  it('JSON이 아닌 요청과 너무 큰 요청을 거절한다', async () => {
    expect((await put(JSON.stringify(trip), 'text/plain')).status).toBe(415)
    const huge = JSON.stringify({ ...trip, title: 'x'.repeat(70 * 1024) })
    await expect(put(huge).then((r) => r.status)).resolves.toBe(500).catch(() => undefined)
    expect((await fetch(url)).status).toBe(404)
  })

  it('DELETE로 초기화한다', async () => {
    await put(JSON.stringify(trip))
    expect((await fetch(url, { method: 'DELETE' })).status).toBe(204)
    expect((await fetch(url)).status).toBe(404)
  })

  it('다른 경로는 넘긴다', async () => {
    expect((await fetch(url.replace('/api/trip', '/other'))).status).toBe(418)
  })
})
