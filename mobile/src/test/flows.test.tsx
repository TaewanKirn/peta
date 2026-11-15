import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import type { CarTrip, Pet } from '../domain/types'
import { services } from '../services'
import { AppStateProvider, initialState, STORAGE_KEY, type AppState } from '../state/AppState'

function Where() {
  const { pathname } = useLocation()
  return <output data-testid="path">{pathname}</output>
}

function open(path: string, state?: Partial<AppState>) {
  if (state) localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...initialState(), ...state }))
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppStateProvider>
        <App />
        <Where />
      </AppStateProvider>
    </MemoryRouter>,
  )
}

const currentPath = () => screen.getByTestId('path').textContent

const bori: Pet = {
  id: 'pet-1',
  photo: null,
  species: 'dog',
  name: '보리',
  breed: '골든 리트리버',
  sizeLabel: '대형견',
  birthday: '2023-11-03',
  restIntervalMinutes: 60,
  hospital: null,
}

const withBori: Partial<AppState> = {
  pets: [bori],
  party: { petIds: ['pet-1'], guardianIds: ['me', 'companion'] },
}

describe('전체 흐름', () => {
  it('가입부터 저장까지 이동하고, 입력한 반려견과 고른 목적지를 차량으로 보낸다', async () => {
    const user = userEvent.setup()
    const save = vi.spyOn(services.tripSync, 'save').mockResolvedValue()
    open('/')

    // M01 계정 연동
    await user.click(screen.getByRole('button', { name: '카카오로 로그인' }))
    await screen.findByRole('heading', { name: /태완님의\s*여행 취향을 골라주세요/ })

    // M01 여행 취향: 선택 수에 따라 CTA가 바뀐다
    expect(screen.getByRole('button', { name: '선택' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: '1박 여행러' }))
    await user.click(screen.getByRole('button', { name: '산책 마스터' }))
    await user.click(screen.getByRole('button', { name: '2개 선택' }))

    // M02 기본정보: 다 채우기 전까지 다음으로 못 간다
    const next = screen.getByRole('button', { name: '여행 정보 작성하기' })
    expect(next).toBeDisabled()
    await user.type(screen.getByLabelText('이름'), '설이')
    await user.type(screen.getByLabelText('생년월일'), '20231103')
    expect(screen.getByLabelText('생년월일')).toHaveValue('2023.11.03')
    await user.click(screen.getByRole('radio', { name: '강아지' }))
    await user.type(screen.getByLabelText('종류'), '말티')
    await user.click(await screen.findByRole('option', { name: '말티즈' }))
    expect(next).toBeEnabled()
    await user.click(next)

    // M02 여행 습관 Q1: 휴식 주기 1시간 15분 → 키보드로 1시간 30분
    await screen.findByRole('heading', { name: /설이와 여행할 때/ })
    const minutes = screen.getByRole('spinbutton', { name: '분' })
    minutes.focus()
    for (let i = 0; i < 3; i += 1) await user.keyboard('{ArrowDown}')
    expect(minutes).toHaveAttribute('aria-valuenow', '30')
    await user.click(screen.getByRole('button', { name: '다음' }))

    // M02 여행 습관 Q2: 병원을 고르기 전까지 등록 완료가 잠겨 있다
    await screen.findByRole('heading', { name: /동물병원은 어디인가요/ })
    expect(screen.getByRole('button', { name: '등록 완료' })).toBeDisabled()
    await user.type(screen.getByLabelText('동물병원'), '멍멍')
    await user.click(await screen.findByRole('option', { name: /멍멍닥터 진료소/ }))
    await user.click(screen.getByRole('button', { name: '등록 완료' }))

    // M03 여행 계획(1): 방금 등록한 반려견이 선택되어 있다
    await screen.findByRole('heading', { name: /누구와 함께/ })
    expect(screen.getByRole('button', { name: /설이/ })).toHaveAttribute('aria-pressed', 'true')
    await user.click(screen.getByRole('button', { name: '선택 완료' }))

    // M03 여행 계획(2) → 장소 상세: 두 번째 장소를 고른다
    await user.click(await screen.findByRole('button', { name: /^.*코지마운틴.*경기 양평/ }))
    await screen.findByRole('heading', { name: '코지마운틴' })
    await user.click(screen.getByRole('button', { name: '숙소로 추가하기' }))

    // M03 일정 확인: 출발지가 없으면 저장하지 않고 해당 행으로 포커스를 옮긴다
    await screen.findByText('일정에 추가됨')
    await user.click(screen.getByRole('button', { name: '여행 계획 저장하기' }))
    expect(save).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.getByRole('button', { name: /출발지/ })).toHaveFocus())

    await user.click(screen.getByRole('button', { name: /출발지/ }))
    expect(screen.getByRole('button', { name: '출발지 설정하기' })).toBeDisabled()
    await user.type(screen.getByLabelText('출발지 검색'), '시청')
    await user.click(await screen.findByRole('button', { name: /시청역 1호선/ }))
    await user.click(screen.getByRole('button', { name: '출발지 설정하기' }))
    await screen.findByText('일정에 추가됨')
    await user.click(screen.getByRole('button', { name: '여행 계획 저장하기' }))

    await screen.findByRole('dialog', { name: '여행을 저장했어요' })
    expect(save).toHaveBeenCalledTimes(1)
    const trip: CarTrip = save.mock.calls[0][0]
    expect(trip.title).toBe('설이와 양평 여행')
    expect(trip.origin).toBe('시청역 1호선')
    expect(trip.pets[0]).toMatchObject({ name: '설이', breed: '말티즈', restIntervalMinutes: 90, sizeLabel: '소형견' })
    expect(trip.pets[0].regularHospital?.name).toBe('멍멍닥터 진료소')
    expect(trip.destination.place.name).toBe('코지마운틴')

    // M04 후기로 이어진다
    await user.click(screen.getByRole('button', { name: '후기 남기기' }))
    await screen.findByRole('heading', { name: /설이와 함께한 이곳/ })
    expect(currentPath()).toBe('/review')
  })
})

