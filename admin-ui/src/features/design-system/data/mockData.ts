/**
 * Design System Catalog - Mock Data
 * Phase 0: Button, Input, Select의 Mock 데이터
 */

import type { ColorToken, ComponentMeta, MotionToken, ShadowToken, SpacingToken, TypographyToken } from './types'

// ============================================================================
// Component Mock Data
// ============================================================================

export const buttonMockData: ComponentMeta = {
  name: 'Button',
  category: 'actions',
  description: '다양한 액션을 트리거하는 버튼 컴포넌트',
  longDescription: `
    SOTA 수준의 버튼 컴포넌트로, forwardRef 지원, 다양한 variant와 size,
    loading 상태, 아이콘 지원, 전체 너비 옵션을 제공합니다.
    접근성을 위해 aria-busy, aria-disabled 속성을 자동으로 관리합니다.
  `,
  stability: 'stable',
  path: '@/shared/ui/Button',
  controls: {
    variant: {
      type: 'select',
      label: 'Variant',
      defaultValue: 'secondary',
      options: [
        { label: 'Primary', value: 'primary' },
        { label: 'Secondary', value: 'secondary' },
        { label: 'Outline', value: 'outline' },
        { label: 'Ghost', value: 'ghost' },
        { label: 'Danger', value: 'danger' },
        { label: 'Success', value: 'success' },
      ],
      description: '버튼의 시각적 스타일',
    },
    size: {
      type: 'select',
      label: 'Size',
      defaultValue: 'md',
      options: [
        { label: 'Small', value: 'sm' },
        { label: 'Medium', value: 'md' },
        { label: 'Large', value: 'lg' },
      ],
      description: '버튼 크기',
    },
    loading: {
      type: 'boolean',
      label: 'Loading',
      defaultValue: false,
      description: '로딩 스피너 표시 및 비활성화',
    },
    disabled: {
      type: 'boolean',
      label: 'Disabled',
      defaultValue: false,
      description: '버튼 비활성화',
    },
    fullWidth: {
      type: 'boolean',
      label: 'Full Width',
      defaultValue: false,
      description: '전체 너비로 확장',
    },
    iconPosition: {
      type: 'radio',
      label: 'Icon Position',
      defaultValue: 'left',
      options: [
        { label: 'Left', value: 'left' },
        { label: 'Right', value: 'right' },
      ],
      description: '아이콘 위치',
    },
    children: {
      type: 'text',
      label: 'Label',
      defaultValue: 'Button',
      description: '버튼 텍스트',
    },
  },
  examples: [
    {
      title: 'Primary Button',
      description: '주요 액션에 사용',
      code: `<Button variant="primary">Save Changes</Button>`,
      context: '폼 제출, 주요 CTA',
    },
    {
      title: 'Loading State',
      description: '비동기 작업 진행 중',
      code: `<Button loading>Saving...</Button>`,
      context: 'API 호출 대기',
      highlightProps: ['loading'],
    },
    {
      title: 'Danger Button',
      description: '위험한 액션 (삭제 등)',
      code: `<Button variant="danger">Delete</Button>`,
      context: '삭제 확인, 위험 액션',
      highlightProps: ['variant'],
    },
    {
      title: 'With Icon',
      description: '아이콘과 함께 사용',
      code: `<Button variant="primary" icon={<Plus />}>Add Item</Button>`,
      context: '아이콘으로 의미 강화',
      highlightProps: ['icon'],
    },
  ],
  antiPatterns: [
    {
      title: 'Danger 액션에 Secondary 사용',
      reason: '사용자가 액션의 위험성을 인지하지 못할 수 있음',
      badCode: `<Button variant="secondary" onClick={handleDelete}>삭제</Button>`,
      goodCode: `<Button variant="danger" onClick={handleDelete}>삭제</Button>`,
      relatedProps: ['variant'],
    },
    {
      title: 'Loading 중 disabled 미설정',
      reason: 'loading prop이 자동으로 disabled를 적용하므로 중복 설정 불필요',
      badCode: `<Button loading disabled>Saving...</Button>`,
      goodCode: `<Button loading>Saving...</Button>`,
      relatedProps: ['loading', 'disabled'],
    },
    {
      title: '아이콘만 있는 버튼에 aria-label 누락',
      reason: '스크린 리더 사용자가 버튼 목적을 알 수 없음',
      badCode: `<Button icon={<X />} />`,
      goodCode: `<Button icon={<X />} aria-label="닫기" />`,
      relatedProps: ['icon'],
    },
  ],
  a11yScore: {
    overall: 95,
    categories: {
      colorContrast: 100,
      keyboardNavigation: 95,
      ariaLabels: 90,
      focusManagement: 95,
    },
    issues: [],
  },
  relatedComponents: ['IconButton'],
  whenToUse: [
    '사용자 액션이 필요할 때',
    '폼 제출 시',
    '모달 확인/취소',
    '네비게이션 트리거',
  ],
  whenNotToUse: [
    '단순 링크 이동은 <a> 태그 사용',
    '아이콘만 필요한 경우 IconButton 사용',
    '토글 상태 표시는 Switch 사용',
  ],
  keywords: ['button', 'cta', 'action', 'submit', '버튼', '액션'],
  version: '1.0.0',
  lastUpdated: '2026-02-01',
}

