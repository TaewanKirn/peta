import { useRef, type KeyboardEvent, type PointerEvent, type WheelEvent } from 'react'

const ROW_HEIGHT = 51
const HOURS = [0, 1, 2, 3, 4, 5, 6]
const MINUTES = Array.from({ length: 12 }, (_, i) => i * 5)

const pad = (n: number) => String(n).padStart(2, '0')

function Wheel({
  label,
  values,
  value,
  wrap,
  format,
  suffix,
  onChange,
}: {
  label: string
  values: number[]
  value: number
  wrap: boolean
  format: (n: number) => string
  suffix: string
  onChange: (value: number) => void
}) {
  const index = values.indexOf(value)
  const at = (i: number) => {
    if (wrap) return values[(i + values.length) % values.length]
    return values[i]
  }
  const step = (delta: number) => {
    const next = at(index + delta)
    if (next !== undefined) onChange(next)
  }

  const drag = useRef<{ y: number } | null>(null)
  const onPointerDown = (event: PointerEvent) => {
    drag.current = { y: event.clientY }
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }
  const onPointerMove = (event: PointerEvent) => {
    if (!drag.current) return
    const moved = event.clientY - drag.current.y
    if (Math.abs(moved) >= ROW_HEIGHT / 2) {
      step(moved < 0 ? 1 : -1)
      drag.current.y = event.clientY
    }
  }
  const onWheel = (event: WheelEvent) => step(event.deltaY > 0 ? 1 : -1)
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      step(1)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      step(-1)
    }
  }

  const previous = at(index - 1)
  const next = at(index + 1)
  return (
    <div
      className="wheel"
      role="spinbutton"
      tabIndex={0}
      aria-label={label}
      aria-valuemin={values[0]}
      aria-valuemax={values[values.length - 1]}
      aria-valuenow={value}
      aria-valuetext={`${format(value)}${suffix}`}
      onKeyDown={onKeyDown}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={() => (drag.current = null)}
      onPointerCancel={() => (drag.current = null)}
      onWheel={onWheel}
    >
      <button type="button" tabIndex={-1} className="wheel-side" aria-hidden="true" onClick={() => step(-1)}>
        {previous === undefined ? '' : format(previous)}
      </button>
      <div className="wheel-current">
        <strong>{format(value)}</strong>
        <span>{suffix}</span>
      </div>
      <button type="button" tabIndex={-1} className="wheel-side" aria-hidden="true" onClick={() => step(1)}>
        {next === undefined ? '' : format(next)}
      </button>
    </div>
  )
}

/** 휴식 주기 선택. 값은 분 단위 하나로 다룬다. */
export function RestIntervalPicker({ minutes, onChange }: { minutes: number; onChange: (minutes: number) => void }) {
  const hour = Math.min(Math.floor(minutes / 60), 6)
  const minute = minutes % 60
  return (
    <div className="rest-picker">
      <Wheel
        label="시간"
        values={HOURS}
        value={hour}
        wrap={false}
        format={String}
        suffix="시간"
        onChange={(h) => onChange(h * 60 + minute)}
      />
      <Wheel
        label="분"
        values={MINUTES}
        value={minute}
        wrap
        format={pad}
        suffix="분마다"
        onChange={(m) => onChange(hour * 60 + m)}
      />
    </div>
  )
}
