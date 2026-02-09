/**
 * Props Extractor Utility
 * Phase 1-A: 컴포넌트 Props 정보를 추출하는 유틸리티
 *
 * 브라우저 환경에서 동작하므로 정적 메타데이터 기반으로 구현.
 * ts-morph는 Node.js 환경 전용이라 빌드 타임 스크립트에서만 사용 가능.
 */

import type { ExtractedProp } from '../data/types'
import { COMPONENT_PROPS_REGISTRY } from '../data/propsRegistry'

// ============================================================================
// Props Extraction API
// ============================================================================

/**
 * 컴포넌트 이름으로 Props 정보 추출
 * @param componentName - 컴포넌트 이름 (예: 'Button', 'Input')
 * @returns Props 정보 배열
 */
export function extractPropsFromComponent(componentName: string): ExtractedProp[] {
  return COMPONENT_PROPS_REGISTRY[componentName] ?? []
}

/**
 * 모든 등록된 컴포넌트 이름 조회
 */
export function getRegisteredComponentNames(): string[] {
  return Object.keys(COMPONENT_PROPS_REGISTRY)
}

/**
 * Props 정보가 등록되어 있는지 확인
 */
export function hasPropsMetadata(componentName: string): boolean {
  return componentName in COMPONENT_PROPS_REGISTRY
}

/**
 * Required Props만 필터링
 */
export function getRequiredProps(componentName: string): ExtractedProp[] {
  return extractPropsFromComponent(componentName).filter((prop) => prop.required)
}

/**
 * Optional Props만 필터링
 */
export function getOptionalProps(componentName: string): ExtractedProp[] {
  return extractPropsFromComponent(componentName).filter((prop) => !prop.required)
}

/**
 * 특정 타입의 Props 필터링 (예: boolean, string)
 */
export function getPropsByType(componentName: string, typePattern: string): ExtractedProp[] {
  return extractPropsFromComponent(componentName).filter((prop) =>
    prop.type.toLowerCase().includes(typePattern.toLowerCase())
  )
}