export const inputMockData: ComponentMeta = {
  name: 'Input',
  category: 'inputs',
  description: '텍스트 입력을 받는 인풋 컴포넌트',
  longDescription: `
    SOTA 수준의 인풋 컴포넌트로, forwardRef 지원, 다양한 size,
    에러 상태 및 메시지, 헬퍼 텍스트, 아이콘 지원을 제공합니다.
    aria-invalid, aria-describedby를 통해 접근성을 보장합니다.
  `,
  stability: 'stable',
  path: '@/shared/ui/Input',
  controls: {
    size: {
      type: 'select',
      label: 'Size',
      defaultValue: 'md',
      options: [
        { label: 'Small', value: 'sm' },
        { label: 'Medium', value: 'md' },
        { label: 'Large', value: 'lg' },
      ],
      description: '인풋 크기',
    },
    placeholder: {
      type: 'text',
      label: 'Placeholder',
      defaultValue: 'Enter text...',
      description: '플레이스홀더 텍스트',
    },
    error: {
      type: 'boolean',
      label: 'Error',
      defaultValue: false,
      description: '에러 상태 표시',
    },
    errorMessage: {
      type: 'text',
      label: 'Error Message',
      defaultValue: '',
      description: '에러 메시지',
    },
    helperText: {
      type: 'text',
      label: 'Helper Text',
      defaultValue: '',
      description: '도움말 텍스트',
    },
    disabled: {
      type: 'boolean',
      label: 'Disabled',
      defaultValue: false,
      description: '비활성화 상태',
    },
  },
  examples: [
    {
      title: 'Basic Input',
      description: '기본 텍스트 입력',
      code: `<Input placeholder="이름을 입력하세요" />`,
      context: '일반 텍스트 입력',
    },
    {
      title: 'With Error',
      description: '에러 상태 표시',
      code: `<Input error errorMessage="필수 입력 항목입니다" />`,
      context: '폼 유효성 검사 실패',
      highlightProps: ['error', 'errorMessage'],
    },
    {
      title: 'With Helper Text',
      description: '도움말 텍스트 포함',
      code: `<Input helperText="영문, 숫자 조합 8자 이상" />`,
      context: '입력 형식 안내',
      highlightProps: ['helperText'],
    },
    {
      title: 'With Icons',
      description: '아이콘 포함',
      code: `<Input leftIcon={<Search />} placeholder="검색..." />`,
      context: '검색 입력',
      highlightProps: ['leftIcon'],
    },
  ],
  antiPatterns: [
    {
      title: 'placeholder를 label 대신 사용',
      reason: '접근성 문제 - placeholder는 입력 시 사라짐',
      badCode: `<Input placeholder="이메일" />`,
      goodCode: `<Label htmlFor="email">이메일</Label>\n<Input id="email" placeholder="example@email.com" />`,
    },
    {
      title: 'error 없이 errorMessage만 설정',
      reason: 'error 상태가 false면 errorMessage가 표시되지 않음',
      badCode: `<Input errorMessage="에러입니다" />`,
      goodCode: `<Input error errorMessage="에러입니다" />`,
      relatedProps: ['error', 'errorMessage'],
    },
  ],
  a11yScore: {
    overall: 92,
    categories: {
      colorContrast: 95,
      keyboardNavigation: 90,
      ariaLabels: 88,
      focusManagement: 95,
    },
    issues: [
      {
        id: 'label-required',
        message: 'Input에는 연결된 label이 필요합니다',
        severity: 'warning',
        suggestion: 'Label 컴포넌트와 함께 사용하거나 aria-label 추가',
      },
    ],
  },
  relatedComponents: ['TextArea', 'Label'],
  whenToUse: [
    '한 줄 텍스트 입력',
    '이메일, 비밀번호 등 특수 입력',
    '검색 필드',
  ],
  whenNotToUse: [
    '여러 줄 입력은 TextArea 사용',
    '옵션 선택은 Select 사용',
    '날짜 입력은 DatePicker 사용',
  ],
  keywords: ['input', 'text', 'field', 'form', '입력', '텍스트'],
  version: '1.0.0',
  lastUpdated: '2026-02-01',
}

