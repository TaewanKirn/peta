/**
 * 플로우 점검 페이지 (/admin). dev 서버에서만 열린다.
 * 화면 배치는 Figma 「화면수정(태완) › PET:A 전체 화면 플로우 (1112 기준 코드)」를 뽑은 flow.json을 그대로 그린다.
 * 차량 앱: /api/debug/command 로 명령을 올리면 차량 앱이 1초 안에 받아 그 화면 상태로 바꾼다.
 * 모바일 앱: 같은 출처의 localStorage(peta.app.v2)에 필요한 값을 채운 뒤 오른쪽 미리보기를 그 경로로 연다.
 */
import '@fontsource/noto-sans-kr/400.css'
import '@fontsource/noto-sans-kr/700.css'
import '../src/styles/tokens.css'
import './admin.css'
import { buildCarTrip } from '../src/domain/trip'
import type { Pet } from '../src/domain/types'
import { MOCK_ORIGINS } from '../src/services/mockData'
import { services } from '../src/services'
import { emptyPetDraft, initialState, STORAGE_KEY, type AppState } from '../src/state/AppState'
import { createFlowView, type BoxSpec, type Flow } from './flow-view'
import flowData from './flow.json'

// ---------------- 상자 → 동작 ----------------

type Needs = 'none' | 'draft' | 'party'

/** 상자를 눌렀을 때 하는 일 */
type Pick =
  /** 차량 앱 명령. screen: 차량 앱이 알리는 Screen 이름(지금 화면 강조용) */
  | { kind: 'car'; action: string; screen?: string }
  /** 모바일 미리보기 경로 */
  | { kind: 'mobile'; route: string | (() => string); needs: Needs }
  | { kind: 'sendTrip' }
  | { kind: 'deleteTrip' }

const car = (label: string, action: string, screen?: string) => ({ label, kind: 'car' as const, action, screen })
const mob = (label: string, route: string | (() => string), needs: Needs = 'none') => ({ label, kind: 'mobile' as const, route, needs })

const SAMPLE_PLACE_ID = 'forest-stay' // mockData 장소. 이미 고른 장소(관광공사 id 포함)가 있으면 그대로 쓴다.
const currentPlaceId = () => readState().tripDraft.placeId ?? SAMPLE_PLACE_ID

