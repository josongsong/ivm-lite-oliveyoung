import { useMemo } from 'react'
import { motion } from 'framer-motion'
import { ArrowRight, Minus, Plus, RefreshCw } from 'lucide-react'
import type { VersionDiff } from '@/shared/types'
import './DiffViewer.css'

interface DiffViewerProps {
  fromVersion: number
  toVersion: number
  diffs: VersionDiff[]
  fromData?: Record<string, unknown>
  toData?: Record<string, unknown>
}

export function DiffViewer({ fromVersion, toVersion, diffs }: DiffViewerProps) {
  // 변경 타입별 그룹핑
  const groupedDiffs = useMemo(() => {
    const added = diffs.filter(d => d.type === 'added')
    const removed = diffs.filter(d => d.type === 'removed')
    const changed = diffs.filter(d => d.type === 'changed')
    return { added, removed, changed }
  }, [diffs])

  const stats = useMemo(() => ({
    added: groupedDiffs.added.length,
    removed: groupedDiffs.removed.length,
    changed: groupedDiffs.changed.length,
    total: diffs.length,
  }), [groupedDiffs, diffs.length])

  if (diffs.length === 0) {
    return (
      <div className="diff-viewer empty">
        <div className="diff-empty-state">
          <RefreshCw size={32} />
          <p>v{fromVersion}과 v{toVersion} 사이에 변경사항이 없습니다.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="diff-viewer">
      {/* 헤더 */}
      <div className="diff-header">
        <div className="diff-versions">
          <span className="diff-version from">v{fromVersion}</span>
          <ArrowRight size={16} />
          <span className="diff-version to">v{toVersion}</span>
        </div>
        <div className="diff-stats">
          {stats.added > 0 && (
            <span className="diff-stat added">
              <Plus size={12} /> {stats.added} added
            </span>
          )}
          {stats.removed > 0 && (
            <span className="diff-stat removed">
              <Minus size={12} /> {stats.removed} removed
            </span>
          )}
          {stats.changed > 0 && (
            <span className="diff-stat changed">
              <RefreshCw size={12} /> {stats.changed} changed
            </span>
          )}
        </div>
      </div>

      {/* Diff 리스트 */}
      <div className="diff-list">
        {diffs.map((diff, index) => (
          <motion.div
            key={diff.path}
            className={`diff-item ${diff.type}`}
            initial={{ opacity: 0, x: -10 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: index * 0.02 }}
          >
            <div className="diff-item-header">
              <span className="diff-type-icon">
                {diff.type === 'added' && <Plus size={14} />}
                {diff.type === 'removed' && <Minus size={14} />}
                {diff.type === 'changed' && <RefreshCw size={14} />}
              </span>
              <span className="diff-path">{diff.path}</span>
              <span className={`diff-type-badge ${diff.type}`}>{diff.type}</span>
            </div>
            <div className="diff-values">
              {diff.type === 'removed' || diff.type === 'changed' ? (
                <div className="diff-value old">
                  <span className="diff-value-prefix">-</span>
                  <code>{formatValue(diff.oldValue)}</code>
                </div>
              ) : null}
              {diff.type === 'added' || diff.type === 'changed' ? (
                <div className="diff-value new">
                  <span className="diff-value-prefix">+</span>
                  <code>{formatValue(diff.newValue)}</code>
                </div>
              ) : null}
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  )
}

function formatValue(value: unknown): string {
  if (value === undefined) return 'undefined'
  if (value === null) return 'null'
  if (typeof value === 'object') {
    const str = JSON.stringify(value, null, 2)
    return str.length > 200 ? str.slice(0, 200) + '...' : str
  }
  return String(value)
}

/** JSON 객체 간의 diff 계산 */
export function computeDiff(
  oldObj: Record<string, unknown>,
  newObj: Record<string, unknown>,
  path = ''
): VersionDiff[] {
  const diffs: VersionDiff[] = []
  const allKeys = new Set([...Object.keys(oldObj), ...Object.keys(newObj)])

  for (const key of allKeys) {
    const currentPath = path ? `${path}.${key}` : key
    const oldValue = oldObj[key]
    const newValue = newObj[key]

    if (!(key in oldObj)) {
      diffs.push({ path: currentPath, type: 'added', newValue })
    } else if (!(key in newObj)) {
      diffs.push({ path: currentPath, type: 'removed', oldValue })
    } else if (typeof oldValue === 'object' && typeof newValue === 'object' && oldValue !== null && newValue !== null && !Array.isArray(oldValue) && !Array.isArray(newValue)) {
      diffs.push(...computeDiff(oldValue as Record<string, unknown>, newValue as Record<string, unknown>, currentPath))
    } else if (JSON.stringify(oldValue) !== JSON.stringify(newValue)) {
      diffs.push({ path: currentPath, type: 'changed', oldValue, newValue })
    }
  }

  return diffs
}
