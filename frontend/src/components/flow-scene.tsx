import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { CheckIcon } from 'lucide-react'

import { StatusDot } from '@/features/orders/order-status-badge'
import type { OrderStatus } from '@/features/orders/api'
import { formatMoney, initials } from '@/lib/format'
import { cn } from '@/lib/utils'

/*
 * "Flow" illustration for the sign-in pages. Three floating cards (client -> order
 * -> payment) are joined by a dashed track with particles running along it, and a
 * small story loops: the order moves NEW -> IN_PROGRESS -> COMPLETED while the
 * payment fills 0% -> 40% -> 100%. The stack tilts gently toward the pointer.
 * Solid theme colours, no gradients. Under prefers-reduced-motion it stands still.
 */

const TOTAL = 250_000
const STEPS: { status: OrderStatus; paid: number }[] = [
  { status: 'NEW', paid: 0 },
  { status: 'IN_PROGRESS', paid: 100_000 },
  { status: 'COMPLETED', paid: TOTAL },
]
const STEP_MS = 2800
const PARTICLES = 22

type Pt = [number, number]
/* Track in 0..1 stage coordinates: under the client card, down to the order card,
 * through it, then on to the payment card. Visible only in the gaps between cards. */
const TRACK: [Pt, Pt, Pt, Pt][] = [
  [[0.3, 0.18], [0.3, 0.34], [0.64, 0.24], [0.64, 0.4]],
  [[0.64, 0.4], [0.64, 0.46], [0.64, 0.52], [0.64, 0.58]],
  [[0.64, 0.58], [0.64, 0.74], [0.34, 0.66], [0.34, 0.82]],
]

