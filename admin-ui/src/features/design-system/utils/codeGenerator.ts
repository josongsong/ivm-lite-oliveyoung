/**
 * Code Generator Utility
 * Phase 1-B: 컴포넌트와 Props를 받아 코드 스니펫을 생성하는 유틸리티
 */

import type { ProjectSettings } from '../data/types'

// ============================================================================
// Types
// ============================================================================

interface GenerateCodeOptions extends Partial<ProjectSettings> {
  /** 멀티라인 포맷팅 */
  multiline?: boolean
  /** 들여쓰기 공백 수 */
  indent?: number
  /** Import 문 포함 여부 */
  includeImport?: boolean
}

// ============================================================================
// Default Settings
// ============================================================================

const DEFAULT_SETTINGS: ProjectSettings = {
  framework: 'react',
  style: 'vanilla',
  import: 'alias',
  typescript: true,
}

// ============================================================================
// Code Generation
// ============================================================================

/**
 * 컴포넌트 코드 스니펫 생성
 *
 * @param componentName - 컴포넌트 이름 (예: 'Button')
 * @param props - Props 객체
 * @param options - 생성 옵션
 * @returns 포맷팅된 코드 문자열
 *
 * @example
 * ```ts
 * generateCodeSnippet('Button', { variant: 'primary', loading: true }, { includeImport: true })
 * // Returns:
 * // import { Button } from '@/shared/ui'
 * //
 * // <Button variant="primary" loading>
 * //   Click me
 * // </Button>
 * ```
 */
export function generateCodeSnippet(
  componentName: string,
  props: Record<string, unknown>,
  options: GenerateCodeOptions = {}
): string {
  const settings = { ...DEFAULT_SETTINGS, ...options }
  const { multiline = false, indent = 2, includeImport = false } = options

  const parts: string[] = []

  // Import 문
  if (includeImport) {
    const importPath = generateImportPath(componentName, settings)
    parts.push(`import { ${componentName} } from '${importPath}'`)
    parts.push('')
  }

  // 컴포넌트 JSX
  const jsxCode = generateJSX(componentName, props, { multiline, indent })
  parts.push(jsxCode)

  return parts.join('\n')
}

/**
 * Import 경로 생성
 */
export function generateImportPath(
  componentName: string,
  settings: Partial<ProjectSettings> = {}
): string {
  const { import: importType = 'alias', customImportPath } = settings

  if (customImportPath) {
    return customImportPath
  }

  if (importType === 'alias') {
    return '@/shared/ui'
  }

  // relative import
  return `../shared/ui/${componentName}`
}

/**
 * JSX 코드 생성
 */
function generateJSX(
  componentName: string,
  props: Record<string, unknown>,
  options: { multiline: boolean; indent: number }
): string {
  const { multiline, indent } = options
  const children = props.children as string | undefined
  const propsWithoutChildren = { ...props }
  delete propsWithoutChildren.children

  const propsEntries = Object.entries(propsWithoutChildren).filter(
    ([, value]) => value !== undefined && value !== null && value !== ''
  )

  // Props가 없고 children도 없는 경우
  if (propsEntries.length === 0 && !children) {
    return `<${componentName} />`
  }

  // Self-closing (children 없음)
  if (!children) {
    if (multiline && propsEntries.length > 2) {
      return formatMultilineProps(componentName, propsEntries, indent, true)
    }
    const propsString = formatInlineProps(propsEntries)
    return `<${componentName}${propsString ? ` ${propsString}` : ''} />`
  }

  // With children
  if (multiline && propsEntries.length > 2) {
    return formatMultilineProps(componentName, propsEntries, indent, false, children)
  }

  const propsString = formatInlineProps(propsEntries)
  return `<${componentName}${propsString ? ` ${propsString}` : ''}>
  ${children}
</${componentName}>`
}

/**
 * Props를 한 줄로 포맷팅
 */
function formatInlineProps(entries: [string, unknown][]): string {
  return entries.map(([key, value]) => formatPropValue(key, value)).join(' ')
}

/**
 * Props를 여러 줄로 포맷팅
 */
function formatMultilineProps(
  componentName: string,
  entries: [string, unknown][],
  indent: number,
  selfClosing: boolean,
  children?: string
): string {
  const indentStr = ' '.repeat(indent)
  const propsLines = entries.map(([key, value]) => `${indentStr}${formatPropValue(key, value)}`)

  if (selfClosing) {
    return `<${componentName}\n${propsLines.join('\n')}\n/>`
  }

  return `<${componentName}
${propsLines.join('\n')}
>
${indentStr}${children}
</${componentName}>`
}

/**
 * 개별 prop 값을 문자열로 포맷팅
 */