describe('M01 계정 연동', () => {
  it('로그인 실패를 보여주고 다시 시도할 수 있다', async () => {
    const user = userEvent.setup()
    const signIn = vi
      .spyOn(services.auth, 'signIn')
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce({ displayName: '태완' })
    open('/')
    await user.click(screen.getByRole('button', { name: '네이버로 로그인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('로그인하지 못했어요')
    await user.click(screen.getByRole('button', { name: '네이버로 로그인' }))
    await waitFor(() => expect(currentPath()).toBe('/signup/style'))
    expect(signIn).toHaveBeenCalledTimes(2)
  })
})

describe('M01 여행 취향', () => {
  it('원형을 눌러 선택하고 옆 항목으로 넘어간다', async () => {
    const user = userEvent.setup()
    open('/signup/style', { auth: { ...initialState().auth, displayName: '태완' } })
    expect(screen.getByRole('heading', { name: '산책 마스터' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '산책 마스터 선택' }))
    expect(screen.getByRole('button', { name: '1개 선택' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: '다음: 인생샷 발굴' }))
    expect(screen.getByRole('heading', { name: '인생샷 발굴' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '1개 선택' })).toBeEnabled()
  })

  it('선택 없이 건너뛸 수 있다', async () => {
    const user = userEvent.setup()
    open('/signup/style')
    await user.click(screen.getAllByRole('button', { name: '건너뛰기' })[0])
    expect(currentPath()).toBe('/signup/pet')
  })
})

describe('M02 반려동물 등록', () => {
  const basics = {
    ...initialState().petDraft,
    species: 'dog' as const,
    name: '보리',
    breed: '골든 리트리버',
    birthday: '2020.01.15',
  }

  it('없는 날짜는 생년월일로 받지 않는다', async () => {
    const user = userEvent.setup()
    open('/signup/pet')
    await user.type(screen.getByLabelText('생년월일'), '20230230')
    expect(screen.getByText('2020.01.15 형식으로 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '여행 정보 작성하기' })).toBeDisabled()
  })

  it('이름을 지울 수 있다', async () => {
    const user = userEvent.setup()
    open('/signup/pet')
    await user.type(screen.getByLabelText('이름'), '설이')
    await user.click(screen.getByRole('button', { name: '이름 지우기' }))
    expect(screen.getByLabelText('이름')).toHaveValue('')
  })

  it('반려동물을 고르기 전에는 종류를 입력할 수 없고, 바꾸면 종류가 비워진다', async () => {
    const user = userEvent.setup()
    open('/signup/pet')
    expect(screen.getByLabelText('종류')).toBeDisabled()
    await user.click(screen.getByRole('radio', { name: '고양이' }))
    await user.type(screen.getByLabelText('종류'), '러시안')
    await user.click(await screen.findByRole('option', { name: '러시안 블루' }))
    await user.click(screen.getByRole('radio', { name: '강아지' }))
    expect(screen.getByLabelText('종류')).toHaveValue('')
  })

  it('목록에 없는 품종도 직접 입력할 수 있다', async () => {
    const user = userEvent.setup()
    open('/signup/pet', { petDraft: { ...basics, breed: '' } })
    await user.type(screen.getByLabelText('종류'), '우리집 믹스')
    expect(await screen.findByText('목록에 없어도 그대로 입력할 수 있어요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '여행 정보 작성하기' })).toBeEnabled()
  })

  it('앞 단계 없이 습관 화면에 들어오면 돌려보낸다', async () => {
    open('/signup/habit')
    await waitFor(() => expect(currentPath()).toBe('/signup/pet'))
  })

  it('병원 검색 결과가 없으면 알려주고, 고른 뒤 다시 바꿀 수 있다', async () => {
    const user = userEvent.setup()
    open('/signup/habit?q=2', { petDraft: basics })
    const search = screen.getByLabelText('동물병원')
    await user.type(search, '없는병원')
    expect(await screen.findByText('검색 결과가 없어요.')).toBeInTheDocument()
    await user.clear(search)
    await user.type(search, '멍멍')
    await user.click(await screen.findByRole('option', { name: /멍멍냥냥 병원/ }))
    expect(screen.getByRole('button', { name: '등록 완료' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: /다시 선택/ }))
    expect(screen.getByRole('button', { name: '등록 완료' })).toBeDisabled()
  })

  it('다른 반려동물을 눌러 정보를 불러온다', async () => {
    const user = userEvent.setup()
    open('/signup/pet', { pets: [bori] })
    await user.click(screen.getByRole('button', { name: /보리/ }))
    expect(screen.getByLabelText('이름')).toHaveValue('보리')
    expect(screen.getByLabelText('생년월일')).toHaveValue('2023.11.03')
  })
})

describe('M03 여행 계획', () => {
  it('반려동물이나 보호자가 없으면 선택 완료를 막는다', async () => {
    const user = userEvent.setup()
    open('/plan/party', withBori)
    expect(screen.getByRole('button', { name: '선택 완료' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: /보리/ }))
    expect(screen.getByRole('button', { name: '선택 완료' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /보리/ }))
    await user.click(screen.getByRole('button', { name: '나' }))
    await user.click(screen.getByRole('button', { name: '동행 보호자' }))
    expect(screen.getByRole('button', { name: '선택 완료' })).toBeDisabled()
  })

  it('장소 조회 실패 시 다시 시도하고, 빈 결과를 알려준다', async () => {
    const user = userEvent.setup()
    const original = services.places.list
    vi.spyOn(services.places, 'list').mockRejectedValueOnce(new Error('fail')).mockImplementation(original)
    open('/plan/trip', withBori)
    await screen.findByText('장소를 불러오지 못했어요.')
    await user.click(screen.getByRole('button', { name: '다시 시도' }))
    await screen.findByRole('button', { name: /숲속 스테이.*경기 양평/ })
    await user.click(screen.getByRole('tab', { name: '코스' }))
    await screen.findByText('이 조건에 맞는 코스가 아직 없어요.')
  })

  it('밑줄 친 지역을 눌러 바꾼다', async () => {
    const user = userEvent.setup()
    open('/plan/trip', withBori)
    await user.click(screen.getByRole('button', { name: /지역 변경/ }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '가평' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /현재 가평/ })).toBeInTheDocument()
    await screen.findByText('이 조건에 맞는 숙소가 아직 없어요.')
  })

  it('저장 실패를 보여주고 재시도하며, 처리 중에는 다시 누를 수 없다', async () => {
    const user = userEvent.setup()
    let finish: () => void = () => {}
    const save = vi
      .spyOn(services.tripSync, 'save')
      .mockRejectedValueOnce(new Error('offline'))
      .mockImplementationOnce(() => new Promise<void>((resolve) => (finish = resolve)))
    open('/plan/itinerary', {
      ...withBori,
      tripDraft: { ...initialState().tripDraft, placeId: 'forest-stay', origin: '서울' },
    })
    await screen.findByText('일정에 추가됨')
    const cta = screen.getByRole('button', { name: '여행 계획 저장하기' })
    await user.click(cta)
    expect(await screen.findByRole('alert')).toHaveTextContent('저장하지 못했어요')
    await user.click(cta)
    await waitFor(() => expect(cta).toBeDisabled())
    finish()
    await screen.findByRole('dialog', { name: '여행을 저장했어요' })
    expect(save).toHaveBeenCalledTimes(2)
  })

  it('장소 없이 저장하면 장소 행으로 안내한다', async () => {
    const user = userEvent.setup()
    const save = vi.spyOn(services.tripSync, 'save').mockResolvedValue()
    open('/plan/itinerary', { ...withBori, tripDraft: { ...initialState().tripDraft, origin: '서울' } })
    await screen.findByRole('button', { name: /숙소/ })
    await user.click(screen.getByRole('button', { name: '여행 계획 저장하기' }))
    await waitFor(() => expect(screen.getByRole('button', { name: /숙소/ })).toHaveFocus())
    expect(save).not.toHaveBeenCalled()
  })
})

describe('M04 후기', () => {
  const reviewState = { ...withBori, tripDraft: { ...initialState().tripDraft, placeId: 'forest-stay' } }

  it('평점이 없으면 제출하지 않는다', async () => {
    const user = userEvent.setup()
    const submit = vi.spyOn(services.reviews, 'submit').mockResolvedValue()
    open('/review', reviewState)
    await user.click(await screen.findByRole('button', { name: '후기 남기기' }))
    expect(screen.getByRole('alert')).toHaveTextContent('평점을 선택해 주세요.')
    expect(submit).not.toHaveBeenCalled()
  })

  it('평점·태그·한줄평을 저장하고 실패하면 다시 시도한다', async () => {
    const user = userEvent.setup()
    const submit = vi
      .spyOn(services.reviews, 'submit')
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce()
    open('/review', reviewState)
    await user.click(await screen.findByRole('radio', { name: '4점' }))
    await user.click(screen.getByRole('button', { name: '보리가 멀미했어요' }))
    await user.type(screen.getByLabelText('한줄평을 남겨주세요.'), '좋았어요')
    await user.click(screen.getByRole('button', { name: '후기 남기기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('후기를 저장하지 못했어요')
    await user.click(screen.getByRole('button', { name: '후기 남기기' }))
    await screen.findByRole('dialog', { name: '후기를 남겼어요' })
    expect(submit).toHaveBeenLastCalledWith({
      placeId: 'forest-stay',
      rating: 4,
      drivingTags: ['보리가 멀미했어요'],
      comment: '좋았어요',
    })
  })

  it('평점은 화살표 키로 바꿀 수 있다', async () => {
    const user = userEvent.setup()
    open('/review', reviewState)
    await user.click(await screen.findByRole('radio', { name: '2점' }))
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('radio', { name: '3점' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('radio', { name: '3점' })).toHaveFocus()
  })

  it('갈 곳이 없으면 빈 상태를 보여준다', async () => {
    open('/review', withBori)
    expect(await screen.findByText('후기를 남길 여행이 아직 없어요.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '후기 남기기' })).not.toBeInTheDocument()
  })
})

describe('공통 내비게이션', () => {
  it('뒤로 갔다 돌아와도 입력값이 남고, 새로고침 뒤에도 유지된다', async () => {
    const user = userEvent.setup()
    const first = open('/signup/pet')
    await user.type(screen.getByLabelText('이름'), '설이')
    first.unmount()
    open('/signup/pet')
    expect(screen.getByLabelText('이름')).toHaveValue('설이')
  })

  const reviewing = {
    ...withBori,
    tripDraft: { ...initialState().tripDraft, placeId: 'forest-stay' },
    reviewDraft: { ...initialState().reviewDraft, comment: '좋았어요' },
  }

  it('미저장 값이 있으면 닫기 전에 확인하고, 그만두면 지운다', async () => {
    const user = userEvent.setup()
    open('/review', reviewing)
    await screen.findByLabelText('한줄평을 남겨주세요.')
    await user.click(screen.getByRole('button', { name: '닫기' }))

    const dialog = screen.getByRole('dialog', { name: '작성을 그만둘까요?' })
    await user.click(within(dialog).getByRole('button', { name: '계속 작성' }))
    expect(screen.getByLabelText('한줄평을 남겨주세요.')).toHaveValue('좋았어요')

    await user.click(screen.getByRole('button', { name: '닫기' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '그만두기' }))
    expect(currentPath()).toBe('/')
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(stored.reviewDraft.comment).toBe('')
  })

  it('모달은 Esc로 닫힌다', async () => {
    const user = userEvent.setup()
    open('/review', reviewing)
    await screen.findByLabelText('한줄평을 남겨주세요.')
    await user.click(screen.getByRole('button', { name: '닫기' }))
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('기본 화면에는 기기 장식을 그리지 않는다', () => {
    const { container } = open('/')
    expect(container.querySelector('.device-chrome')).toBeNull()
  })
})
