/**
 * Figma 플로우차트(flow.json)를 그대로 다시 그리고, 상자를 누르면 화면 바로 가기를 부른다.
 * flow.json은 Figma 섹션의 노드를 평평하게 편 것이다: b = 상자(프레임 · 사각형), t = 글자, v = 화살표(벡터 경로).
 * 베프 tools/beff-admin/flow-view.js와 같은 방식이다. 한쪽을 고치면 다른 쪽도 맞춘다.
 */

export interface FlowNode {
  k: 'b' | 't' | 'v'
  x: number
  y: number
  w?: number
  h?: number
  /** b: 노드 이름, v: 화살표 이름 */
  nm?: string
  /** b: 채우기(아래 → 위 순서) */
  f?: string[]
  /** b: 테두리 색, v: 선 색 */
  s?: string
  c?: string
  sw?: number
  dash?: number[]
  r?: number
  /** t: 글자 */
  fs?: number
  fw?: number
  al?: 'l' | 'c' | 'r'
  ar?: string
  /** v: SVG 경로, 시작 · 끝 화살촉 */
  d?: string
  a0?: boolean
  a1?: boolean
}

export interface Flow {
  w: number
  h: number
  nodes: FlowNode[]
  source?: string
}

export interface BoxSpec<V> {
  variants?: Array<V & { label: string }>
  /** 바로 열 수 없는 화면: 어떻게 가는지 */
  hint?: string
}

export interface FlowView {
  zoom(mode: string): void
  /** 지금 화면 상자를 강조한다(차량 · 모바일 둘 다일 수 있다) */
  setCurrent(names: string[]): void
}

