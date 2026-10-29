import { CheckCheck, FilePlus2, Send, UserPlus, Wallet } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { formatMoney } from '@/lib/format'
import { cn } from '@/lib/utils'

/*
 * The sign-in object: a spiral galaxy of a few thousand particles drawn on a
 * canvas. Around it, ledger events (a payment, a new order, a new client...)
 * appear as small glass tickets, and each ticket sends a stream of particles
 * into the core, so the picture literally shows business flowing into one place.
 * Colours come from the --hero-p1/p2/p3 tokens and are re-read when the theme
 * changes. Under prefers-reduced-motion one still frame is drawn.
 */

type Kind = 'payment' | 'order' | 'client' | 'invoice' | 'done'
const EVENTS: { kind: Kind; amount?: number }[] = [
  { kind: 'payment', amount: 75_000 },
  { kind: 'order' },
  { kind: 'client' },
  { kind: 'invoice' },
  { kind: 'payment', amount: 150_000 },
  { kind: 'done' },
]
const ICONS = { payment: Wallet, order: FilePlus2, client: UserPlus, invoice: Send, done: CheckCheck } as const

// Ticket slots, in % of the panel; the galaxy centre is at CENTER
const CENTER = { x: 0.54, y: 0.4 }
const SLOTS = [
  { left: '6%', top: '15%' },
  { right: '5%', top: '33%' },
  { left: '9%', top: '50%' },
]
const TICK_MS = 2600

type Slot = { event: number; state: 'hidden' | 'shown' | 'gone'; key: number }

function reducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function GalaxyScene({ className, onIncome }: { className?: string; onIncome?: (amount: number) => void }) {
  const { t } = useTranslation()
  const wrapRef = useRef<HTMLDivElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const slotRefs = useRef<(HTMLDivElement | null)[]>([])
  const engineRef = useRef<Engine | null>(null)
  const incomeRef = useRef(onIncome)
  useEffect(() => {
    incomeRef.current = onIncome
  }, [onIncome])

  const [slots, setSlots] = useState<Slot[]>(() =>
    SLOTS.map((_, i) => ({ event: i, state: reducedMotion() ? 'shown' : 'hidden', key: i })),
  )

  // Canvas engine
  useEffect(() => {
    const canvas = canvasRef.current
    const wrap = wrapRef.current
    if (!canvas || !wrap) return
    const engine = createEngine(canvas, wrap)
    engineRef.current = engine
    return () => {
      engine.destroy()
      engineRef.current = null
    }
  }, [])

  // Ticket cycle: every tick one slot shows the next event; a moment later the
  // ticket streams into the core
  useEffect(() => {
    if (reducedMotion()) return
    const timers: number[] = []
    let tick = 0
    let next = SLOTS.length
    const show = (slot: number, event: number) => {
      setSlots((s) => s.map((x, i) => (i === slot ? { event, state: 'hidden', key: x.key + 1 } : x)))
      timers.push(window.setTimeout(() => setSlots((s) => s.map((x, i) => (i === slot ? { ...x, state: 'shown' } : x))), 40))
      timers.push(
        window.setTimeout(() => {
          const el = slotRefs.current[slot]
          const engine = engineRef.current
          const ev = EVENTS[event]
          if (el && engine) {
            engine.stream(el, ev.kind === 'payment' ? 90 : 45, () => {
              if (ev.amount) incomeRef.current?.(ev.amount)
            })
          }
        }, 650),
      )
    }
    // first round: the three initial tickets come in one after another
    SLOTS.forEach((_, i) => timers.push(window.setTimeout(() => show(i, i), 300 + i * 700)))
    const id = window.setInterval(() => {
      const slot = tick % SLOTS.length
      tick += 1
      const event = next % EVENTS.length
      next += 1
      setSlots((s) => s.map((x, i) => (i === slot ? { ...x, state: 'gone' } : x)))
      timers.push(window.setTimeout(() => show(slot, event), 600))
    }, TICK_MS)
    return () => {
      window.clearInterval(id)
      timers.forEach((x) => window.clearTimeout(x))
    }
  }, [])

  return (
    <div ref={wrapRef} className={cn('pointer-events-none absolute inset-0', className)} aria-hidden>
      <canvas ref={canvasRef} className="absolute inset-0 size-full" />
      {slots.map((slot, i) => {
        const ev = EVENTS[slot.event]
        const Icon = ICONS[ev.kind]
        const money = ev.kind === 'payment'
        return (
          <div
            key={i}
            ref={(el) => {
              slotRefs.current[i] = el
            }}
            data-state={slot.state}
            className={cn(
              'cf-ticket absolute flex w-[15.5rem] items-center gap-3 rounded-xl px-3 py-2.5',
              // phones: one ticket, pinned under the galaxy
              i === 1 ? 'max-sm:!top-auto max-sm:!right-4 max-sm:bottom-4 max-sm:left-4 max-sm:w-auto' : 'max-sm:hidden',
            )}
            style={SLOTS[i]}
          >
            <span
              className={cn(
                'grid size-8 shrink-0 place-items-center rounded-full',
                money ? 'bg-[var(--hero-a)] text-[var(--hero-bg)]' : 'bg-white/8 text-[var(--hero-fg)]',
              )}
            >
              <Icon className="size-4" strokeWidth={2} />
            </span>
            <span className="grid min-w-0 flex-1 leading-tight">
              <span className="truncate text-[13px] font-medium text-[var(--hero-fg)]">{t(`scene.events.${ev.kind}.title`)}</span>
              <span className="truncate font-mono text-[11px] text-[var(--hero-muted)]">{t(`scene.events.${ev.kind}.meta${slot.event === 4 ? '2' : ''}`)}</span>
            </span>
            {money && ev.amount ? (
              <span className="shrink-0 font-mono text-[12px] font-medium text-[var(--hero-a)] tabular-nums">
                +{formatMoney(ev.amount, 'KZT')}
              </span>
            ) : null}
          </div>
        )
      })}
    </div>
  )
}

