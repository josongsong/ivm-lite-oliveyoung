import { AnimatePresence, motion } from 'framer-motion'
import { ChevronDown, FileJson } from 'lucide-react'
import { Button, Label } from '@/shared/ui'

interface Schema {
  id: string
  name: string
  version: string
  description?: string
}

interface SchemaSelectorProps {
  selectedSchema: string
  showDropdown: boolean
  schemas?: Schema[]
  schemasError: boolean
  onToggleDropdown: () => void
  onSelectSchema: (schemaId: string) => void
  onManualInput: (value: string) => void
}

export function SchemaSelector({
  selectedSchema,
  showDropdown,
  schemas,
  schemasError,
  onToggleDropdown,
  onSelectSchema,
  onManualInput,
}: SchemaSelectorProps) {
  return (
    <div className="form-group">
      <Label>Schema</Label>
      <div className="schema-selector">
        <Button variant="secondary" className="schema-btn" onClick={onToggleDropdown}>
          <FileJson size={16} />
          <span>{selectedSchema || '스키마 선택...'}</span>
          <ChevronDown size={14} className={showDropdown ? 'rotated' : ''} />
        </Button>
        <AnimatePresence>
          {showDropdown ? (
            <motion.div
              className="schema-dropdown"
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
            >
              {schemasError ? (
                <div className="schema-manual">
                  <input
                    type="text"
                    placeholder="스키마 ID 직접 입력 (예: product-v1)"
                    value={selectedSchema}
                    onChange={(e) => onManualInput(e.target.value)}
                    className="schema-input"
                    onClick={(e) => e.stopPropagation()}
                  />
                </div>
              ) : null}
              {!schemasError && schemas && schemas.length > 0 ? (
                schemas.map((schema) => (
                  <Button
                    key={schema.id}
                    variant={selectedSchema === schema.id ? 'primary' : 'ghost'}
                    className={`schema-item ${selectedSchema === schema.id ? 'selected' : ''}`}
                    onClick={() => onSelectSchema(schema.id)}
                  >
                    <span className="schema-name">{schema.name}</span>
                    <span className="schema-version">v{schema.version}</span>
                    {schema.description ? (
                      <span className="schema-desc">{schema.description}</span>
                    ) : null}
                  </Button>
                ))
              ) : !schemasError ? (
                <div className="schema-empty">스키마가 없습니다</div>
              ) : null}
            </motion.div>
          ) : null}
        </AnimatePresence>
      </div>
    </div>
  )
}