const BOXES: Record<string, BoxSpec<Pick>> = {
  // 모바일
  'M01 계정 연동': { variants: [mob('계정 연동', '/')] },
  'M01 여행 스타일': { variants: [mob('여행 스타일', '/signup/style')] },
  'M02 반려견 프로필 (1) 기본 정보': { variants: [mob('반려견 프로필 (1)', '/signup/pet')] },
  'M02 반려견 프로필 (2) 휴식 주기 · 병원': { variants: [mob('반려견 프로필 (2)', '/signup/habit', 'draft')] },
  'M03 동행 선택': { variants: [mob('동행 선택', '/plan/party', 'party')] },
  'M03 여행 계획 (지역 · 날짜 · 테마)': { variants: [mob('여행 계획', '/plan/trip', 'party')] },
  'M03 장소 상세': { variants: [mob('장소 상세', () => `/plan/place/${encodeURIComponent(currentPlaceId())}`, 'party')] },
  'M03 여행 일정 확인': { variants: [mob('여행 일정 확인', '/plan/itinerary', 'party')] },
  'M03 출발지 선택': { variants: [mob('출발지 선택', '/plan/origin', 'party')] },
  '팝업 여행 계획 저장 완료 (차량에서도 볼 수 있어요)': {
    hint: '여행 일정 확인에서 「여행 계획 저장하기」를 누르면 뜹니다. 저장하면 차량 앱이 3초 안에 C02로 받아 갑니다.',
    variants: [mob('여행 일정 확인 열기', '/plan/itinerary', 'party'), { label: '바로 차량에 보내기 (PUT /api/trip)', kind: 'sendTrip' }],
  },
  'M04 후기 · 장소 저장': { variants: [mob('후기 · 장소 저장', '/review', 'party')] },

  // 차량 · 시작
  'C00 스플래시 (3초)': { variants: [car('스플래시', 'C00', 'C00_SPLASH')] },
  'C01R 여행 중 다시 연 홈 (정차)': { variants: [car('여행 중 다시 연 홈', 'C01R', 'C01R_HOME_TRIP')] },
  '분기 후기 남길 여행이 있나?': {
    hint: '차량 앱이 켜질 때 남은 후기가 있으면 C10, 없으면 폰 여행을 확인합니다.',
    variants: [car('있음 → C10 후기', 'C10', 'C10_REVIEW'), car('없음 → C02 여행 확인', 'C02', 'C02_CONFIRM')],
  },
  '분기 폰에 저장된 여행이 있나?': {
    hint: '폰 여행은 /api/trip에 저장된 여행입니다. 아래 두 줄은 서버 값을 바꿔 실제 분기를 탑니다.',
    variants: [
      car('있음 → C02 여행 확인', 'C02', 'C02_CONFIRM'),
      car('없음 → C01 홈 (샘플 여행)', 'C01', 'C01_HOME'),
      { label: '폰 여행 저장 (PUT /api/trip)', kind: 'sendTrip' },
      { label: '폰 여행 지우기 (DELETE /api/trip)', kind: 'deleteTrip' },
    ],
  },
  'C02 여행 확인': { variants: [car('여행 확인', 'C02', 'C02_CONFIRM')] },
  'C01 홈 · 폰 여행 없음 (샘플 여행)': { variants: [car('홈 · 폰 여행 없음', 'C01', 'C01_HOME')] },

  // 출발 준비
  'C03 준비물 체크': { variants: [car('준비물 체크', 'C03', 'C03_CHECKLIST')] },
  'C04 경로 요약 · 쉬어 갈 곳': { variants: [car('경로 요약', 'C04', 'C04_ROUTE')] },
  'C04-E 경로 찾기 실패': { variants: [car('경로 찾기 실패', 'C04E', 'C04E_ROUTE_FAIL')] },

  // 언제나
  '탭 홈 · 검색 · 설정 (정차 중)': {
    variants: [car('홈 탭 (여행 확인)', 'C02', 'C02_CONFIRM'), car('검색 탭', 'C12', 'C12_SEARCH'), car('설정 탭', 'C14', 'C14_SETTINGS')],
  },
  'C12 검색 · 추천': { variants: [car('검색 · 추천', 'C12', 'C12_SEARCH')] },
  'C13-E 결과 없음 · 범위 넓히기': { variants: [car('검색 결과 없음', 'C13E', 'C12_SEARCH')] },
  'C14 설정': { variants: [car('설정', 'C14', 'C14_SETTINGS')] },
  'C11 응급 · 가까운 동물병원': { variants: [car('응급 · 동물병원', 'C11', 'C11_EMERGENCY')] },

  // 주행 · 도착
  '내비 기본 내비 길 안내': {
    hint: 'Pleos 기본 내비 화면입니다. 차량 앱은 3초 뒤 C05로 돌아옵니다. 에뮬레이터에서는 달린 거리로 도착을 처리합니다.',
    variants: [car('D 주행 (80km/h)으로 달리기', 'gear:drive'), car('C05 주행 중 보기', 'C05', 'C05_DRIVING')],
  },
  'C05-V 변경/취소 음성 질문 (답하면 C05)': {
    variants: [car('질문 화면 (정지)', 'C05V', 'C05V_STOP_VOICE'), car('실제 음성 질문 (C05에서)', 'event:askStop', 'C05V_STOP_VOICE')],
  },
  'C05 주행 중 (정적 화면)': {
    variants: [car('주행 중 (D 정지)', 'C05', 'C05_DRIVING'), car('D 주행 (80km/h) — 쉬었다 갈 곳까지 자동', 'gear:drive', 'C05_DRIVING')],
  },
  '분기 휴식 주기를 넘겼나? (경유지 없이 달릴 때)': {
    hint: '경유지 없이 달리다가 연속 운전이 휴식 주기(반려견 · 운전자 중 짧은 쪽)를 넘으면 C06을 띄웁니다.',
    variants: [car('휴식 주기만큼 주행 채우기 (실제 흐름)', 'event:longDrive', 'C06_REST'), car('C06 바로 보기', 'C06', 'C06_REST')],
  },
  'C06 휴식 제안 · 음성 (답하면 C05)': { variants: [car('휴식 제안', 'C06', 'C06_REST')] },
  'C06-E 다시 묻기 (못 알아들었을 때)': { variants: [car('다시 묻기', 'C06E', 'C06E_REST_RETRY')] },
  'C07 쉬었다 갈 곳 도착': {
    variants: [car('쉬었다 갈 곳 도착 (정지)', 'C07', 'C07_ARRIVED_STOP'), car('실제 흐름: 경유지 도착 이벤트', 'event:waypoint', 'C07_ARRIVED_STOP')],
  },
  'C08 휴식 중 (체류 시간)': { variants: [car('휴식 중', 'C08', 'C08_RESTING')] },
  'C09 목적지 도착': {
    variants: [car('목적지 도착 (정지)', 'C09', 'C09_ARRIVED_DEST'), car('실제 흐름: 목적지 도착 이벤트', 'event:destination', 'C09_ARRIVED_DEST')],
  },
  'C10 여행 후기': { variants: [car('여행 후기', 'C10', 'C10_REVIEW')] },
  'C10-S 후기 저장 팝업': { variants: [car('후기 저장 팝업', 'C10S', 'C10_REVIEW')] },
}

