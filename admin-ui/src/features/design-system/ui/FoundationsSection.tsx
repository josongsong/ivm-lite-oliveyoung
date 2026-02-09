/**
 * Foundations Section
 *
 * Phase 4: Foundations 섹션 (Colors, Typography, Spacing, Shadows, Motion)
 */

import { useParams } from 'react-router-dom'
import { Construction, Layers, Maximize2, Palette, Type, Zap } from 'lucide-react'

import { ColorPalette } from '../components/foundations/ColorPalette'
import { TypographyScale } from '../components/foundations/TypographyScale'
import { SpacingScale } from '../components/foundations/SpacingScale'
import { ShadowScale } from '../components/foundations/ShadowScale'
import { MotionPreview } from '../components/foundations/MotionPreview'

// ============================================================================
// Section Data
// ============================================================================

interface SectionInfo {
  title: string
  description: string
  icon: React.ReactNode
  component: React.ComponentType | null
}

const SECTIONS: Record<string, SectionInfo> = {
  colors: {
    title: 'Colors',
    description: '프로젝트의 컬러 팔레트와 시맨틱 색상 시스템입니다. CSS 변수를 클릭하여 복사할 수 있습니다.',
    icon: <Palette size={32} />,
    component: ColorPalette,
  },
  typography: {
    title: 'Typography',
    description: '폰트 스케일, 폰트 웨이트, 줄 높이 등 타이포그래피 시스템입니다.',
    icon: <Type size={32} />,
    component: TypographyScale,
  },
  spacing: {
    title: 'Spacing',
    description: '일관된 간격을 위한 스페이싱 스케일입니다. 4px 기반 시스템을 사용합니다.',
    icon: <Maximize2 size={32} />,
    component: SpacingScale,
  },
  shadows: {
    title: 'Shadows',
    description: '요소의 깊이를 표현하기 위한 그림자 레벨입니다.',
    icon: <Layers size={32} />,
    component: ShadowScale,
  },
  motion: {
    title: 'Motion',
    description: '애니메이션 duration, easing, 전환 효과 프리셋입니다.',
    icon: <Zap size={32} />,
    component: MotionPreview,
  },
}

// ============================================================================
// Component
// ============================================================================

export function FoundationsSection() {
  const { section } = useParams<{ section: string }>()
  const sectionInfo = section ? SECTIONS[section] : null

  if (!sectionInfo) {
    return (
      <div className="ds-placeholder">
        <Construction size={48} className="ds-placeholder-icon" />
        <h2 className="ds-placeholder-title">섹션을 찾을 수 없습니다</h2>
        <p className="ds-placeholder-description">
          좌측 메뉴에서 원하는 Foundation 섹션을 선택해주세요.
        </p>
      </div>
    )
  }

  const SectionComponent = sectionInfo.component

  return (
    <div className="ds-section">
      <header className="ds-section-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
          <span style={{ color: 'var(--accent-purple)' }}>{sectionInfo.icon}</span>
          <h1 className="ds-section-title" style={{ margin: 0 }}>{sectionInfo.title}</h1>
        </div>
        <p className="ds-section-description">{sectionInfo.description}</p>
      </header>

      {SectionComponent ? (
        <SectionComponent />
      ) : (
        <div className="ds-placeholder" style={{ minHeight: '300px' }}>
          <Construction size={48} className="ds-placeholder-icon" />
          <h2 className="ds-placeholder-title">구현 예정</h2>
          <p className="ds-placeholder-description">
            이 섹션은 다음 Phase에서 상세 구현될 예정입니다.
          </p>
        </div>
      )}
    </div>
  )
}
