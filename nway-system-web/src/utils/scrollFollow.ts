/**
 * 流式输出时的滚动跟随：
 * 容器默认贴底展示最新内容；用户手动上滚后暂停跟随，回到底部后自动恢复。
 * 跟随状态按元素记录，互不影响。
 */

// 距底部多少像素内仍视为“在底部”，避免滚动位置小数导致误判
const BOTTOM_GAP = 24

// 仅记录被用户离开底部的容器，未记录的容器默认跟随
const detached = new WeakSet<HTMLElement>()

function isNearBottom(el: HTMLElement): boolean {
  return el.scrollHeight - el.scrollTop - el.clientHeight <= BOTTOM_GAP
}

/** 绑定到滚动容器的 scroll 事件：离开底部暂停跟随，回到底部恢复跟随 */
export function trackFollowScroll(event: Event): void {
  const el = event.currentTarget as HTMLElement | null
  if (!el) return
  if (isNearBottom(el)) detached.delete(el)
  else detached.add(el)
}

/** 内容更新后贴底；用户已上滚且未强制时保持当前位置 */
export function followBottom(el: HTMLElement | null | undefined, force = false): void {
  if (!el) return
  if (force) detached.delete(el)
  else if (detached.has(el)) return
  // 临时覆盖容器上的 scroll-behavior: smooth，流式输出需要瞬时贴底
  const previous = el.style.scrollBehavior
  el.style.scrollBehavior = 'auto'
  el.scrollTop = el.scrollHeight
  el.style.scrollBehavior = previous
}

/** 让容器内最后一个匹配元素（通常属于正在流式输出的消息）贴底 */
export function followLast(root: HTMLElement | null | undefined, selector: string): void {
  if (!root) return
  const list = root.querySelectorAll<HTMLElement>(selector)
  if (list.length) followBottom(list[list.length - 1])
}
