import type { SchemaField, SchemaInfo } from '@/shared/types'

/** 파일 업로드 최대 크기 (5MB) */
export const MAX_FILE_SIZE = 5 * 1024 * 1024

const randomInt = (min: number, max: number) => Math.floor(Math.random() * (max - min + 1)) + min
const randomItem = <T,>(arr: readonly T[]): T => arr[Math.floor(Math.random() * arr.length)]

/** 스키마 타입별 샘플 데이터 풀 */
const SAMPLE_POOL = {
  // Product 관련
  productNames: ['비타민C 1000mg', '히알루론산 세럼', '선크림 SPF50', '립스틱 로즈핑크', '쿠션 파운데이션'],
  brands: ['올리브영', '닥터지', '라운드랩', '토리든', 'COSRX'],
  productDescriptions: ['촉촉한 보습감', '강력한 자외선 차단', '풍부한 발색력', '부드러운 사용감', '건강한 피부를 위해'],
  tags: ['인기', '베스트셀러', '신상품', '세일', '리뷰많음', '추천'],
  images: [
    'https://image.oliveyoung.co.kr/sample1.jpg',
    'https://image.oliveyoung.co.kr/sample2.jpg',
    'https://image.oliveyoung.co.kr/sample3.jpg',
  ],

  // Brand 관련
  brandNames: ['올리브영', '닥터지', '라운드랩', '토리든', 'COSRX', '아이오페', '설화수', '헤라'],
  brandDescriptions: ['자연주의 화장품 브랜드', '피부과학 전문 브랜드', '클린뷰티 브랜드', '저자극 스킨케어', '한국 대표 뷰티 브랜드'],
  countries: ['대한민국', '미국', '일본', '프랑스', '독일'],
  logoUrls: [
    'https://image.oliveyoung.co.kr/brand-logo1.png',
    'https://image.oliveyoung.co.kr/brand-logo2.png',
  ],
  websites: [
    'https://www.oliveyoung.co.kr',
    'https://www.drjart.com',
    'https://www.roundlab.co.kr',
  ],

  // Category 관련
  categories: ['건강식품', '스킨케어', '메이크업', '헤어케어', '바디케어'],
  categoryPaths: ['건강식품>비타민', '스킨케어>세럼', '메이크업>립', '헤어케어>샴푸', '바디케어>로션'],

  // 공통
  currencies: ['KRW', 'USD', 'JPY'],
} as const

/** 필드 타입 기반 기본 값 생성 */
export function generateByType(field: SchemaField): unknown {
  switch (field.type) {
    case 'string':
      // 필드명 힌트가 있으면 활용
      if (field.name.toLowerCase().includes('url') || field.name.toLowerCase().includes('link')) {
        return `https://example.com/${field.name.toLowerCase()}`
      }
      if (field.name.toLowerCase().includes('email')) {
        return `sample@oliveyoung.co.kr`
      }
      if (field.name.toLowerCase().includes('phone')) {
        return `010-${randomInt(1000, 9999)}-${randomInt(1000, 9999)}`
      }
      return `샘플_${field.name}`

    case 'number':
      return randomInt(1, 10000)

    case 'boolean':
      return Math.random() > 0.5

    case 'array': {
      // 배열은 1~3개 아이템
      const count = randomInt(1, 3)
      return Array.from({ length: count }, (_, i) => `${field.name}_${i + 1}`)
    }

    case 'object':
      return {
        id: randomInt(1, 999),
        value: `${field.name}_value`
      }

    default:
      return field.required ? `필수_${field.name}` : null
  }
}

/** Product 스키마 필드 생성 */
function generateProductField(name: string): unknown | undefined {
  const fieldHandlers: Record<string, () => unknown> = {
    sku: () => `SKU-${randomInt(10000, 99999)}`,
    name: () => randomItem(SAMPLE_POOL.productNames),
    price: () => randomInt(10, 100) * 1000,
    saleprice: () => randomInt(5, 80) * 1000,
    brand: () => randomItem(SAMPLE_POOL.brands),
    brandid: () => `BRAND-${randomInt(100, 999)}`,
    category: () => randomItem(SAMPLE_POOL.categories),
    categorypath: () => randomItem(SAMPLE_POOL.categoryPaths),
    stock: () => randomInt(0, 500),
    isavailable: () => Math.random() > 0.2,
    description: () => randomItem(SAMPLE_POOL.productDescriptions),
    tags: () => {
      const count = randomInt(1, 3)
      return Array.from({ length: count }, () => randomItem([...SAMPLE_POOL.tags]))
    },
    images: () => {
      const count = randomInt(1, 3)
      return SAMPLE_POOL.images.slice(0, count)
    },
    currency: () => randomItem([...SAMPLE_POOL.currencies]),
  }
  return fieldHandlers[name]?.()
}

/** Brand 스키마 필드 생성 */
function generateBrandField(name: string): unknown | undefined {
  const fieldHandlers: Record<string, () => unknown> = {
    brandid: () => `BRAND-${randomInt(100, 999)}`,
    name: () => randomItem(SAMPLE_POOL.brandNames),
    logourl: () => randomItem([...SAMPLE_POOL.logoUrls]),
    description: () => randomItem(SAMPLE_POOL.brandDescriptions),
    country: () => randomItem([...SAMPLE_POOL.countries]),
    website: () => randomItem([...SAMPLE_POOL.websites]),
  }
  return fieldHandlers[name]?.()
}

/** Category 스키마 필드 생성 */
function generateCategoryField(name: string): unknown | undefined {
  const fieldHandlers: Record<string, () => unknown> = {
    categoryid: () => `CAT-${randomInt(100, 999)}`,
    name: () => randomItem([...SAMPLE_POOL.categories]),
    parentid: () => Math.random() > 0.5 ? `CAT-${randomInt(1, 99)}` : null,
    depth: () => randomInt(1, 3),
    displayorder: () => randomInt(1, 100),
    path: () => randomItem(SAMPLE_POOL.categoryPaths),
    isactive: () => Math.random() > 0.1,
  }
  return fieldHandlers[name]?.()
}

/** 스키마 필드 기반 샘플 데이터 생성 */
export function generateSampleData(schema: SchemaInfo): Record<string, unknown> {
  const result: Record<string, unknown> = {}

  // 스키마 타입별 샘플 데이터 풀
  const isProduct = schema.id.includes('product')
  const isBrand = schema.id.includes('brand')
  const isCategory = schema.id.includes('category')

  for (const field of schema.fields) {
    // required: false 필드는 70% 확률로 포함
    if (!field.required && Math.random() > 0.7) {
      continue
    }

    const name = field.name.toLowerCase()
    let value: unknown

    // 스키마별 필드명 기반 스마트 생성
    if (isProduct) {
      value = generateProductField(name) ?? generateByType(field)
    } else if (isBrand) {
      value = generateBrandField(name) ?? generateByType(field)
    } else if (isCategory) {
      value = generateCategoryField(name) ?? generateByType(field)
    } else {
      value = generateByType(field)
    }

    // 타입 검증 및 조정
    if (field.type === 'number' && typeof value === 'string') {
      value = parseInt(value.replace(/\D/g, ''), 10) || randomInt(1000, 99999)
    } else if (field.type === 'array' && !Array.isArray(value)) {
      value = [value]
    } else if (field.type === 'boolean' && typeof value !== 'boolean') {
      value = Math.random() > 0.5
    }

    result[field.name] = value
  }

  return result
}