/* ------------------------------------------------------------------ engine */

interface Engine {
  stream: (from: HTMLElement, count: number, onArrive: () => void) => void
  destroy: () => void
}

interface Star {
  r: number
  theta: number
  y: number
  speed: number
  size: number
  tone: number
  alpha: number
  phase: number
  tw: number
}

interface Spark {
  x0: number
  y0: number
  cx: number
  cy: number
  start: number
  dur: number
  size: number
  tone: number
  last?: boolean
  done?: () => void
}

const TONES = 8

function gauss() {
  return (Math.random() + Math.random() + Math.random() + Math.random() - 2) / 2
}

function parseColor(value: string): [number, number, number] {
  const v = value.trim()
  const hex = v.startsWith('#') ? v.slice(1) : ''
  if (hex.length === 6) return [0, 2, 4].map((i) => parseInt(hex.slice(i, i + 2), 16)) as [number, number, number]
  const m = v.match(/\d+(\.\d+)?/g)
  return m ? [Number(m[0]), Number(m[1]), Number(m[2])] : [255, 255, 255]
}

function mix(a: number[], b: number[], k: number) {
  return a.map((x, i) => Math.round(x + (b[i] - x) * k))
}

/** Soft round sprite of one colour, drawn once and reused for every particle. */
function sprite([r, g, b]: number[]) {
  const c = document.createElement('canvas')
  c.width = c.height = 64
  const ctx = c.getContext('2d')
  if (!ctx) return c
  const grad = ctx.createRadialGradient(32, 32, 0, 32, 32, 32)
  grad.addColorStop(0, `rgba(${r},${g},${b},1)`)
  grad.addColorStop(0.22, `rgba(${r},${g},${b},0.75)`)
  grad.addColorStop(0.5, `rgba(${r},${g},${b},0.18)`)
  grad.addColorStop(1, `rgba(${r},${g},${b},0)`)
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, 64, 64)
  return c
}