function formatPropValue(key: string, value: unknown): string {
  // Boolean true: <Button loading />
  if (value === true) {
    return key
  }

  // Boolean false: <Button disabled={false} />
  if (value === false) {
    return `${key}={false}`
  }

  // String: <Button variant="primary" />
  if (typeof value === 'string') {
    // JSX expression이 포함된 경우 (예: <Icon />)
    if (value.startsWith('<') || value.startsWith('{')) {
      return `${key}={${value}}`
    }
    return `${key}="${escapeString(value)}"`
  }

  // Number: <Input maxLength={100} />
  if (typeof value === 'number') {
    return `${key}={${value}}`
  }

  // Function (시그니처만 표시)
  if (typeof value === 'function') {
    return `${key}={() => {}}`
  }

  // Array or Object
  if (typeof value === 'object') {
    return `${key}={${JSON.stringify(value)}}`
  }

  return `${key}={${String(value)}}`
}

/**
 * 문자열 이스케이프
 */
function escapeString(str: string): string {
  return str
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n')
}

// ============================================================================
// Code Formatting Utilities
// ============================================================================

/**
 * 코드에 하이라이팅을 위한 토큰 정보 추가
 */
export interface CodeToken {
  type: 'tag' | 'prop' | 'string' | 'boolean' | 'number' | 'bracket' | 'text'
  value: string
  start: number
  end: number
}

/**
 * 간단한 JSX 토크나이저 (Syntax highlighting용)
 */
export function tokenizeJSX(code: string): CodeToken[] {
  const tokens: CodeToken[] = []

  // 태그 이름 매칭
  const tagRegex = /<\/?([A-Z][a-zA-Z0-9]*)/g
  let match: RegExpExecArray | null

  while ((match = tagRegex.exec(code)) !== null) {
    tokens.push({
      type: 'tag',
      value: match[1],
      start: match.index + (match[0].startsWith('</') ? 2 : 1),
      end: match.index + match[0].length,
    })
  }

  // Prop 이름 매칭
  const propRegex = /\s([a-zA-Z][a-zA-Z0-9]*)(?==|[\s/>])/g
  while ((match = propRegex.exec(code)) !== null) {
    tokens.push({
      type: 'prop',
      value: match[1],
      start: match.index + 1,
      end: match.index + 1 + match[1].length,
    })
  }

  // 문자열 값 매칭
  const stringRegex = /"([^"]*)"/g
  while ((match = stringRegex.exec(code)) !== null) {
    tokens.push({
      type: 'string',
      value: match[0],
      start: match.index,
      end: match.index + match[0].length,
    })
  }

  // 정렬
  tokens.sort((a, b) => a.start - b.start)

  return tokens
}

// ============================================================================
// Preset Templates
// ============================================================================

interface CodeTemplate {
  name: string
  description: string
  props: Record<string, unknown>
}

const COMPONENT_TEMPLATES: Record<string, CodeTemplate[]> = {
  Button: [
    {
      name: 'Primary Action',
      description: '주요 CTA 버튼',
      props: { variant: 'primary', children: 'Save Changes' },
    },
    {
      name: 'Loading State',
      description: '로딩 중인 버튼',
      props: { variant: 'primary', loading: true, children: 'Saving...' },
    },
    {
      name: 'Danger Action',
      description: '삭제 등 위험한 액션',
      props: { variant: 'danger', children: 'Delete' },
    },
    {
      name: 'With Icon',
      description: '아이콘이 포함된 버튼',
      props: { variant: 'primary', icon: '<Plus />', children: 'Add Item' },
    },
    {
      name: 'Ghost Button',
      description: '배경 없는 버튼',
      props: { variant: 'ghost', children: 'Cancel' },
    },
  ],
  Input: [
    {
      name: 'Basic Input',
      description: '기본 텍스트 입력',
      props: { placeholder: 'Enter text...' },
    },
    {
      name: 'With Error',
      description: '에러 상태 입력',
      props: { error: true, errorMessage: 'This field is required' },
    },
    {
      name: 'With Helper',
      description: '도움말 포함',
      props: { helperText: 'Enter at least 8 characters' },
    },
    {
      name: 'Search Input',
      description: '검색 입력',
      props: { leftIcon: '<Search />', placeholder: 'Search...' },
    },
  ],
  Select: [
    {
      name: 'Basic Select',
      description: '기본 드롭다운',
      props: {
        placeholder: 'Select option...',
        options: [
          { value: 'opt1', label: 'Option 1' },
          { value: 'opt2', label: 'Option 2' },
        ],
      },
    },
  ],
}

/**
 * 컴포넌트의 템플릿 목록 조회
 */
export function getCodeTemplates(componentName: string): CodeTemplate[] {
  return COMPONENT_TEMPLATES[componentName] ?? []
}

/**
 * 템플릿으로 코드 생성
 */
export function generateFromTemplate(
  componentName: string,
  templateName: string,
  options: GenerateCodeOptions = {}
): string | null {
  const templates = getCodeTemplates(componentName)
  const template = templates.find((t) => t.name === templateName)

  if (!template) {
    return null
  }

  return generateCodeSnippet(componentName, template.props, options)
}
