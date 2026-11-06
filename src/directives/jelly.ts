import type { Directive } from 'vue'

/**
 * v-jelly：鼠标速度驱动的果冻抖动（参考哲风壁纸的图块效果）。
 *
 * 原理：mousemove 的位移向量注入「角速度」，卡片角度走欠阻尼弹簧向 0 回正——
 * 甩得越快倾斜越大，停下后过冲回弹几次再静止（这就是「抖动」的手感来源）。
 * 变换直接写 transform 走 GPU 合成，静止后清空并停掉 rAF 省电。
 *
 * 守卫：仅精确指针（鼠标）设备启用；prefers-reduced-motion 时完全禁用。
 * 注意：图块内图片需有基础放大（wall 变体已配 scale(1.08)），否则倾斜会露出底色。
 */

/** 弹簧刚度：越大回正越快 */
const STIFFNESS = 90
/** 阻尼系数：小于临界阻尼才有回弹的「果冻感」 */
const DAMPING = 6.5
/** 鼠标位移 → 角速度的注入系数（0.16 时普通甩动峰值仅 0.4°，肉眼不可见） */
const INJECT = 1.2
/** 倾斜角硬上限（deg） */
const MAX_DEG = 6

interface JellyState {
  tiltX: number
  tiltY: number
  velX: number
  velY: number
  raf: number
  lastTime: number
  lastX: number
  lastY: number
  disposed: boolean
}

const states = new WeakMap<HTMLElement, JellyState>()

function tick(el: HTMLElement, state: JellyState) {
  if (state.disposed) {
    return
  }
  const now = performance.now()
  const dt = Math.min((now - state.lastTime) / 1000, 0.032) || 0.016
  state.lastTime = now
  // 欠阻尼弹簧：加速度 = -k·角度 - c·角速度
  const accelX = -STIFFNESS * state.tiltX - DAMPING * state.velX
  const accelY = -STIFFNESS * state.tiltY - DAMPING * state.velY
  state.velX += accelX * dt
  state.velY += accelY * dt
  state.tiltX += state.velX * dt
  state.tiltY += state.velY * dt
  if (
    Math.abs(state.tiltX) < 0.02 &&
    Math.abs(state.tiltY) < 0.02 &&
    Math.abs(state.velX) < 0.02 &&
    Math.abs(state.velY) < 0.02
  ) {
    // 已静止：复位样式并停帧
    state.tiltX = state.tiltY = state.velX = state.velY = 0
    el.style.transform = ''
    state.raf = 0
    return
  }
  const rx = Math.max(-MAX_DEG, Math.min(MAX_DEG, state.tiltX))
  const ry = Math.max(-MAX_DEG, Math.min(MAX_DEG, state.tiltY))
  el.style.transform = `perspective(800px) rotateX(${rx.toFixed(3)}deg) rotateY(${ry.toFixed(3)}deg)`
  state.raf = requestAnimationFrame(() => tick(el, state))
}

function kick(el: HTMLElement, state: JellyState, dx: number, dy: number) {
  state.velY += Math.max(-40, Math.min(40, dx)) * INJECT
  state.velX += Math.max(-40, Math.min(40, dy)) * INJECT
  if (!state.raf) {
    state.lastTime = performance.now()
    state.raf = requestAnimationFrame(() => tick(el, state))
  }
}

export const vJelly: Directive<HTMLElement> = {
  mounted(el) {
    if (
      window.matchMedia('(prefers-reduced-motion: reduce)').matches ||
      !window.matchMedia('(pointer: fine)').matches
    ) {
      return
    }
    const state: JellyState = {
      tiltX: 0,
      tiltY: 0,
      velX: 0,
      velY: 0,
      raf: 0,
      lastTime: 0,
      lastX: 0,
      lastY: 0,
      disposed: false,
    }
    states.set(el, state)
    el.style.willChange = 'transform'
    el.addEventListener('mousemove', (event: MouseEvent) => {
      if (state.lastX || state.lastY) {
        kick(el, state, event.clientX - state.lastX, event.clientY - state.lastY)
      }
      state.lastX = event.clientX
      state.lastY = event.clientY
    })
    el.addEventListener('mouseleave', () => {
      state.lastX = 0
      state.lastY = 0
    })
  },
  unmounted(el) {
    const state = states.get(el)
    if (state) {
      state.disposed = true
      if (state.raf) {
        cancelAnimationFrame(state.raf)
      }
    }
    states.delete(el)
    el.style.transform = ''
    el.style.willChange = ''
  },
}