function createEngine(canvas: HTMLCanvasElement, wrap: HTMLElement): Engine {
  const context = typeof ResizeObserver === 'undefined' ? null : canvas.getContext('2d')
  // no canvas support (old browser, test environment): tickets still work, no galaxy
  if (!context) return { stream: () => undefined, destroy: () => undefined }
  const ctx = context
  const still = reducedMotion()
  let width = 0
  let height = 0
  let dpr = 1
  let sprites: HTMLCanvasElement[] = []
  let frame = 0
  let flare = 0
  const sparks: Spark[] = []

  // --- particles
  const small = window.innerWidth < 640
  const count = small ? 1800 : 4200
  const stars: Star[] = []
  for (let i = 0; i < count; i++) {
    const roll = Math.random()
    const tw = 0.6 + Math.random() * 2.2
    const phase = Math.random() * Math.PI * 2
    if (roll < 0.14) {
      // bulge: a small round cloud of warm light at the core
      const r = Math.abs(gauss()) * 0.2
      stars.push({
        r,
        theta: Math.random() * Math.PI * 2,
        y: gauss() * 0.11,
        speed: 0.05 / (r + 0.25),
        size: 0.8 + Math.random() * 1.8,
        tone: Math.random() < 0.7 ? 0 : 1,
        alpha: 0.5 + Math.random() * 0.5,
        phase,
        tw,
      })
      continue
    }
    const halo = roll > 0.9
    const arm = i % 2
    const r = halo ? 0.3 + Math.random() * 1.0 : 0.08 + Math.pow(Math.random(), 1.25) * 0.95
    // particles scatter around the arm, more on the outside
    const spread = gauss() * (halo ? 3 : 0.55 + r * 0.25)
    stars.push({
      r,
      theta: arm * Math.PI + r * 5.2 + spread,
      y: gauss() * (halo ? 0.22 : 0.05 * (1 - r) + 0.015),
      speed: 0.045 / (r + 0.22),
      size: (halo ? 0.5 : 0.6) + Math.random() * 1.7,
      tone: Math.min(TONES - 1, Math.max(0, Math.floor(r * (TONES - 1) + gauss() * 1.6))),
      alpha: halo ? 0.3 : 0.4 + Math.random() * 0.5,
      phase,
      tw,
    })
  }
  // distant background dust, fixed on screen
  const dust = Array.from({ length: small ? 90 : 200 }, () => ({
    x: Math.random(),
    y: Math.random(),
    size: 0.4 + Math.random() * 1.1,
    phase: Math.random() * Math.PI * 2,
    tw: 0.3 + Math.random(),
  }))

  const readColors = () => {
    const css = getComputedStyle(wrap)
    const p1 = parseColor(css.getPropertyValue('--hero-p1') || '#ffffff')
    const p2 = parseColor(css.getPropertyValue('--hero-p2') || '#ff73b4')
    const p3 = parseColor(css.getPropertyValue('--hero-p3') || '#6f8bff')
    sprites = Array.from({ length: TONES }, (_, i) => {
      const k = i / (TONES - 1)
      return sprite(k < 0.4 ? mix(p1, p2, k / 0.4) : mix(p2, p3, (k - 0.4) / 0.6))
    })
  }

  const resize = () => {
    const rect = wrap.getBoundingClientRect()
    dpr = Math.min(window.devicePixelRatio || 1, 2)
    width = rect.width
    height = rect.height
    canvas.width = Math.round(width * dpr)
    canvas.height = Math.round(height * dpr)
    if (still) draw(0)
  }

  // --- pointer tilt (eased)
  const pointer = { x: 0, y: 0, tx: 0, ty: 0 }
  const host = wrap.parentElement ?? wrap
  const onMove = (e: PointerEvent) => {
    const r = host.getBoundingClientRect()
    pointer.tx = ((e.clientX - r.left) / r.width - 0.5) * 2
    pointer.ty = ((e.clientY - r.top) / r.height - 0.5) * 2
  }
  const onLeave = () => {
    pointer.tx = 0
    pointer.ty = 0
  }

  const geometry = () => {
    const R = Math.min(width * (small ? 0.46 : 0.42), height * (small ? 0.36 : 0.4))
    return { R, cx: width * (small ? 0.5 : CENTER.x), cy: height * (small ? 0.48 : CENTER.y) }
  }

  function draw(time: number) {
    const t = time / 1000
    pointer.x += (pointer.tx - pointer.x) * 0.04
    pointer.y += (pointer.ty - pointer.y) * 0.04
    const { R, cx, cy } = geometry()

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    ctx.clearRect(0, 0, width, height)
    ctx.globalCompositeOperation = 'lighter'

    for (const d of dust) {
      ctx.globalAlpha = 0.25 + 0.25 * Math.sin(t * d.tw + d.phase)
      const s = d.size * 3
      ctx.drawImage(sprites[0], d.x * width - s / 2, d.y * height - s / 2, s, s)
    }

    // faint disc light so the arms sit in a nebula instead of on black
    ctx.globalAlpha = 0.16
    ctx.save()
    ctx.translate(cx, cy)
    ctx.rotate(-0.38)
    ctx.scale(1, 0.48)
    ctx.drawImage(sprites[3], -R * 1.25, -R * 1.25, R * 2.5, R * 2.5)
    ctx.restore()

    const incl = 1.08 + pointer.y * 0.16
    const yaw = pointer.x * 0.35
    const roll = -0.38
    const ci = Math.cos(incl)
    const si = Math.sin(incl)
    const cr = Math.cos(roll)
    const sr = Math.sin(roll)
    const focal = 3

    for (const p of stars) {
      const a = p.theta + p.speed * t + yaw
      const x = Math.cos(a) * p.r
      const z0 = Math.sin(a) * p.r
      const y1 = p.y * ci - z0 * si
      const z1 = p.y * si + z0 * ci
      const xr = x * cr - y1 * sr
      const yr = x * sr + y1 * cr
      const s = focal / (focal + z1)
      const size = p.size * s * (small ? 3.2 : 3.8)
      ctx.globalAlpha = p.alpha * (0.65 + 0.35 * Math.sin(t * p.tw + p.phase)) * Math.min(1, s * s)
      ctx.drawImage(sprites[p.tone], cx + xr * R * s - size / 2, cy + yr * R * s - size / 2, size, size)
    }

    // core glow, breathing, brighter right after a stream arrives
    flare *= 0.96
    const breathe = 1 + Math.sin(t * 0.9) * 0.04
    ctx.globalAlpha = 0.55 + flare * 0.4
    let s = R * 0.95 * breathe
    ctx.drawImage(sprites[2], cx - s / 2, cy - s / 2, s, s)
    ctx.globalAlpha = 0.9
    s = R * (0.34 + flare * 0.12) * breathe
    ctx.drawImage(sprites[0], cx - s / 2, cy - s / 2, s, s)

    // streams from tickets into the core (quadratic curve, ease-in)
    for (let i = sparks.length - 1; i >= 0; i--) {
      const sp = sparks[i]
      const k = (time - sp.start) / sp.dur
      if (k < 0) continue
      if (k >= 1) {
        flare = Math.min(1.4, flare + 0.035)
        if (sp.last) sp.done?.()
        sparks.splice(i, 1)
        continue
      }
      for (let trail = 0; trail < 4; trail++) {
        const q = Math.max(0, k - trail * 0.025)
        const e = q * q * (3 - 2 * q)
        const u = 1 - e
        const px = u * u * sp.x0 + 2 * u * e * sp.cx + e * e * cx
        const py = u * u * sp.y0 + 2 * u * e * sp.cy + e * e * cy
        const size = sp.size * (1 - trail * 0.22) * (1 - e * 0.5)
        ctx.globalAlpha = (1 - trail * 0.25) * Math.min(1, k * 6)
        ctx.drawImage(sprites[sp.tone], px - size / 2, py - size / 2, size, size)
      }
    }
    ctx.globalAlpha = 1
    ctx.globalCompositeOperation = 'source-over'
  }

  const loop = (time: number) => {
    draw(time)
    frame = requestAnimationFrame(loop)
  }

  readColors()
  resize()
  const ro = new ResizeObserver(resize)
  ro.observe(wrap)
  const mo = new MutationObserver(() => {
    readColors()
    if (still) draw(0)
  })
  mo.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  if (!still) {
    host.addEventListener('pointermove', onMove)
    host.addEventListener('pointerleave', onLeave)
    frame = requestAnimationFrame(loop)
  }

  return {
    stream(from, n, onArrive) {
      if (still) return
      const box = wrap.getBoundingClientRect()
      const r = from.getBoundingClientRect()
      if (r.width === 0) {
        onArrive()
        return
      }
      const x0 = r.left - box.left + 22
      const y0 = r.top - box.top + r.height / 2
      const { cx, cy } = geometry()
      const now = performance.now()
      for (let i = 0; i < n; i++) {
        // the control point bends the path sideways so streams swirl in
        const bend = 0.35 + Math.random() * 0.25
        sparks.push({
          x0: x0 + gauss() * 10,
          y0: y0 + gauss() * 8,
          cx: (x0 + cx) / 2 + (cy - y0) * bend + gauss() * 30,
          cy: (y0 + cy) / 2 - (cx - x0) * bend + gauss() * 30,
          start: now + i * 14 + Math.random() * 60,
          dur: 1500 + Math.random() * 500,
          size: 4 + Math.random() * 7,
          tone: Math.floor(Math.random() * 3),
          last: i === n - 1,
          done: onArrive,
        })
      }
    },
    destroy() {
      cancelAnimationFrame(frame)
      ro.disconnect()
      mo.disconnect()
      host.removeEventListener('pointermove', onMove)
      host.removeEventListener('pointerleave', onLeave)
    },
  }
}