export const selectMockData: ComponentMeta = {
  name: 'Select',
  category: 'inputs',
  description: '드롭다운 옵션 선택 컴포넌트',
  longDescription: `
    커스텀 드롭다운 Select 컴포넌트로, forwardRef 지원, 다양한 size,
    아이콘 지원, 키보드 네비게이션을 제공합니다.
    aria-haspopup, aria-expanded, role="listbox" 등으로 접근성을 보장합니다.
  `,
  stability: 'stable',
  path: '@/shared/ui/Select',
  controls: {
    size: {
      type: 'select',
      label: 'Size',
      defaultValue: 'md',
      options: [
        { label: 'Small', value: 'sm' },
        { label: 'Medium', value: 'md' },
        { label: 'Large', value: 'lg' },
      ],
      description: 'Select 크기',
    },
    placeholder: {
      type: 'text',
      label: 'Placeholder',
      defaultValue: 'Select...',
      description: '플레이스홀더 텍스트',
    },
    disabled: {
      type: 'boolean',
      label: 'Disabled',
      defaultValue: false,
      description: '비활성화 상태',
    },
    value: {
      type: 'text',
      label: 'Value',
      defaultValue: '',
      description: '선택된 값',
    },
  },
  examples: [
    {
      title: 'Basic Select',
      description: '기본 드롭다운',
      code: `<Select
  value={value}
  onChange={setValue}
  options={[
    { value: 'opt1', label: 'Option 1' },
    { value: 'opt2', label: 'Option 2' },
  ]}
/>`,
      context: '일반 옵션 선택',
    },
    {
      title: 'With Icon',
      description: '아이콘 포함',
      code: `<Select
  icon={<Globe />}
  value={locale}
  onChange={setLocale}
  options={[
    { value: 'ko', label: '한국어' },
    { value: 'en', label: 'English' },
  ]}
/>`,
      context: '언어 선택 등',
      highlightProps: ['icon'],
    },
    {
      title: 'With Disabled Options',
      description: '비활성화된 옵션 포함',
      code: `<Select
  options={[
    { value: 'opt1', label: 'Available' },
    { value: 'opt2', label: 'Unavailable', disabled: true },
  ]}
/>`,
      context: '조건부 옵션 비활성화',
    },
  ],
  antiPatterns: [
    {
      title: '옵션 없이 Select 렌더링',
      reason: '빈 드롭다운은 사용자 경험 저하',
      badCode: `<Select options={[]} />`,
      goodCode: `{options.length > 0 ? <Select options={options} /> : <EmptyState />}`,
    },
    {
      title: 'onChange 없이 value만 설정',
      reason: '제어 컴포넌트로 동작하므로 onChange 필수',
      badCode: `<Select value="opt1" options={options} />`,
      goodCode: `<Select value={value} onChange={setValue} options={options} />`,
      relatedProps: ['value', 'onChange'],
    },
  ],
  a11yScore: {
    overall: 90,
    categories: {
      colorContrast: 95,
      keyboardNavigation: 88,
      ariaLabels: 85,
      focusManagement: 92,
    },
    issues: [
      {
        id: 'focus-visible',
        message: '포커스 표시가 일부 상황에서 약할 수 있음',
        severity: 'info',
        suggestion: 'focus-visible 스타일 강화 고려',
      },
    ],
  },
  relatedComponents: ['Input'],
  whenToUse: [
    '여러 옵션 중 하나 선택',
    '5개 이상의 옵션이 있을 때',
    '공간이 제한적일 때',
  ],
  whenNotToUse: [
    '2-3개 옵션은 Radio 사용',
    '여러 개 선택은 Checkbox 그룹 사용',
    '검색이 필요한 경우 Combobox 사용',
  ],
  keywords: ['select', 'dropdown', 'option', 'picker', '선택', '드롭다운'],
  version: '1.0.0',
  lastUpdated: '2026-02-01',
}

