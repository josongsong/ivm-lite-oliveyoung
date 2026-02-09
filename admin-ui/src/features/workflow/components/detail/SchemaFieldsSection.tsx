import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronDown, Hash } from 'lucide-react'
import { Button } from '@/shared/ui'

interface SchemaFieldsSectionProps {
  schemaFields: string[]
}

export function SchemaFieldsSection({ schemaFields }: SchemaFieldsSectionProps) {
  const [isExpanded, setIsExpanded] = useState(true)

  if (schemaFields.length === 0) return null

  return (
    <section className="panel-section schema-fields-section">
      <Button
        variant="ghost"
        className="section-title-toggle"
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <div className="section-title-left">
          <Hash size={14} />
          <span>스키마 필드</span>
          <span className="field-count">{schemaFields.length}</span>
        </div>
        <ChevronDown
          size={14}
          className={`chevron ${isExpanded ? 'expanded' : ''}`}
        />
      </Button>
      <AnimatePresence initial={false}>
        {isExpanded ? (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: 'easeInOut' }}
            className="fields-container"
          >
            <div className="fields-grid">
              {schemaFields.map((field) => (
                <motion.div
                  key={`field-${field}`}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  className={`field-tag ${field === '*' ? 'wildcard' : ''}`}
                >
                  <span className="field-icon">
                    {field === '*' ? '✦' : '•'}
                  </span>
                  <span className="field-name">
                    {field === '*' ? 'All Fields' : field}
                  </span>
                </motion.div>
              ))}
            </div>
            {schemaFields.includes('*') && (
              <div className="wildcard-hint">
                <span>✦</span> 모든 필드가 슬라이스에 포함됩니다
              </div>
            )}
          </motion.div>
        ) : null}
      </AnimatePresence>
    </section>
  )
}
