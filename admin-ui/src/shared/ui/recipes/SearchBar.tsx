import { useCallback, useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Clock, Database, Eye, Hash, Layers, Search, X } from 'lucide-react'
import type { SearchHistoryItem, SearchSuggestion } from '@/shared/types'
import './SearchBar.css'

export interface SearchBarProps {
  onSearch: (tenant: string, entityId: string, version?: number | 'latest') => void
  onAutocomplete?: (query: string, tenant: string) => Promise<SearchSuggestion[]>
  defaultTenant?: string
  placeholder?: string
  historyKey?: string
}

const DEFAULT_HISTORY_KEY = 'search_history'
const MAX_HISTORY = 10

function getSearchHistory(historyKey: string): SearchHistoryItem[] {
  try {
    const stored = localStorage.getItem(historyKey)
    return stored ? JSON.parse(stored) : []
  } catch {
    return []
  }
}

function addToHistory(historyKey: string, item: Omit<SearchHistoryItem, 'timestamp'>) {
  const history = getSearchHistory(historyKey)
  const newItem: SearchHistoryItem = { ...item, timestamp: Date.now() }
  const filtered = history.filter(h => h.query !== item.query)
  const updated = [newItem, ...filtered].slice(0, MAX_HISTORY)
  localStorage.setItem(historyKey, JSON.stringify(updated))
}

const suggestionIcons: Record<SearchSuggestion['type'], React.ElementType> = {
  tenant: Database,
  entity: Hash,
  version: Clock,
  sliceType: Layers,
  viewDef: Eye,
}