function bezier([a, b, c, d]: [Pt, Pt, Pt, Pt], t: number): Pt {
  const u = 1 - t
  const x = u * u * u * a[0] + 3 * u * u * t * b[0] + 3 * u * t * t * c[0] + t * t * t * d[0]
  const y = u * u * u * a[1] + 3 * u * u * t * b[1] + 3 * u * t * t * c[1] + t * t * t * d[1]
  return [x, y]
}
/** Position along the whole track, t in 0..1 */
function along(t: number): Pt {
  const scaled = Math.min(t, 0.9999) * TRACK.length
  const i = Math.floor(scaled)
  return bezier(TRACK[i], scaled - i)
}

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function FlowScene({ className }: { className?: string }) {
  const { t } = useTranslation()
  const stageRef = useRef<HTMLDivElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [step, setStep] = useState(1)

  // The story: advance the order / payment every few seconds
  useEffect(() => {
    if (prefersReducedMotion()) return
    const id = window.setInterval(() => setStep((s) => (s + 1) % STEPS.length), STEP_MS)
    return () => window.clearInterval(id)
  }, [])

  // Pointer tilt
  useEffect(() => {
    const stage = stageRef.current
    if (!stage || prefersReducedMotion()) return
    let frame = 0
    let target = { x: 0, y: 0 }
    let current = { x: 0, y: 0 }
    const onMove = (e: PointerEvent) => {
      const r = stage.getBoundingClientRect()
      target = { x: ((e.clientX - r.left) / r.width - 0.5) * 2, y: ((e.clientY - r.top) / r.height - 0.5) * 2 }
    }
    const onLeave = () => {
      target = { x: 0, y: 0 }
    }
    const tick = () => {
      current = { x: current.x + (target.x - current.x) * 0.08, y: current.y + (target.y - current.y) * 0.08 }
      stage.style.setProperty('--tilt-x', `${(-current.y * 6).toFixed(2)}deg`)
      stage.style.setProperty('--tilt-y', `${(current.x * 8).toFixed(2)}deg`)
      frame = requestAnimationFrame(tick)
    }
    stage.addEventListener('pointermove', onMove)
    stage.addEventListener('pointerleave', onLeave)
    frame = requestAnimationFrame(tick)
    return () => {
      stage.removeEventListener('pointermove', onMove)
      stage.removeEventListener('pointerleave', onLeave)
      cancelAnimationFrame(frame)
    }
  }, [])

  // Track + particles
  useEffect(() => {
    const canvas = canvasRef.current
    const ctx = canvas?.getContext('2d')
    if (!canvas || !ctx) return
    const reduced = prefersReducedMotion()
    let W = 0
    let H = 0
    let colors = { particle: '#4f5bd5', track: '#ccc' }
    const readColors = () => {
      const s = getComputedStyle(canvas)
      colors = {
        particle: s.getPropertyValue('--particle').trim() || colors.particle,
        track: s.getPropertyValue('--track').trim() || colors.track,
      }
    }
    const resize = () => {
      const dpr = Math.min(2, window.devicePixelRatio || 1)
      W = canvas.clientWidth
      H = canvas.clientHeight
      canvas.width = Math.round(W * dpr)
      canvas.height = Math.round(H * dpr)
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      readColors()
      if (reduced) draw()
    }

    const particles = Array.from({ length: PARTICLES }, (_, i) => ({
      t: i / PARTICLES,
      speed: 0.0011 + Math.random() * 0.0008,
      size: 1.8 + Math.random() * 1.8,
    }))

    const draw = () => {
      ctx.clearRect(0, 0, W, H)
      // dashed track
      ctx.strokeStyle = colors.track
      ctx.lineWidth = 1.5
      ctx.setLineDash([4, 6])
      ctx.beginPath()
      for (const [a, b, c, d] of TRACK) {
        ctx.moveTo(a[0] * W, a[1] * H)
        ctx.bezierCurveTo(b[0] * W, b[1] * H, c[0] * W, c[1] * H, d[0] * W, d[1] * H)
      }
      ctx.stroke()
      ctx.setLineDash([])
      // particles
      ctx.fillStyle = colors.particle
      for (const p of particles) {
        const [x, y] = along(p.t)
        ctx.globalAlpha = 0.35 + 0.65 * Math.min(1, Math.min(p.t, 1 - p.t) * 8)
        ctx.beginPath()
        ctx.arc(x * W, y * H, p.size, 0, Math.PI * 2)
        ctx.fill()
      }
      ctx.globalAlpha = 1
    }

    // Theme switches change the token values
    const themeObserver = new MutationObserver(() => {
      readColors()
      if (reduced) draw()
    })
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    const sizeObserver = new ResizeObserver(resize)
    sizeObserver.observe(canvas)
    resize()

    let frame = 0
    const loop = () => {
      for (const p of particles) p.t = (p.t + p.speed) % 1
      draw()
      frame = requestAnimationFrame(loop)
    }
    if (!reduced) frame = requestAnimationFrame(loop)
    return () => {
      cancelAnimationFrame(frame)
      sizeObserver.disconnect()
      themeObserver.disconnect()
    }
  }, [])

  const { status, paid } = STEPS[step]
  const percent = Math.round((paid / TOTAL) * 100)
  const money = (n: number) => formatMoney(n, 'KZT')

  return (
    <div ref={stageRef} className={className} style={{ perspective: '1200px' }} aria-hidden>
      <div
        className="relative mx-auto aspect-square w-full max-w-[520px] will-change-transform"
        style={{ transform: 'rotateX(var(--tilt-x, 0deg)) rotateY(var(--tilt-y, 0deg))', transformStyle: 'preserve-3d' }}
      >
        <canvas
          ref={canvasRef}
          className="absolute inset-0 size-full [--particle:var(--primary)] [--track:var(--border)] dark:[--track:oklch(1_0_0/18%)]"
        />

        {/* Client */}
        <SceneCard className="top-[3%] left-[2%] w-[56%]" delay="0s">
          <p className="text-[11px] font-medium tracking-wider text-muted-foreground uppercase">{t('scene.client')}</p>
          <div className="mt-2.5 flex items-center gap-3">
            <span className="flex size-10 items-center justify-center rounded-full bg-accent text-sm font-semibold text-accent-foreground">
              {initials(t('scene.clientName'))}
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">{t('scene.clientName')}</p>
              <p className="truncate text-xs text-muted-foreground tabular-nums">+7 701 ··· ·· 33</p>
            </div>
          </div>
        </SceneCard>

        {/* Order */}
        <SceneCard className="top-[37%] right-0 w-[62%]" delay="0.8s">
          <div className="flex items-center justify-between gap-2">
            <p className="text-[11px] font-medium tracking-wider text-muted-foreground uppercase">{t('scene.order')}</p>
            <span className="font-mono text-[11px] text-muted-foreground">ORD-000128</span>
          </div>
          <p className="mt-1.5 truncate text-sm font-semibold">{t('scene.orderTitle')}</p>
          <div className="mt-3 flex items-center gap-2">
            <span
              key={status}
              className="cf-num inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-2 py-0.5 text-xs"
            >
              <StatusDot status={status} />
              {t(`orderStatus.${status}`)}
            </span>
            <span className="ml-auto text-sm font-semibold tabular-nums">{money(TOTAL)}</span>
          </div>
        </SceneCard>

        {/* Payment */}
        <SceneCard className="bottom-[2%] left-[8%] w-[58%]" delay="1.6s">
          <div className="flex items-center justify-between gap-2">
            <p className="text-[11px] font-medium tracking-wider text-muted-foreground uppercase">{t('scene.payment')}</p>
            <span
              className={cn(
                'flex size-5 items-center justify-center rounded-full bg-success text-success-foreground transition-[opacity,transform] duration-500 ease-[var(--ease-spring)]',
                percent === 100 ? 'scale-100 opacity-100' : 'scale-50 opacity-0',
              )}
              title={t('scene.paid')}
            >
              <CheckIcon className="size-3" strokeWidth={3} />
            </span>
          </div>
          <div className="mt-2 flex items-baseline justify-between gap-2">
            <p key={paid} className="cf-num text-lg font-semibold tabular-nums">
              {money(paid)}
            </p>
            <span className="text-xs text-muted-foreground tabular-nums">{percent}%</span>
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted">
            <div
              className="h-full origin-left rounded-full bg-success transition-transform duration-1000 ease-[var(--ease-out-expo)]"
              style={{ transform: `scaleX(${percent / 100})` }}
            />
          </div>
        </SceneCard>
      </div>
    </div>
  )
}

function SceneCard({ className, delay, children }: { className: string; delay: string; children: ReactNode }) {
  return (
    <div
      className={cn(
        'cf-float absolute rounded-2xl bg-card p-4 shadow-[var(--shadow-raised)] ring-1 ring-foreground/10',
        className,
      )}
      style={{ '--d': delay } as CSSProperties}
    >
      {children}
    </div>
  )
}
