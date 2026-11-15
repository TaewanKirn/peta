/** 받침 유무에 맞는 조사를 붙인다: withJosa('보리', '와', '과') → 보리와 */
export function withJosa(word: string, noBatchim: string, batchim: string): string {
  const code = word.charCodeAt(word.length - 1)
  const isHangul = code >= 0xac00 && code <= 0xd7a3
  const hasBatchim = isHangul && (code - 0xac00) % 28 !== 0
  return `${word}${hasBatchim ? batchim : noBatchim}`
}

const parseDate = (value: string) => {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

const pad = (n: number) => String(n).padStart(2, '0')

const shortDate = (value: string) => {
  const d = parseDate(value)
  return `${d.getMonth() + 1}.${pad(d.getDate())}`
}

/** 11.18 – 11.19 */
export const formatDateRange = (start: string, end: string) => `${shortDate(start)} – ${shortDate(end)}`

/** 1박 2일. 당일치기면 '당일' */
export function formatNights(start: string, end: string): string {
  const nights = Math.round((parseDate(end).getTime() - parseDate(start).getTime()) / 86_400_000)
  return nights <= 0 ? '당일' : `${nights}박 ${nights + 1}일`
}

/** '2020.01.15' 또는 '20200115' → '2020-01-15'. 없는 날짜는 null */
export function parseBirthday(input: string): string | null {
  const digits = input.replace(/\D/g, '')
  if (digits.length !== 8) return null
  const year = Number(digits.slice(0, 4))
  const month = Number(digits.slice(4, 6))
  const day = Number(digits.slice(6, 8))
  const date = new Date(year, month - 1, day)
  const valid = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
  return valid ? `${year}-${pad(month)}-${pad(day)}` : null
}

/** 입력 중 숫자를 2020.01.15 형태로 맞춘다. */
export function maskBirthday(input: string): string {
  const d = input.replace(/\D/g, '').slice(0, 8)
  if (d.length <= 4) return d
  if (d.length <= 6) return `${d.slice(0, 4)}.${d.slice(4)}`
  return `${d.slice(0, 4)}.${d.slice(4, 6)}.${d.slice(6)}`
}

export function ageFrom(birthday: string, today: Date = new Date()): number {
  const birth = parseDate(birthday)
  let age = today.getFullYear() - birth.getFullYear()
  const beforeBirthday =
    today.getMonth() < birth.getMonth() ||
    (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate())
  if (beforeBirthday) age -= 1
  return Math.max(age, 0)
}