const CAR_EVENTS: Array<{ title: string; items: Array<{ action: string; label: string; tone?: 'warn' }> }> = [
  {
    title: '기어',
    items: [
      { action: 'gear:park', label: 'P 정차' },
      { action: 'gear:hold', label: 'D 정지 (0km/h)' },
      { action: 'gear:drive', label: 'D 주행 (80km/h)' },
      { action: 'gear:real', label: '실제 기어 따르기' },
    ],
  },
  {
    title: '이벤트',
    items: [
      { action: 'event:longDrive', label: '휴식 주기만큼 주행 채우기' },
      { action: 'event:waypoint', label: '쉬었다 갈 곳 도착' },
      { action: 'event:destination', label: '목적지 도착' },
      { action: 'event:askStop', label: '변경/취소 음성 질문 (실제)' },
    ],
  },
  {
    title: '초기화',
    items: [
      { action: 'event:reset', label: '여행 초기화' },
      { action: 'event:resetApp', label: '앱 초기화 (후기 기록 삭제)', tone: 'warn' },
    ],
  },
]

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T

function toast(text: string) {
  const el = $('toast')
  el.textContent = text
  el.classList.add('show')
  window.clearTimeout(Number(el.dataset.timer))
  el.dataset.timer = String(window.setTimeout(() => el.classList.remove('show'), 4000))
}

function button(label: string, onClick: (b: HTMLButtonElement) => void, tone?: string) {
  const b = document.createElement('button')
  b.type = 'button'
  b.className = 'btn' + (tone ? ` ${tone}` : '')
  b.textContent = label
  b.addEventListener('click', () => onClick(b))
  return b
}

// ---------------- 차량 앱 ----------------

/** 마지막으로 누른 차량 상자. 같은 Screen을 쓰는 상자(C10/C10-S, C12/C13-E)를 가린다. */
let lastCarBox: string | null = null

async function sendCar(action: string, b?: HTMLButtonElement) {
  b?.classList.add('sent')
  try {
    const res = await fetch('/api/debug/command', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action }),
    })
    if (!res.ok) throw new Error(String(res.status))
  } catch {
    b?.classList.add('failed')
    toast('차량 명령을 보내지 못했습니다')
  } finally {
    window.setTimeout(() => b?.classList.remove('sent', 'failed'), 900)
  }
}

interface CarStatus {
  screen?: string
  stage?: string
  phase?: string
  gear?: string
  realGear?: string
  gearOverride?: boolean
  speedKph?: number
  plan?: string | null
  planFromPhone?: boolean
  legRemainKm?: number | null
  continuousDrivingMinutes?: number
  restAfterMinutes?: number
}

/** 차량 Screen 이름 → 상자 */
function carBoxFor(screen: string | undefined): string | null {
  if (!screen) return null
  const candidates = Object.entries(BOXES)
    .filter(([name]) => !name.startsWith('분기') && !name.startsWith('탭') && !name.startsWith('내비'))
    .filter(([, spec]) => (spec.variants ?? []).some((v) => v.kind === 'car' && v.screen === screen && /^C/.test(v.action)))
    .map(([name]) => name)
  if (lastCarBox && candidates.includes(lastCarBox)) return lastCarBox
  return candidates[0] ?? null
}

