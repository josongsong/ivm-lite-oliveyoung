/**
 * Array utility functions
 */

/**
 * 배열에서 중복 항목 제거 (키 기준)
 * @param items 배열
 * @param keyFn 키 추출 함수
 * @returns 중복 제거된 배열
 *
 * @example
 * uniqueBy([{ id: 1, name: 'a' }, { id: 1, name: 'b' }], (i) => i.id)
 * // => [{ id: 1, name: 'a' }]
 */
export function uniqueBy<T>(items: T[], keyFn: (item: T) => string | number): T[] {
  const seen = new Set<string | number>()
  return items.filter((item) => {
    const key = keyFn(item)
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}
