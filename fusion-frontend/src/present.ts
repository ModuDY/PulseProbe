import type { Quality, SourceStateName } from './types'

/**
 * 展示层文案。所有“不确定”的呈现集中在这里，保证同一语义在各处写法一致。
 */

export const qualityLabel: Record<Quality, string> = {
  valid: '有效',
  partial: '部分',
  no_data: '无数据',
  reset: '计数重置',
  stale: '已过期',
  unknown: '未知',
  unsupported: '不支持',
}

export const qualityTone: Record<Quality, 'ok' | 'warn' | 'bad' | 'muted'> = {
  valid: 'ok',
  partial: 'warn',
  no_data: 'muted',
  reset: 'muted',
  stale: 'warn',
  unknown: 'muted',
  unsupported: 'muted',
}

export const sourceLabel: Record<SourceStateName, string> = {
  ok: '正常',
  stale: '数据过期',
  unavailable: '不可用',
  unsupported: '能力不支持',
}

export const sourceTone: Record<SourceStateName, 'ok' | 'warn' | 'bad' | 'muted'> = {
  ok: 'ok',
  stale: 'warn',
  unavailable: 'bad',
  unsupported: 'muted',
}

export const healthLabel = {
  healthy: '正常',
  warning: '注意',
  critical: '异常',
  unknown: '未知',
} as const

/** 单位只做展示修饰，换算在适配器完成，前端不改数值 */
export function formatValue(value: number | null, unit: string): string {
  if (value === null || value === undefined) {
    return '—'
  }
  switch (unit) {
    case 'bytes':
      return formatBytes(value)
    case 'percent':
      return value.toFixed(1) + '%'
    case 'ratio':
      return (value * 100).toFixed(1) + '%'
    case 'milliseconds':
      return value.toFixed(0) + ' ms'
    case 'requests_per_second':
      return value.toFixed(1) + ' req/s'
    case 'cores':
      return value.toFixed(2) + ' 核'
    case 'count':
      return value.toFixed(0)
    default:
      return String(Math.round(value * 100) / 100)
  }
}

export function formatBytes(value: number): string {
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
  let size = value
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return size.toFixed(index === 0 ? 0 : 2) + ' ' + units[index]
}

/** 数据年龄：总览必须能看到“多久之前的数”，不能只给一个笼统的更新时间 */
export function formatAge(seconds: number | null): string {
  if (seconds === null || seconds === undefined) {
    return '时间未知'
  }
  if (seconds < 60) {
    return seconds + ' 秒前'
  }
  if (seconds < 3600) {
    return Math.floor(seconds / 60) + ' 分钟前'
  }
  return Math.floor(seconds / 3600) + ' 小时前'
}

export function formatClock(iso: string | null): string {
  if (!iso) {
    return '—'
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN', { hour12: false })
}