let currentCarBox: string | null = null
let currentMobileBox: string | null = null

async function pollCar() {
  try {
    const res = await fetch('/api/debug/status', { cache: 'no-store' })
    const body = (await res.json()) as { car: CarStatus | null; carAgeMs: number | null }
    const online = body.car !== null && body.carAgeMs !== null && body.carAgeMs < 3500
    const s = body.car ?? {}
    $('car-dot').className = 'dot' + (online ? ' on' : '')
    $('car-line').textContent = online
      ? [
          s.screen || '화면 없음 (패널 닫힘)',
          `${s.stage} · ${s.phase}`,
          `기어 ${s.gear}${s.gearOverride ? ` (점검 페이지 · 실제 ${s.realGear})` : ''} · ${Math.round(s.speedKph ?? 0)}km/h`,
          s.legRemainKm != null ? `남은 ${s.legRemainKm.toFixed(1)}km` : null,
          `연속 운전 ${s.continuousDrivingMinutes ?? 0}/${s.restAfterMinutes ?? '-'}분`,
          s.plan ? `${s.plan}${s.planFromPhone ? ' (폰)' : ' (샘플)'}` : '여행 없음',
        ]
          .filter(Boolean)
          .join('  |  ')
      : '차량 앱 연결 안 됨 — 에뮬레이터에서 PETA를 켜 두세요 (DEMO_MODE 빌드, 10.0.2.2:5173)'
    $('car-raw').textContent = JSON.stringify(body, null, 2)
    currentCarBox = online ? carBoxFor(s.screen) : null
  } catch {
    $('car-dot').className = 'dot'
    $('car-line').textContent = 'dev 서버 응답 없음'
    currentCarBox = null
  }
  view.setCurrent([currentCarBox, currentMobileBox].filter((x): x is string => x !== null))
}

// ---------------- 모바일 앱 ----------------

const SAMPLE_PET: Pet = {
  id: 'pet-sample-richie',
  photo: null,
  species: 'dog',
  name: '리치',
  breed: '말티푸',
  sizeLabel: '소형견',
  birthday: '2025-03-15', // [가정] 시연용 샘플. 차량 SampleTrip과 같은 1살
  restIntervalMinutes: 70,
  hospital: null,
}

function readState(): AppState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? { ...initialState(), ...(JSON.parse(raw) as Partial<AppState>) } : initialState()
  } catch {
    return initialState()
  }
}

function writeState(state: AppState) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    message('브라우저 저장소에 쓸 수 없습니다')
  }
}

/** 화면이 요구하는 값만 채운다. 이미 있는 값은 그대로 둔다. */
function ensure(needs: Needs) {
  if (needs === 'none') return
  const s = readState()
  if (needs === 'draft') {
    if (!s.petDraft.name.trim() || !s.petDraft.species || !s.petDraft.breed.trim() || !s.petDraft.birthday.trim()) {
      s.petDraft = {
        ...emptyPetDraft(),
        species: 'dog',
        name: SAMPLE_PET.name,
        breed: SAMPLE_PET.breed,
        birthday: SAMPLE_PET.birthday.replaceAll('-', '.'),
      }
    }
  } else {
    if (s.pets.length === 0) s.pets = [SAMPLE_PET]
    if (s.party.petIds.filter((id) => s.pets.some((p) => p.id === id)).length === 0) {
      s.party = { ...s.party, petIds: [s.pets[0].id] }
    }
    if (s.party.guardianIds.length === 0) s.party = { ...s.party, guardianIds: ['me'] }
    if (!s.tripDraft.placeId) s.tripDraft = { ...s.tripDraft, placeId: SAMPLE_PLACE_ID }
    if (!s.tripDraft.origin) s.tripDraft = { ...s.tripDraft, origin: MOCK_ORIGINS[0].name }
  }
  writeState(s)
}

function openPhone(route: string) {
  $('phone-route').textContent = route
  $<HTMLAnchorElement>('phone-open').href = route
  // 같은 경로여도 새로 읽게 한다 (localStorage를 바꿨을 수 있다)
  const frame = $<HTMLIFrameElement>('phone')
  frame.src = 'about:blank'
  requestAnimationFrame(() => (frame.src = route))
}

