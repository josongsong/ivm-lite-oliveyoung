/**
 * className 합성 유틸리티 함수
 *
 * 여러 className을 안전하게 합성합니다.
 * null, undefined, false 값은 자동으로 필터링됩니다.
 *
 * @example
 * ```tsx
 * import { cn } from '@/shared/utils/cn'
 *
 * // 기본 사용
 * <div className={cn('ui-button', `ui-button--${variant}`, className)} />
 *
 * // 조건부 클래스
 * <div className={cn('base-class', isActive && 'active', isDisabled && 'disabled')} />
 *
 * // 배열 사용
 * <div className={cn(['class1', 'class2', condition && 'class3'])} />
 * ```
 */
export function cn(...classes: (string | undefined | null | false | (string | undefined | null | false)[])[]): string {
  return classes
    .flat()
    .filter((cls): cls is string => Boolean(cls))
    .join(' ')
}