export function SearchBar({
  onSearch,
  onAutocomplete,
  defaultTenant = 'oliveyoung',
  placeholder = 'tenant:oliveyoung entity:SKU-12345 v:latest',
  historyKey = DEFAULT_HISTORY_KEY,
}: SearchBarProps) {
  const [query, setQuery] = useState('')
  const [isFocused, setIsFocused] = useState(false)
  const [selectedIndex, setSelectedIndex] = useState(-1)
  const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([])
  const [, setIsLoadingSuggestions] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  // 파싱된 검색어
  const parsedQuery = parseSearchQuery(query)
  const currentTenant = parsedQuery.tenant || defaultTenant

  // 검색 히스토리
  const [history] = useState(() => getSearchHistory(historyKey))

  // 자동완성 로드
  useEffect(() => {
    if (!onAutocomplete || query.length <= 1 || !isFocused) {
      setSuggestions(
        history.slice(0, 5).map(h => ({
          type: 'entity' as const,
          value: h.entityId,
          label: h.entityId,
          description: `${h.tenant} - ${new Date(h.timestamp).toLocaleDateString()}`,
        }))
      )
      return
    }

    setIsLoadingSuggestions(true)
    onAutocomplete(query, currentTenant)
      .then((sugs) => {
        setSuggestions(sugs)
      })
      .catch(() => {
        setSuggestions([])
      })
      .finally(() => {
        setIsLoadingSuggestions(false)
      })
  }, [query, currentTenant, isFocused, onAutocomplete, history])


  // 제안 적용
  const applySuggestion = useCallback((suggestion: SearchSuggestion) => {
    const newQuery = buildQueryWithSuggestion(query, suggestion)
    setQuery(newQuery)
    setSelectedIndex(-1)
    inputRef.current?.focus()
  }, [query])

  // 검색 실행
  const executeSearch = useCallback(() => {
    const parsed = parseSearchQuery(query)
    if (!parsed.entity) return

    const tenant = parsed.tenant || defaultTenant
    const version = parsed.version === 'latest' ? 'latest' : parsed.version ? parseInt(parsed.version) : undefined

    addToHistory(historyKey, { query, tenant, entityId: parsed.entity })
    onSearch(tenant, parsed.entity, version)
    setIsFocused(false)
  }, [query, defaultTenant, historyKey, onSearch])

  // 키보드 네비게이션
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIndex(prev => Math.min(prev + 1, suggestions.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIndex(prev => Math.max(prev - 1, -1))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (selectedIndex >= 0 && suggestions[selectedIndex]) {
        applySuggestion(suggestions[selectedIndex])
      } else {
        executeSearch()
      }
    } else if (e.key === 'Escape') {
      setIsFocused(false)
      inputRef.current?.blur()
    }
  }, [selectedIndex, suggestions, applySuggestion, executeSearch])

  // 외부 클릭 감지
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsFocused(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <div className="search-bar-container" ref={containerRef}>
      <div className={`search-bar ${isFocused ? 'focused' : ''}`}>
        <Search size={18} className="search-icon" />
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={e => {
            setQuery(e.target.value)
            setSelectedIndex(-1)
          }}
          onFocus={() => setIsFocused(true)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          className="search-input"
          autoComplete="off"
          spellCheck={false}
        />
        {query && (
          <button
            className="clear-btn"
            onClick={() => {
              setQuery('')
              inputRef.current?.focus()
            }}
          >
            <X size={14} />
          </button>
        )}
        <button
          className="search-btn"
          onClick={executeSearch}
          disabled={!parseSearchQuery(query).entity}
        >
          <Search size={16} />
        </button>
      </div>

      {/* 검색 힌트 */}
      {isFocused && !query && (
        <motion.div
          className="search-hints"
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <div className="hint-group">
            <span className="hint-label">Syntax:</span>
            <code className="hint-code">tenant:&lt;name&gt;</code>
            <code className="hint-code">entity:&lt;id&gt;</code>
            <code className="hint-code">v:&lt;version|latest&gt;</code>
          </div>
        </motion.div>
      )}

      {/* 자동완성 드롭다운 */}
      <AnimatePresence>
        {isFocused && suggestions.length > 0 && (
          <motion.div
            className="suggestions-dropdown"
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
          >
            {suggestions.map((suggestion, index) => {
              const Icon = suggestionIcons[suggestion.type]
              return (
                <button
                  key={`${suggestion.type}-${suggestion.value}-${index}`}
                  className={`suggestion-item ${index === selectedIndex ? 'selected' : ''}`}
                  onClick={() => applySuggestion(suggestion)}
                  onMouseEnter={() => setSelectedIndex(index)}
                >
                  <span className="suggestion-icon">
                    <Icon size={14} />
                  </span>
                  <span className="suggestion-type">{suggestion.type}</span>
                  <span className="suggestion-value">{suggestion.label}</span>
                  {suggestion.description && (
                    <span className="suggestion-desc">{suggestion.description}</span>
                  )}
                </button>
              )
            })}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

/** 검색어 파싱 */
function parseSearchQuery(query: string) {
  const result: { tenant?: string; entity?: string; version?: string } = {}

  const tenantMatch = query.match(/tenant:(\S+)/i)
  if (tenantMatch) result.tenant = tenantMatch[1]

  const entityMatch = query.match(/entity:(\S+)/i)
  if (entityMatch) result.entity = entityMatch[1]

  const versionMatch = query.match(/v:(\S+)/i)
  if (versionMatch) result.version = versionMatch[1]

  // 단순 입력 (tenant:entity: 없이)
  if (!result.entity) {
    const plainText = query
      .replace(/tenant:\S+/gi, '')
      .replace(/v:\S+/gi, '')
      .trim()
    if (plainText && !plainText.includes(':')) {
      result.entity = plainText
    }
  }

  return result
}

/** 제안 적용 후 쿼리 생성 */
function buildQueryWithSuggestion(query: string, suggestion: SearchSuggestion): string {
  const prefix = `${suggestion.type}:`
  const replacement = `${prefix}${suggestion.value}`

  const regex = new RegExp(`${suggestion.type}:\\S*`, 'i')
  if (regex.test(query)) {
    return query.replace(regex, replacement)
  }

  return query.trim() + ' ' + replacement
}