// ============================================================================
// All Components Registry
// ============================================================================

export const mockComponents: ComponentMeta[] = [
  buttonMockData,
  inputMockData,
  selectMockData,
]

// ============================================================================
// Design Tokens Mock Data
// ============================================================================

export const mockColorTokens: ColorToken[] = [
  // Primary Colors
  { name: 'Primary 50', cssVar: '--color-primary-50', value: '#f0f9ff', category: 'primary' },
  { name: 'Primary 100', cssVar: '--color-primary-100', value: '#e0f2fe', category: 'primary' },
  { name: 'Primary 500', cssVar: '--color-primary-500', value: '#0ea5e9', category: 'primary' },
  { name: 'Primary 600', cssVar: '--color-primary-600', value: '#0284c7', category: 'primary' },
  { name: 'Primary 700', cssVar: '--color-primary-700', value: '#0369a1', category: 'primary' },

  // Neutral Colors
  { name: 'Neutral 50', cssVar: '--color-neutral-50', value: '#fafafa', category: 'neutral' },
  { name: 'Neutral 100', cssVar: '--color-neutral-100', value: '#f4f4f5', category: 'neutral' },
  { name: 'Neutral 200', cssVar: '--color-neutral-200', value: '#e4e4e7', category: 'neutral' },
  { name: 'Neutral 300', cssVar: '--color-neutral-300', value: '#d4d4d8', category: 'neutral' },
  { name: 'Neutral 400', cssVar: '--color-neutral-400', value: '#a1a1aa', category: 'neutral' },
  { name: 'Neutral 500', cssVar: '--color-neutral-500', value: '#71717a', category: 'neutral' },
  { name: 'Neutral 600', cssVar: '--color-neutral-600', value: '#52525b', category: 'neutral' },
  { name: 'Neutral 700', cssVar: '--color-neutral-700', value: '#3f3f46', category: 'neutral' },
  { name: 'Neutral 800', cssVar: '--color-neutral-800', value: '#27272a', category: 'neutral' },
  { name: 'Neutral 900', cssVar: '--color-neutral-900', value: '#18181b', category: 'neutral' },

  // Semantic Colors
  { name: 'Success', cssVar: '--color-success', value: '#22c55e', category: 'semantic', description: '성공, 완료 상태' },
  { name: 'Warning', cssVar: '--color-warning', value: '#f59e0b', category: 'semantic', description: '경고, 주의' },
  { name: 'Error', cssVar: '--color-error', value: '#ef4444', category: 'semantic', description: '에러, 실패' },
  { name: 'Info', cssVar: '--color-info', value: '#3b82f6', category: 'semantic', description: '정보 안내' },
]

export const mockTypographyTokens: TypographyToken[] = [
  { name: 'Display Large', cssVar: '--text-display-lg', fontSize: '3rem', lineHeight: '1.1', fontWeight: 700, description: '대형 타이틀' },
  { name: 'Display Medium', cssVar: '--text-display-md', fontSize: '2.25rem', lineHeight: '1.2', fontWeight: 700, description: '중형 타이틀' },
  { name: 'Heading 1', cssVar: '--text-h1', fontSize: '2rem', lineHeight: '1.25', fontWeight: 600, description: 'H1 제목' },
  { name: 'Heading 2', cssVar: '--text-h2', fontSize: '1.5rem', lineHeight: '1.3', fontWeight: 600, description: 'H2 제목' },
  { name: 'Heading 3', cssVar: '--text-h3', fontSize: '1.25rem', lineHeight: '1.4', fontWeight: 600, description: 'H3 제목' },
  { name: 'Body Large', cssVar: '--text-body-lg', fontSize: '1.125rem', lineHeight: '1.6', fontWeight: 400, description: '큰 본문' },
  { name: 'Body Medium', cssVar: '--text-body-md', fontSize: '1rem', lineHeight: '1.6', fontWeight: 400, description: '기본 본문' },
  { name: 'Body Small', cssVar: '--text-body-sm', fontSize: '0.875rem', lineHeight: '1.5', fontWeight: 400, description: '작은 본문' },
  { name: 'Caption', cssVar: '--text-caption', fontSize: '0.75rem', lineHeight: '1.4', fontWeight: 400, description: '캡션, 라벨' },
]