export function createFlowView<V>(
  container: HTMLElement,
  flow: Flow,
  opts: { boxes: Record<string, BoxSpec<V>>; onPick: (v: V & { label: string }, box: string) => void; onHint: (spec: BoxSpec<V>) => void },
): FlowView {
  const { boxes, onPick, onHint } = opts
  container.classList.add('flow-wrap')
  const stage = document.createElement('div')
  stage.className = 'flow-stage'
  stage.style.width = `${flow.w}px`
  stage.style.height = `${flow.h}px`

  const svgNS = 'http://www.w3.org/2000/svg'
  const svg = document.createElementNS(svgNS, 'svg')
  svg.setAttribute('width', String(flow.w))
  svg.setAttribute('height', String(flow.h))
  svg.classList.add('flow-lines')
  const defs = document.createElementNS(svgNS, 'defs')
  svg.append(defs)
  const markers = new Map<string, string>()
  const marker = (color: string) => {
    const known = markers.get(color)
    if (known) return known
    const id = `arrow-${markers.size}`
    const m = document.createElementNS(svgNS, 'marker')
    for (const [k, val] of Object.entries({ id, viewBox: '0 0 10 10', refX: '9', refY: '5', markerWidth: '6', markerHeight: '6', orient: 'auto-start-reverse' })) {
      m.setAttribute(k, val)
    }
    const p = document.createElementNS(svgNS, 'path')
    for (const [k, val] of Object.entries({ d: 'M 1 1 L 9 5 L 1 9', fill: 'none', stroke: color, 'stroke-width': '1.6', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' })) {
      p.setAttribute(k, val)
    }
    m.append(p)
    defs.append(m)
    markers.set(color, id)
    return id
  }

  const hot = new Map<string, HTMLElement>()
  /** 'jump C02→C03 준비물' → 'C03 '으로 시작하는 상자 */
  const jumpTarget = (name: string) => {
    const to = name.split('→')[1]?.trim()
    const code = to?.split(' ')[0]
    if (!to || !code) return null
    return Object.keys(boxes).find((k) => k === to || k.startsWith(`${code} `)) ?? null
  }

  for (const n of flow.nodes) {
    if (n.k === 'v') {
      const color = n.c ?? '#59666f'
      const p = document.createElementNS(svgNS, 'path')
      p.setAttribute('d', n.d ?? '')
      p.setAttribute('transform', `translate(${n.x} ${n.y})`)
      p.setAttribute('fill', 'none')
      p.setAttribute('stroke', color)
      p.setAttribute('stroke-width', String(n.sw ?? 2))
      p.setAttribute('stroke-linejoin', 'round')
      if (n.dash) p.setAttribute('stroke-dasharray', n.dash.join(' '))
      if (n.a1) p.setAttribute('marker-end', `url(#${marker(color)})`)
      if (n.a0) p.setAttribute('marker-start', `url(#${marker(color)})`)
      svg.append(p)
      continue
    }
    const el = document.createElement('div')
    el.style.left = `${n.x}px`
    el.style.top = `${n.y}px`
    el.style.width = `${n.w}px`
    el.style.height = `${n.h}px`
    if (n.k === 'b') {
      el.className = 'flow-box'
      const layers = [...(n.f ?? [])].reverse()
      if (layers.length === 1) el.style.background = layers[0]
      else if (layers.length > 1) {
        el.style.background = `${layers.slice(0, -1).map((c) => `linear-gradient(${c}, ${c})`).join(', ')}, ${layers[layers.length - 1]}`
      }
      if (n.s) el.style.border = `${n.sw}px ${n.dash ? 'dashed' : 'solid'} ${n.s}`
      el.style.borderRadius = `${Math.min(n.r ?? 0, (n.h ?? 0) / 2)}px`
      const name = n.nm ?? ''
      const isJump = name.startsWith('jump ')
      const key = boxes[name] ? name : isJump ? jumpTarget(name) : null
      const spec = key ? boxes[key] : undefined
      if (key && spec) {
        el.classList.add('flow-hot')
        if (isJump) el.classList.add('flow-jump')
        else hot.set(name, el)
        const variants = spec.variants ?? []
        el.title = spec.hint ?? (variants.length > 1 ? `${variants.length}가지 상태` : (variants[0]?.label ?? ''))
        if (!isJump && variants.length > 1) el.dataset.count = String(variants.length)
        if (!variants.length) el.classList.add('flow-hint')
        el.addEventListener('click', (e) => {
          e.stopPropagation()
          if (isJump) {
            if (variants[0]) onPick(variants[0], key)
            else onHint(spec)
            return
          }
          open(el, key, spec)
        })
      }
    } else {
      el.className = 'flow-text'
      el.textContent = (n as FlowNode & { s: string }).s
      el.style.fontSize = `${n.fs}px`
      el.style.fontWeight = String(n.fw)
      el.style.color = n.c ?? '#000'
      el.style.textAlign = n.al === 'c' ? 'center' : n.al === 'r' ? 'right' : 'left'
      if (n.ar === 'WIDTH_AND_HEIGHT') el.style.whiteSpace = 'pre'
    }
    stage.append(el)
  }
  // 화살표는 상자 위에 그려야 꺾인 선이 상자 뒤로 숨지 않는다. 클릭은 상자가 받는다(pointer-events: none).
  stage.append(svg)

  const menu = document.createElement('div')
  menu.className = 'flow-menu'
  menu.hidden = true
  container.append(stage, menu)
  document.addEventListener('click', () => (menu.hidden = true))

  function open(el: HTMLElement, key: string, spec: BoxSpec<V>) {
    const variants = spec.variants ?? []
    if (variants.length === 1 && !spec.hint) {
      onPick(variants[0], key)
      return
    }
    if (!variants.length) {
      onHint(spec)
      return
    }
    menu.replaceChildren()
    if (spec.hint) {
      const p = document.createElement('p')
      p.textContent = spec.hint
      menu.append(p)
    }
    for (const v of variants) {
      const b = document.createElement('button')
      b.type = 'button'
      b.textContent = v.label
      b.addEventListener('click', (e) => {
        e.stopPropagation()
        menu.hidden = true
        onPick(v, key)
      })
      menu.append(b)
    }
    const r = el.getBoundingClientRect()
    const c = container.getBoundingClientRect()
    menu.style.left = `${r.left - c.left + container.scrollLeft}px`
    menu.style.top = `${r.bottom - c.top + container.scrollTop + 6}px`
    menu.hidden = false
  }

  let mode = 'fit'
  function fit() {
    const scale = mode === 'fit' ? Math.min(1, (container.clientWidth - 2) / flow.w) : Number(mode)
    stage.style.transform = `scale(${scale})`
    stage.style.marginBottom = `${flow.h * (scale - 1)}px`
    stage.style.marginRight = `${flow.w * (scale - 1)}px`
  }
  new ResizeObserver(fit).observe(container)

  return {
    zoom(m) {
      mode = m
      fit()
    },
    setCurrent(names) {
      for (const [k, el] of hot) el.classList.toggle('flow-current', names.includes(k))
    },
  }
}
