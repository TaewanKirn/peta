// Figma 화면과 같은 값을 채우는 시각 비교용 상태. 제품 코드에서는 쓰지 않는다.
const bori = {
  id: 'pet-bori',
  photo: null,
  species: 'dog',
  name: '보리',
  breed: '골든 리트리버',
  sizeLabel: '대형견',
  birthday: '2023-11-03',
  restIntervalMinutes: 75,
  hospital: null,
}
const ruby = { ...bori, id: 'pet-ruby', species: 'cat', name: '루비', breed: '코리안 숏헤어', sizeLabel: '반려묘', birthday: '2021-10-01' }
const hospital = { id: 'h-1', name: '멍멍닥터 진료소', address: '서울특별시 마포구 12-1', lat: 37.5563, lng: 126.9236 }

export const figmaState = {
  auth: { provider: 'kakao', status: 'success', error: null, displayName: '태완' },
  travelStyles: [],
  petDraft: {
    editingId: 'pet-bori',
    photo: null,
    species: 'dog',
    name: '보리',
    breed: '골든 리트리버',
    birthday: '2020.01.15',
    restIntervalMinutes: 75,
    hospital: null,
  },
  hospitalQuery: '',
  pets: [bori, ruby],
  guardians: [
    { id: 'me', name: '나', avatar: 'me' },
    { id: 'companion', name: '동행 보호자', avatar: 'companion' },
  ],
  party: { petIds: ['pet-bori'], guardianIds: ['me', 'companion'] },
  tripDraft: {
    region: '양평',
    startDate: '2026-11-18',
    endDate: '2026-11-19',
    theme: 'nature',
    placeId: 'forest-stay',
    origin: '',
    items: [],
  },
  reviewDraft: { placeId: 'forest-stay', rating: 4, drivingTags: ['편안하게 이동했어요'], comment: '' },
  favorites: [],
}

const withHospital = { ...figmaState, petDraft: { ...figmaState.petDraft, hospital } }
const selectedWalk = { ...figmaState, travelStyles: ['walk'] }

const withOrigin = { ...figmaState, tripDraft: { ...figmaState.tripDraft, origin: '시청역 1호선' } }
const reviewEmpty = { ...figmaState, reviewDraft: { placeId: 'forest-stay', rating: 0, drivingTags: [], comment: '' } }

export const SCREENS = [
  { name: 'm01_account_link', path: '/' },
  { name: 'm01_travel_style_01', path: '/signup/style' },
  { name: 'm01_travel_style_02', path: '/signup/style', state: selectedWalk },
  { name: 'm02_pet_profile_03', path: '/signup/pet' },
  { name: 'm02_pet_profile_04', path: '/signup/habit' },
  { name: 'm02_pet_profile_05', path: '/signup/habit?q=2' },
  { name: 'm02_pet_profile_07', path: '/signup/habit?q=2', state: withHospital },
  { name: 'm03_trip_plan_01', path: '/plan/party' },
  { name: 'm03_trip_plan_02', path: '/plan/trip' },
  { name: 'm03_place_detail', path: '/plan/place/forest-stay' },
  { name: 'm03_itinerary_01', path: '/plan/itinerary' },
  { name: 'm03_itinerary_02', path: '/plan/origin' },
  { name: 'm03_itinerary_03', path: '/plan/origin', state: withOrigin },
  { name: 'm04_review_save_01', path: '/review', state: reviewEmpty },
  { name: 'm04_review_save_02', path: '/review' },
]