export const mockSpacingTokens: SpacingToken[] = [
  { name: 'Space 0', cssVar: '--space-0', value: '0', pxValue: 0 },
  { name: 'Space 1', cssVar: '--space-1', value: '0.25rem', pxValue: 4 },
  { name: 'Space 2', cssVar: '--space-2', value: '0.5rem', pxValue: 8 },
  { name: 'Space 3', cssVar: '--space-3', value: '0.75rem', pxValue: 12 },
  { name: 'Space 4', cssVar: '--space-4', value: '1rem', pxValue: 16 },
  { name: 'Space 5', cssVar: '--space-5', value: '1.25rem', pxValue: 20 },
  { name: 'Space 6', cssVar: '--space-6', value: '1.5rem', pxValue: 24 },
  { name: 'Space 8', cssVar: '--space-8', value: '2rem', pxValue: 32 },
  { name: 'Space 10', cssVar: '--space-10', value: '2.5rem', pxValue: 40 },
  { name: 'Space 12', cssVar: '--space-12', value: '3rem', pxValue: 48 },
  { name: 'Space 16', cssVar: '--space-16', value: '4rem', pxValue: 64 },
]

export const mockShadowTokens: ShadowToken[] = [
  { name: 'Shadow None', cssVar: '--shadow-none', value: 'none', description: '그림자 없음' },
  { name: 'Shadow XS', cssVar: '--shadow-xs', value: '0 1px 2px 0 rgb(0 0 0 / 0.05)', description: '아주 작은 그림자' },
  { name: 'Shadow SM', cssVar: '--shadow-sm', value: '0 1px 3px 0 rgb(0 0 0 / 0.1)', description: '작은 그림자' },
  { name: 'Shadow MD', cssVar: '--shadow-md', value: '0 4px 6px -1px rgb(0 0 0 / 0.1)', description: '중간 그림자' },
  { name: 'Shadow LG', cssVar: '--shadow-lg', value: '0 10px 15px -3px rgb(0 0 0 / 0.1)', description: '큰 그림자' },
  { name: 'Shadow XL', cssVar: '--shadow-xl', value: '0 20px 25px -5px rgb(0 0 0 / 0.1)', description: '아주 큰 그림자' },
]

export const mockMotionTokens: MotionToken[] = [
  { name: 'Duration Fast', cssVar: '--duration-fast', duration: '100ms', easing: 'ease-out', description: '빠른 전환' },
  { name: 'Duration Normal', cssVar: '--duration-normal', duration: '200ms', easing: 'ease-in-out', description: '일반 전환' },
  { name: 'Duration Slow', cssVar: '--duration-slow', duration: '300ms', easing: 'ease-in-out', description: '느린 전환' },
  { name: 'Duration Slower', cssVar: '--duration-slower', duration: '500ms', easing: 'cubic-bezier(0.4, 0, 0.2, 1)', description: '아주 느린 전환' },
  { name: 'Ease In', cssVar: '--ease-in', duration: '200ms', easing: 'cubic-bezier(0.4, 0, 1, 1)', description: '가속 이징' },
  { name: 'Ease Out', cssVar: '--ease-out', duration: '200ms', easing: 'cubic-bezier(0, 0, 0.2, 1)', description: '감속 이징' },
  { name: 'Ease Bounce', cssVar: '--ease-bounce', duration: '300ms', easing: 'cubic-bezier(0.68, -0.55, 0.265, 1.55)', description: '바운스 이징' },
]
