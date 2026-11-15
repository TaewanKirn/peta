import { ageFrom, formatDateRange, formatNights, withJosa } from './format'
import type { CarTrip, Guardian, Pet, Place, ThemeId, TripDraft } from './types'

export const THEMES: Array<{ id: ThemeId; label: string }> = [
  { id: 'nature', label: '자연 속 휴식' },
  { id: 'walk', label: '산책' },
  { id: 'activity', label: '액티비티' },
]

export const themeLabel = (id: ThemeId) => THEMES.find((theme) => theme.id === id)?.label ?? ''

/** 3살 · 대형견 */
export const petSummary = (pet: Pet, today?: Date) =>
  `${ageFrom(pet.birthday, today)}살 · ${pet.sizeLabel}`

/** 차량 앱으로 보낼 여행을 만든다. 장소 문구의 {pet}은 대표 반려동물 이름으로 바꾼다. */
export function buildCarTrip(args: {
  draft: TripDraft
  pets: Pet[]
  guardians: Guardian[]
  place: Place
  now?: Date
}): CarTrip {
  const { draft, pets, guardians, place, now = new Date() } = args
  const lead = pets[0]
  return {
    id: `trip-${draft.region}-${draft.startDate}`,
    title: `${withJosa(lead.name, '와', '과')} ${draft.region} 여행`,
    dateLabel: `${formatDateRange(draft.startDate, draft.endDate)} · ${formatNights(draft.startDate, draft.endDate)}`,
    guardianCount: guardians.length,
    origin: draft.origin,
    theme: themeLabel(draft.theme),
    pets: pets.map((pet) => ({
      name: pet.name,
      species: pet.species,
      breed: pet.breed,
      age: ageFrom(pet.birthday, now),
      sizeLabel: pet.sizeLabel,
      restIntervalMinutes: pet.restIntervalMinutes,
      regularHospital: pet.hospital && {
        id: pet.hospital.id,
        name: pet.hospital.name,
        address: pet.hospital.address,
        lat: pet.hospital.lat,
        lng: pet.hospital.lng,
        // 전화번호는 있을 때만 보낸다(없으면 키 자체를 생략).
        ...(pet.hospital.phone ? { phone: pet.hospital.phone } : {}),
      },
    })),
    destination: {
      place: { id: place.id, name: place.name, address: place.address, lat: place.lat, lng: place.lng },
      activity: place.activity.replace('{pet}', withJosa(lead.name, '랑', '이랑')),
      nearbyInfo: place.nearbyInfo,
    },
    savedAt: now.toISOString(),
  }
}