function message(text: string) {
  $('mobile-msg').textContent = text
  toast(text)
}

async function sendSampleTrip() {
  ensure('party')
  const s = readState()
  const pets = s.party.petIds.flatMap((id) => s.pets.filter((p) => p.id === id))
  const guardians = s.party.guardianIds.flatMap((id) => s.guardians.filter((g) => g.id === id))
  const placeId = s.tripDraft.placeId ?? SAMPLE_PLACE_ID
  try {
    const place = await services.places.get(placeId)
    if (!place) throw new Error(`장소 ${placeId}를 찾지 못했습니다`)
    await services.tripSync.save(buildCarTrip({ draft: s.tripDraft, pets, guardians, place }))
    message(`차량으로 보냈습니다: ${place.name} (3초 안에 차량 C02에 뜹니다)`)
  } catch (error) {
    message(`보내지 못했습니다: ${error instanceof Error ? error.message : String(error)}`)
  }
}

async function deleteTrip() {
  const res = await fetch('/api/trip', { method: 'DELETE' }).catch(() => null)
  message(res?.ok ? '저장된 여행을 지웠습니다. 차량은 샘플 여행으로 C01을 보여 줍니다.' : '지우지 못했습니다')
}

/** 미리보기 iframe 경로 → 모바일 상자 */
function mobileBoxFor(path: string): string | null {
  for (const [name, spec] of Object.entries(BOXES)) {
    for (const v of spec.variants ?? []) {
      if (v.kind !== 'mobile' || name.startsWith('팝업')) continue
      const route = typeof v.route === 'string' ? v.route : v.route()
      const same = route.startsWith('/plan/place/') ? path.startsWith('/plan/place/') : route === path
      if (same) return name
    }
  }
  return null
}

function watchPhone() {
  const frame = $<HTMLIFrameElement>('phone')
  window.setInterval(() => {
    try {
      const path = frame.contentWindow?.location.pathname
      if (!path || path === 'blank') return
      $('phone-route').textContent = path
      currentMobileBox = mobileBoxFor(path)
    } catch {
      currentMobileBox = null
    }
  }, 500)
}

// ---------------- 그리기 ----------------

function pick(v: Pick & { label: string }, box: string) {
  switch (v.kind) {
    case 'car':
      if (/^C/.test(v.action)) lastCarBox = box
      void sendCar(v.action)
      toast(`차량: ${v.label}`)
      break
    case 'mobile':
      ensure(v.needs)
      openPhone(typeof v.route === 'string' ? v.route : v.route())
      break
    case 'sendTrip':
      void sendSampleTrip()
      break
    case 'deleteTrip':
      void deleteTrip()
      break
  }
}

const view = createFlowView<Pick>($('flow'), flowData as Flow, {
  boxes: BOXES,
  onPick: pick,
  onHint: (spec) => spec.hint && toast(spec.hint),
})

const zoom = $('zoom')
for (const [m, label] of [['fit', '화면 맞춤'], ['0.5', '50%'], ['0.75', '75%'], ['1', '100%']] as const) {
  const b = button(label, () => {
    zoom.querySelectorAll('.btn').forEach((x) => x.classList.toggle('on', x === b))
    view.zoom(m)
  })
  if (m === 'fit') b.classList.add('on')
  zoom.append(b)
}

for (const group of CAR_EVENTS) {
  const wrap = document.createElement('div')
  const h = document.createElement('h3')
  h.textContent = group.title
  const list = document.createElement('div')
  list.className = 'buttons'
  for (const item of group.items) list.append(button(item.label, (b) => void sendCar(item.action, b), item.tone))
  wrap.append(h, list)
  $('car-events').append(wrap)
}

$('mobile-data').append(
  button('빈 상태로 (모바일 입력값 초기화)', () => {
    writeState(initialState())
    openPhone('/')
    message('모바일 입력값을 비웠습니다')
  }, 'warn'),
  button('샘플 반려견 · 여행 채우기', () => {
    ensure('party')
    message('리치(말티푸)와 샘플 여행을 채웠습니다')
    openPhone('/plan/itinerary')
  }),
  button('차량에 여행 보내기', () => void sendSampleTrip()),
  button('저장된 여행 지우기', () => void deleteTrip()),
)

watchPhone()
void pollCar()
window.setInterval(() => void pollCar(), 1000)
