/**
 * SearchBar 유틸리티 함수
 */
import type { SearchHistoryItem, SearchSuggestion } from '@/shared/types'

const SEARCH_HISTORY_KEY = 'explorer_search_history'
const MAX_HISTORY = 10

export function getSearchHistory(): SearchHistoryItem[] {
  try {
    const stored = localStorage.getItem(SEARCH_HISTORY_KEY)
    if (!stored) return []
    const parsed = JSON.parse(stored)
    if (!Array.isArray(parsed)) {
      localStorage.removeItem(SEARCH_HISTORY_KEY)
      return []
    }
    return parsed
  } catch {
    localStorage.removeItem(SEARCH_HISTORY_KEY)
    return []
  }
}

export function addToHistory(item: Omit<SearchHistoryItem, 'timestamp'>) {
  const history = getSearchHistory()
  const newItem: SearchHistoryItem = { ...item, timestamp: Date.now() }
  const filtered = history.filter(h => h.query !== item.query)
  const updated = [newItem, ...filtered].slice(0, MAX_HISTORY)
  localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(updated))
}

export function parseSearchQuery(query: string) {
  const result: { tenant?: string; entity?: string; version?: string } = {}
  const tenantMatch = query.match(/tenant:(\S+)/i)
  if (tenantMatch) result.tenant = tenantMatch[1]
  const entityMatch = query.match(/entity:(\S+)/i)
  if (entityMatch) result.entity = entityMatch[1]
  const versionMatch = query.match(/v:(\S+)/i)
  if (versionMatch) result.version = versionMatch[1]

  if (!result.entity) {
    const plainText = query.replace(/tenant:\S+/gi, '').replace(/v:\S+/gi, '').trim()
    if (plainText && !plainText.includes(':')) result.entity = plainText
  }
  return result
}

export function buildQueryWithSuggestion(query: string, suggestion: SearchSuggestion): string {
  const prefix = `${suggestion.type}:`
  const replacement = `${prefix}${suggestion.value}`
  const lowerQuery = query.toLowerCase()
  const lowerPrefix = prefix.toLowerCase()
  const prefixIndex = lowerQuery.indexOf(lowerPrefix)

  if (prefixIndex !== -1) {
    const afterPrefix = prefixIndex + prefix.length
    let endIndex = afterPrefix
    while (endIndex < query.length && !/\s/.test(query[endIndex])) endIndex++
    return query.slice(0, prefixIndex) + replacement + query.slice(endIndex)
  }
  return query.trim() + ' ' + replacement
}
